package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.RepRange
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlin.math.ceil
import kotlin.math.floor

/** One exercise of a routine as the folder review reads it (07 section 3 "Folder review", input). */
data class ReviewExercise(
    val id: String,
    val muscleGroup: MuscleGroup,
    val secondary: List<MuscleGroup>,
    /** Already resolved with [countsForVolume]. */
    val countsAsStrength: Boolean,
    val movementPattern: MovementPattern?,
    /** Working sets (warm-up excluded; a drop set is one). */
    val workingSets: Int,
    val repMin: Int?,
    val repMax: Int?,
)

/** A routine of the folder with its exercises in order. */
data class ReviewRoutine(val name: String, val exercises: List<ReviewExercise>)

/** One row of the checklist. */
sealed interface ChecklistRow {
    val met: Boolean
}

/** C1/C2: weekly [sets] of one of the six groups (fractional counting). */
data class GroupRow(val group: MuscleGroup, val sets: Double) : ChecklistRow {
    override val met: Boolean get() = sets >= FolderReviewCalculator.TARGET_SETS

    /** C2: the group has no sets at all. */
    val isMissing: Boolean get() = sets == 0.0

    /** Sets to add to reach the target (rounded up); 0 when met. */
    val missingSets: Int
        get() = if (met) 0 else ceil(FolderReviewCalculator.TARGET_SETS - sets).toInt()
}

/** C3: [liftingRoutines] routines with at least one counting exercise. */
data class DaysRow(val liftingRoutines: Int) : ChecklistRow {
    override val met: Boolean get() = liftingRoutines >= FolderReviewCalculator.MIN_DAYS
}

/** A main lift above the heavy-rep ceiling. */
data class RepOffender(val routineName: String, val exerciseId: String, val repMin: Int, val repMax: Int)

/** C4a (strength only); shown only when at least one main lift had a range. */
data class StrengthRepsRow(val offenders: List<RepOffender>) : ChecklistRow {
    override val met: Boolean get() = offenders.isEmpty()
}

/** A routine where [mainLiftId] comes after [earlierExerciseId]; [mainLiftIds] are all its main lifts in order. */
data class OrderOffender(
    val routineName: String,
    val mainLiftId: String,
    val earlierExerciseId: String,
    val mainLiftIds: List<String>,
)

/** C4b (strength only); shown only when at least one routine was eligible. */
data class StrengthOrderRow(val offenders: List<OrderOffender>) : ChecklistRow {
    override val met: Boolean get() = offenders.isEmpty()
}

/**
 * The review of a folder: [score] 0-10 with one decimal, [rows] in display order (six groups, days, then the
 * strength rows when judged) and [findings], the unmet rows in finding order (C2, C1 by deficit, C3, C4a, C4b).
 */
data class FolderReviewResult(
    val score: Double,
    val rows: List<ChecklistRow>,
    val findings: List<ChecklistRow>,
    val liftingRoutines: Int,
    val routineCount: Int,
    val strength: Boolean,
) {
    /** Every row met: the `complete` line. */
    val complete: Boolean get() = findings.isEmpty()
    val isEmpty: Boolean get() = routineCount == 0
}

/** Deterministic folder review (07 section 3 "Folder review"); pure, no model, no network. */
object FolderReviewCalculator {
    const val TARGET_SETS = 10
    const val MIN_DAYS = 2
    const val MAX_HEAVY_REPS = 8
    const val CAP_SCORE = 6.0
    private const val SECONDARY_WEIGHT = 0.5
    private const val W_VOLUME = 75.0
    private const val W_DAYS = 25.0
    private const val W_VOLUME_STRENGTH = 50.0
    private const val W_DAYS_STRENGTH = 20.0
    private const val W_STRENGTH_PART = 15.0
    private const val DECIMAL_SCALE = 10.0
    private const val SCORE_MAX = 10.0
    private const val ROUND_EPSILON = 1e-9

    val GROUPS = listOf(
        MuscleGroup.Chest,
        MuscleGroup.Back,
        MuscleGroup.Shoulders,
        MuscleGroup.Quads,
        MuscleGroup.Hamstrings,
        MuscleGroup.Glutes,
    )

    private val MAIN_PATTERNS = setOf(
        MovementPattern.Squat,
        MovementPattern.Hinge,
        MovementPattern.Lunge,
        MovementPattern.HorizontalPush,
        MovementPattern.VerticalPush,
        MovementPattern.HorizontalPull,
        MovementPattern.VerticalPull,
    )

    fun compute(routines: List<ReviewRoutine>, intention: TrainingIntention?): FolderReviewResult {
        val strength = intention == TrainingIntention.Strength
        val groupRows = GROUPS.map { GroupRow(it, weeklySets(routines, it)) }
        val lifting = routines.count { r -> r.exercises.any { it.countsAsStrength } }
        val days = DaysRow(lifting)
        val reps = repsRow(routines)
        val order = orderRow(routines)

        val pV = groupRows.map { minOf(it.sets / TARGET_SETS, 1.0) }.average()
        val pD = if (days.met) 1.0 else 0.0
        val parts = buildList {
            add((if (strength) W_VOLUME_STRENGTH else W_VOLUME) to pV)
            add((if (strength) W_DAYS_STRENGTH else W_DAYS) to pD)
            if (strength) {
                reps.second?.let { add(W_STRENGTH_PART to it) }
                order.second?.let { add(W_STRENGTH_PART to it) }
            }
        }
        val raw = SCORE_MAX * parts.sumOf { it.first * it.second } / parts.sumOf { it.first }
        val rounded = floor(raw * DECIMAL_SCALE + SECONDARY_WEIGHT + ROUND_EPSILON) / DECIMAL_SCALE
        val score = if (groupRows.any { it.isMissing }) minOf(rounded, CAP_SCORE) else rounded

        val strengthRows = if (strength) listOfNotNull(reps.first, order.first) else emptyList()
        val rows = groupRows + days + strengthRows
        val findings = buildList {
            addAll(groupRows.filter { it.isMissing })
            addAll(groupRows.filter { !it.isMissing && !it.met }.sortedByDescending { it.missingSets })
            if (!days.met) add(days)
            strengthRows.filter { !it.met }.forEach { add(it) }
        }
        return FolderReviewResult(score, rows, findings, lifting, routines.size, strength)
    }

    /**
     * The rep range of a routine exercise: its own [repRange], else the min and max of its planned working sets'
     * reps; null when there are none (07: such a main lift is not judged).
     */
    fun resolveRepRange(repRange: RepRange?, plannedSets: List<PlannedSet>): Pair<Int, Int>? {
        if (repRange != null) return repRange.min to repRange.max
        val reps = plannedSets.filter { it.type != SetType.WarmUp && it.reps > 0 }.map { it.reps }
        return if (reps.isEmpty()) null else reps.min() to reps.max()
    }

    private fun weeklySets(routines: List<ReviewRoutine>, group: MuscleGroup): Double = routines.sumOf { r ->
        r.exercises.filter { it.countsAsStrength }.sumOf { e ->
            when {
                e.muscleGroup == group -> e.workingSets.toDouble()
                group in e.secondary -> e.workingSets * SECONDARY_WEIGHT
                else -> 0.0
            }
        }
    }

    private fun isMain(e: ReviewExercise) = e.countsAsStrength && e.movementPattern in MAIN_PATTERNS

    /** C4a row and its share (null when no main lift has a range). */
    private fun repsRow(routines: List<ReviewRoutine>): Pair<StrengthRepsRow?, Double?> {
        val judged = routines.flatMap { r ->
            r.exercises.filter { isMain(it) && it.repMin != null && it.repMax != null }.map { r.name to it }
        }
        if (judged.isEmpty()) return null to null
        val offenders = judged.filter { (_, e) -> e.repMax!! > MAX_HEAVY_REPS }
            .map { (name, e) -> RepOffender(name, e.id, e.repMin!!, e.repMax!!) }
        return StrengthRepsRow(offenders) to (judged.size - offenders.size).toDouble() / judged.size
    }

    /** C4b row and its share (null when no routine is eligible). */
    private fun orderRow(routines: List<ReviewRoutine>): Pair<StrengthOrderRow?, Double?> {
        val eligible = routines.filter { r ->
            val counting = r.exercises.filter { it.countsAsStrength }
            counting.any { isMain(it) } && counting.any { !isMain(it) }
        }
        if (eligible.isEmpty()) return null to null
        val offenders = eligible.mapNotNull { r ->
            val counting = r.exercises.filter { it.countsAsStrength }
            val mains = counting.filter { isMain(it) }
            val lateMain = counting.withIndex().firstOrNull { (i, e) ->
                isMain(e) && counting.take(i).any { !isMain(it) }
            }
            lateMain?.let { (i, e) ->
                val before = counting.take(i).first { !isMain(it) }
                OrderOffender(r.name, e.id, before.id, mains.map { it.id })
            }
        }
        return StrengthOrderRow(offenders) to (eligible.size - offenders.size).toDouble() / eligible.size
    }
}

private val TIMED_METRICS = setOf(
    ExerciseMetricType.Duration,
    ExerciseMetricType.WeightDuration,
    ExerciseMetricType.DistanceDuration,
    ExerciseMetricType.FloorsDuration,
    ExerciseMetricType.StepsDuration,
)

/**
 * Whether the sets of [this] exercise add to the weekly volume the review checks (07 section 3 "What H1 counts"):
 * the stored flag for a catalogue row; a custom row counts unless its metric type is time- or distance-based
 * (its stored flag is only the column default).
 */
fun Exercise.countsForVolume(): Boolean = if (isCustom) metricType !in TIMED_METRICS else countsAsStrength
