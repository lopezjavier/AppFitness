package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.usecase.DaysRow
import io.github.lopezjavier.appfitness.domain.usecase.FolderReviewCalculator
import io.github.lopezjavier.appfitness.domain.usecase.FolderReviewResult
import io.github.lopezjavier.appfitness.domain.usecase.GroupRow
import io.github.lopezjavier.appfitness.domain.usecase.ReviewExercise
import io.github.lopezjavier.appfitness.domain.usecase.ReviewRoutine
import io.github.lopezjavier.appfitness.domain.usecase.StrengthOrderRow
import io.github.lopezjavier.appfitness.domain.usecase.countsForVolume
import kotlin.math.roundToInt

/**
 * One exercise of a folder routine as the block check reads and copies it. [repMin]/[repMax] are already resolved as
 * the review does (own range, else the planned sets' reps; null when there are none). [added] marks what the user
 * added in the fix step (the only thing that can be removed).
 */
data class FolderBlockExercise(
    val id: String,
    val muscleGroup: MuscleGroup,
    val secondary: List<MuscleGroup>,
    val countsAsStrength: Boolean,
    val movementPattern: MovementPattern?,
    val equipment: Equipment,
    val sets: Int,
    val repMin: Int?,
    val repMax: Int?,
    val rpe: Double?,
    val restSeconds: Int?,
    /** The folder's own first working-set weight, if any. */
    val plannedWeightKg: Double?,
    val added: Boolean = false,
) {
    internal fun toReview() = ReviewExercise(
        id,
        muscleGroup,
        secondary,
        countsAsStrength,
        movementPattern,
        sets,
        repMin,
        repMax,
    )

    internal val isMain: Boolean get() = countsAsStrength && movementPattern in FolderReviewCalculator.MAIN_PATTERNS
}

data class FolderBlockRoutine(val name: String, val exercises: List<FolderBlockExercise>) {
    /** A lifting routine holds at least one exercise that counts for H1; only those are block days. */
    val lifting: Boolean get() = exercises.any { it.countsAsStrength }
}

/** The days rule (Q-B5): the block has one training day per lifting routine. */
sealed interface DaysCheck {
    data object Ok : DaysCheck

    /** The folder has [routines] lifting routines but "Dias por semana" is [days]. */
    data class Refused(val routines: Int, val days: Int) : DaysCheck

    /** One lifting routine and 2 or more days: add a second routine to the folder or start from scratch. */
    data object NeedsSecondRoutine : DaysCheck
}

/** A gate failure of the folder copy (07 (g)). */
sealed interface GateIssue {
    data class OutsideEquipment(val routineIndex: Int, val exerciseId: String) : GateIssue

    data class RepeatInRoutine(val routineIndex: Int, val exerciseId: String) : GateIssue

    /** The exercise has more than 10 working sets (the server bound is 1..10); swapping it does not clear this. */
    data class SetsOverLimit(val routineIndex: Int, val exerciseId: String) : GateIssue
}

/** The "strength_reps" proposal for a main lift: "Cambiar a {newMin}-{newMax} repeticiones". */
data class RepFix(val routineIndex: Int, val exerciseIndex: Int, val newMin: Int, val newMax: Int)

/** Live state of a folder in the builder: the review at 10,0, the days rule and the gate. */
data class FolderBlockStatus(
    val review: FolderReviewResult,
    /** 10 x the review score, equals 100 only at 10,0. */
    val percent: Int,
    val days: DaysCheck,
    val gate: List<GateIssue>,
    val repFixes: List<RepFix>,
    /** The folder has a main lift after another exercise; saving moves the main lifts first (C4b). */
    val orderFixedAtSave: Boolean,
    val canSave: Boolean,
    /** Names of the routines with no lifting exercise: they are not block days and are not saved. */
    val leftOutRoutines: List<String> = emptyList(),
)

/**
 * "Desde una carpeta" (07 section 3 "Block builder" (h)): an immutable copy of a folder, checked at the same 100 % as
 * the builder (the folder review at 10,0) and fixed with a few operations. Every operation returns a new draft (an
 * invalid one returns this draft unchanged); the source folder is never touched.
 */
@Suppress("TooManyFunctions", "ReturnCount", "ComplexCondition") // Small fix operations; invalid ones exit early.
data class FolderBlockDraft(
    val routines: List<FolderBlockRoutine>,
    val intention: TrainingIntention?,
    val daysPerWeek: Int,
    val equipment: Set<Equipment>,
) {
    private val strength: Boolean get() = intention == TrainingIntention.Strength

    private val liftingRoutines: List<Pair<Int, FolderBlockRoutine>>
        get() = routines.withIndex().filter { it.value.lifting }.map { it.index to it.value }

    fun status(): FolderBlockStatus {
        val asFound = FolderReviewCalculator.compute(reviewRoutines(routines), intention)
        // The order is fixed at save (07 (h)), so the score is read on the order the block will have.
        val review = if (strength) {
            FolderReviewCalculator.compute(
                reviewRoutines(routinesMainFirst()),
                intention,
            )
        } else {
            asFound
        }
        val orderFixedAtSave = strength && asFound.rows.filterIsInstance<StrengthOrderRow>().any { !it.met }
        val lifting = liftingRoutines.size
        val days = when {
            lifting == 1 && daysPerWeek == 1 -> DaysCheck.Ok
            lifting == 1 && daysPerWeek >= 2 -> DaysCheck.NeedsSecondRoutine
            lifting == daysPerWeek -> DaysCheck.Ok
            else -> DaysCheck.Refused(lifting, daysPerWeek)
        }
        // A one-routine folder with one day is the 1-day split: C3 stays unmet and every other check must be met.
        val reviewOk = review.findings.all { it is DaysRow } && (review.complete || (lifting == 1 && daysPerWeek == 1))
        val gate = gateIssues()
        return FolderBlockStatus(
            review = review,
            percent = (review.score * PERCENT_PER_POINT).roundToInt(),
            days = days,
            gate = gate,
            repFixes = repFixes(),
            orderFixedAtSave = orderFixedAtSave,
            canSave = days == DaysCheck.Ok && gate.isEmpty() && reviewOk,
            leftOutRoutines = routines.filter { !it.lifting }.map { it.name },
        )
    }

    /** The unmet muscle groups of the review (missing or low), each with the sets it still needs. */
    fun addableGroups(): List<GroupRow> = status().review.rows.filterIsInstance<GroupRow>().filter { !it.met }

    /** The slot the exercise picker filters for when adding a [group] exercise to routine [routineIndex]. */
    fun pickerSlot(group: MuscleGroup, routineIndex: Int): Slot? {
        if (routineIndex !in routines.indices) return null
        val needed = addableGroups().firstOrNull { it.group == group }?.missingSets ?: 0
        // Whether the pick is a heavy main lift depends on the exercise chosen; the picker only filters by group.
        return addSlot(group, needed.coerceIn(MIN_ADD_SETS, SectionPrescription.MAX_SETS_PER_SLOT), onlyMain = false)
    }

    /** The slot the picker filters for when swapping the row [exerciseIndex] of routine [routineIndex]. */
    fun swapSlot(routineIndex: Int, exerciseIndex: Int): Slot? {
        val old = routines.getOrNull(routineIndex)?.exercises?.getOrNull(exerciseIndex) ?: return null
        return Slot(
            old.muscleGroup,
            SlotKind.Standard,
            old.sets,
            old.repMin ?: DEFAULT_REPS,
            old.repMax ?: DEFAULT_REPS,
            old.restSeconds ?: STANDARD_REST,
        )
    }

    private fun addSlot(group: MuscleGroup, sets: Int, onlyMain: Boolean): Slot {
        val heavy = strength && onlyMain
        val (min, max, rest) = when {
            !strength -> Triple(HYPERTROPHY_MIN, HYPERTROPHY_MAX, STANDARD_REST)
            heavy -> Triple(HEAVY_MIN, HEAVY_MAX, HEAVY_REST)
            else -> Triple(ACCESSORY_MIN, ACCESSORY_MAX, STANDARD_REST)
        }
        return Slot(group, SlotKind.Standard, sets, min, max, rest)
    }

    private fun reviewRoutines(list: List<FolderBlockRoutine>) =
        list.map { r -> ReviewRoutine(r.name, r.exercises.map { it.toReview() }) }

    private fun routinesMainFirst() = routines.map { r ->
        r.copy(exercises = r.exercises.filter { it.isMain } + r.exercises.filter { !it.isMain })
    }

    private fun gateIssues(): List<GateIssue> = liftingRoutines.flatMap { (ri, r) ->
        val outside = r.exercises.filter { equipment.isNotEmpty() && it.equipment !in equipment }
            .map { GateIssue.OutsideEquipment(ri, it.id) }
        val repeats = r.exercises.groupBy { it.id }.filter { it.value.size > 1 }.keys
            .map { GateIssue.RepeatInRoutine(ri, it) }
        val over = r.exercises.filter { it.sets > MAX_SETS }.map { GateIssue.SetsOverLimit(ri, it.id) }
        outside + repeats + over
    }

    private fun repFixes(): List<RepFix> = if (!strength) {
        emptyList()
    } else {
        routines.flatMapIndexed { ri, r ->
            r.exercises.mapIndexedNotNull { ei, e ->
                val max = e.repMax
                val min = e.repMin
                if (e.isMain && min != null && max != null && max > FolderReviewCalculator.MAX_HEAVY_REPS) {
                    RepFix(ri, ei, minOf(min, FIX_MAX_MIN), FolderReviewCalculator.MAX_HEAVY_REPS)
                } else {
                    null
                }
            }
        }
    }

    /**
     * Adds [exercise] at the end of routine [routineIndex] with the builder's values: sets = what its group still
     * needs (capped at 5, at least 2); non-strength 8-12 / RPE 8 / 120 s; strength 6-8 / 120 s, or 4-6 / 180 s when it
     * is the routine's only main lift for the group. Ignored when the exercise is a repeat, outside the equipment or
     * the routine does not exist.
     */
    fun add(routineIndex: Int, exercise: CatalogExercise, history: ExerciseHistory?): FolderBlockDraft {
        val routine = routines.getOrNull(routineIndex) ?: return this
        val x = exercise.exercise
        if (routine.exercises.any { it.id == x.id } || !inEquipment(x.equipment)) return this
        val group = x.muscleGroup
        val needed = status().review.rows.filterIsInstance<GroupRow>()
            .firstOrNull { it.group == group }?.missingSets ?: 0
        val sets = needed.coerceIn(MIN_ADD_SETS, SectionPrescription.MAX_SETS_PER_SLOT)
        val main = x.movementPattern in FolderReviewCalculator.MAIN_PATTERNS
        val onlyMain = main && routine.exercises.none { it.isMain && it.muscleGroup == group }
        val slot = addSlot(group, sets, onlyMain)
        val min = slot.repMin
        val max = slot.repMax
        val rest = slot.restSeconds
        val added = FolderBlockExercise(
            id = x.id,
            muscleGroup = group,
            secondary = x.secondaryMuscles,
            countsAsStrength = x.countsForVolume(),
            movementPattern = x.movementPattern,
            equipment = x.equipment,
            sets = sets,
            repMin = min,
            repMax = max,
            rpe = SectionPrescription.RPE.toDouble(),
            restSeconds = rest,
            plannedWeightKg = Pick.of(x, slot, history).plannedWeightKg,
            added = true,
        )
        return replaceRoutine(routineIndex) { it.copy(exercises = it.exercises + added) }
    }

    /** Removes an exercise the user added; the folder's own exercises cannot be removed. */
    fun removeAdded(routineIndex: Int, exerciseIndex: Int): FolderBlockDraft {
        val target = routines.getOrNull(routineIndex)?.exercises?.getOrNull(exerciseIndex) ?: return this
        if (!target.added) return this
        return replaceRoutine(routineIndex) { r ->
            r.copy(
                exercises = r.exercises.filterIndexed { i, _ ->
                    i !=
                        exerciseIndex
                },
            )
        }
    }

    /**
     * Swaps an exercise for [replacement] of the same primary group, inside the equipment and not already in the
     * routine; sets, reps, RPE and rest stay. The planned weight follows 07 (e) for the new exercise.
     */
    fun swap(
        routineIndex: Int,
        exerciseIndex: Int,
        replacement: CatalogExercise,
        history: ExerciseHistory?,
    ): FolderBlockDraft {
        val routine = routines.getOrNull(routineIndex) ?: return this
        val old = routine.exercises.getOrNull(exerciseIndex) ?: return this
        val x = replacement.exercise
        val repeats = routine.exercises.withIndex().any { it.index != exerciseIndex && it.value.id == x.id }
        if (x.muscleGroup != old.muscleGroup || !inEquipment(x.equipment) || repeats) return this
        val slot = Slot(
            old.muscleGroup,
            SlotKind.Standard,
            old.sets,
            old.repMin ?: DEFAULT_REPS,
            old.repMax ?: DEFAULT_REPS,
            old.restSeconds ?: STANDARD_REST,
        )
        val swapped = old.copy(
            id = x.id,
            secondary = x.secondaryMuscles,
            countsAsStrength = x.countsForVolume(),
            movementPattern = x.movementPattern,
            equipment = x.equipment,
            plannedWeightKg = Pick.of(x, slot, history).plannedWeightKg,
        )
        return replaceRoutine(routineIndex) { r ->
            r.copy(exercises = r.exercises.mapIndexed { i, e -> if (i == exerciseIndex) swapped else e })
        }
    }

    /** Applies the review's "Cambiar a {min(min, 6)}-8 repeticiones" on a main lift above 8 reps. */
    fun applyStrengthRepFix(routineIndex: Int, exerciseIndex: Int): FolderBlockDraft {
        val fix = status().repFixes.firstOrNull { it.routineIndex == routineIndex && it.exerciseIndex == exerciseIndex }
            ?: return this
        return replaceRoutine(routineIndex) { r ->
            r.copy(
                exercises = r.exercises.mapIndexed { i, e ->
                    if (i ==
                        exerciseIndex
                    ) {
                        e.copy(repMin = fix.newMin, repMax = fix.newMax)
                    } else {
                        e
                    }
                },
            )
        }
    }

    /**
     * The block saved as a new folder, or null while [FolderBlockStatus.canSave] is false. Lifting routines only
     * (non-lifting ones and cardio-pattern exercises are not copied), cardio last per routine, "Ganar fuerza" main
     * lifts moved first (C4b). A missing planned weight follows 07 (e) from [history].
     */
    fun toBuiltBlock(
        sessionMinutes: Int,
        cardioTargetMinWeek: Int,
        cardioExerciseId: String?,
        history: Map<String, ExerciseHistory>,
    ): BuiltBlock? {
        if (!status().canSave) return null
        val lifting = liftingRoutines.map { it.second }
        val cardio = CardioPlan.of(cardioTargetMinWeek, lifting.size)
        val sessions = lifting.mapIndexed { i, routine ->
            val exercises = routine.exercises.filter { it.movementPattern != MovementPattern.Cardio }
                .map { it.toBuilt(history[it.id]) }
            val ordered = if (strength) {
                exercises.filter { it.role == BuiltExercise.ROLE_MAIN } +
                    exercises.filter { it.role != BuiltExercise.ROLE_MAIN }
            } else {
                exercises
            }
            val cardioSlot = if (cardio.perRoutineMin[i] > 0 && cardioExerciseId != null) {
                listOf(BuiltExercise.cardio(cardioExerciseId, cardio.perRoutineMin[i]))
            } else {
                emptyList()
            }
            BuiltSession(routine.name, groupsOf(routine), ordered + cardioSlot)
        }
        return BuiltBlock(
            daysPerWeek = lifting.size,
            intention = intention,
            equipment = equipment.sortedBy { it.ordinal },
            sessionMinutes = sessionMinutes,
            routineTotalMin = cardio.perRoutineMin.map { sessionMinutes + it },
            cardio = cardio,
            source = BuiltBlock.SOURCE_FOLDER,
            sessions = sessions,
        )
    }

    private fun groupsOf(routine: FolderBlockRoutine) = routine.exercises.filter { it.countsAsStrength }
        .map { it.muscleGroup }.distinct().map { it.name.lowercase() }

    private fun FolderBlockExercise.toBuilt(history: ExerciseHistory?): BuiltExercise {
        val min = repMin ?: DEFAULT_REPS
        val max = repMax ?: DEFAULT_REPS
        val slot = Slot(muscleGroup, SlotKind.Standard, sets, min, max, restSeconds ?: STANDARD_REST)
        val weight = plannedWeightKg ?: history?.let {
            val w = it.lastWeightKg
            val r = it.lastReps
            if (w != null && w > 0 && r != null && r in min..max) w else null
        }
        return BuiltExercise(
            role = if (movementPattern in
                FolderReviewCalculator.MAIN_PATTERNS
            ) {
                BuiltExercise.ROLE_MAIN
            } else {
                BuiltExercise.ROLE_ISOLATION
            },
            exerciseId = id,
            sets = slot.sets,
            repMin = min,
            repMax = max,
            restSeconds = slot.restSeconds,
            targetRpe = (rpe ?: SectionPrescription.RPE.toDouble()).roundToInt().coerceIn(RPE_MIN, RPE_MAX),
            plannedWeightKg = weight,
            supersetGroup = null,
            durationMin = null,
            pace = null,
        )
    }

    private fun inEquipment(e: Equipment) = equipment.isEmpty() || e in equipment

    private fun replaceRoutine(index: Int, change: (FolderBlockRoutine) -> FolderBlockRoutine) =
        copy(routines = routines.mapIndexed { i, r -> if (i == index) change(r) else r })

    private companion object {
        const val PERCENT_PER_POINT = 10
        const val FIX_MAX_MIN = 6
        const val MIN_ADD_SETS = 2
        const val HYPERTROPHY_MIN = 8
        const val HYPERTROPHY_MAX = 12
        const val STANDARD_REST = 120
        const val HEAVY_MIN = 4
        const val HEAVY_MAX = 6
        const val HEAVY_REST = 180
        const val ACCESSORY_MIN = 6
        const val ACCESSORY_MAX = 8
        const val DEFAULT_REPS = 10
        const val RPE_MIN = 7
        const val RPE_MAX = 9
        const val MAX_SETS = 10
    }
}
