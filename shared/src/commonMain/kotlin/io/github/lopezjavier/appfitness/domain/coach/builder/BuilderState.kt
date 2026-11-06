package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.usecase.FolderReviewCalculator

/** An exercise chosen for a slot with its planned weight (07 (e)); the planned reps are the slot's range minimum. */
data class Pick(
    val exerciseId: String,
    val group: MuscleGroup,
    val movementPattern: MovementPattern?,
    val plannedWeightKg: Double?,
) {
    companion object {
        /**
         * The last working weight when its logged reps fell inside the [slot] range, else empty (never converted from
         * another range or exercise).
         */
        @Suppress("ComplexCondition") // History present, positive weight and reps inside the range.
        fun of(exercise: Exercise, slot: Slot, history: ExerciseHistory?): Pick {
            val reps = history?.lastReps
            val weight = history?.lastWeightKg
            val planned = if (reps != null && weight != null && weight > 0 &&
                reps in slot.repMin..slot.repMax
            ) {
                weight
            } else {
                null
            }
            return Pick(exercise.id, exercise.muscleGroup, exercise.movementPattern, planned)
        }
    }
}

/** A slot of the routine and the exercise the user picked for it, if any. */
data class FilledSlot(val slot: Slot, val pick: Pick? = null)

/** One training day of the builder after the time fit. [liftingSeconds] is the fitted estimate. */
data class BuilderDay(
    val type: RoutineType,
    val name: String,
    val slots: List<FilledSlot>,
    val supersets: List<SupersetPair>,
    val extras: List<FilledSlot>,
    val cutSets: Boolean,
    val liftingSeconds: Int,
)

/** The weekly cardio arithmetic of `coach_assert_block`: 5-minute units, remainder to the first routines. */
data class CardioPlan(
    val targetMinWeek: Int,
    val plannedMinWeek: Int,
    val onYourOwnMin: Int,
    val perRoutineMin: List<Int>,
) {
    companion object {
        const val UNIT = 5
        const val MAX_UNITS_PER_ROUTINE = 12

        fun of(targetMinWeek: Int, days: Int): CardioPlan {
            val units = minOf(targetMinWeek / UNIT, MAX_UNITS_PER_ROUTINE * days)
            val per = List(days) { i -> UNIT * (units / days + if (i < units % days) 1 else 0) }
            return CardioPlan(targetMinWeek, UNIT * units, targetMinWeek - UNIT * units, per)
        }
    }
}

/** Outcome of starting a builder: a state, or the day whose routine does not fit even at 2 sets per slot. */
sealed interface BuilderStart {
    data class Ready(val state: BuilderState) : BuilderStart

    data class NoFit(val dayIndex: Int, val fit: DoesNotFit) : BuilderStart
}

/**
 * Immutable state of the block builder. Plain data with no behaviour tied to a platform, so the ViewModels can keep it
 * in a `SavedStateHandle` (ADR-40) after a serialisation of their own.
 */
data class BuilderState(
    val intention: TrainingIntention?,
    val sessionMinutes: Int,
    val equipment: List<Equipment>,
    val days: List<BuilderDay>,
    val cardioTargetMinWeek: Int,
    val cardioExerciseId: String?,
) {
    val score: BuilderScoreResult get() = BuilderScore.score(days, intention)

    /** "Guardar bloque" is enabled. */
    val canSave: Boolean get() = score.complete

    val cardioPlan: CardioPlan get() = CardioPlan.of(cardioTargetMinWeek, days.size)

    fun pick(day: Int, slotIndex: Int, pick: Pick): BuilderState = updateDay(day) {
        it.copy(slots = it.slots.mapIndexed { i, s -> if (i == slotIndex) s.copy(pick = pick) else s })
    }

    fun clear(day: Int, slotIndex: Int): BuilderState = updateDay(day) {
        it.copy(slots = it.slots.mapIndexed { i, s -> if (i == slotIndex) s.copy(pick = null) else s })
    }

    /** Adds an extra of the pick's own group with 2 sets and the accessory values. */
    fun addExtra(day: Int, pick: Pick): BuilderState = updateDay(day) {
        it.copy(extras = it.extras + FilledSlot(SectionPrescription.extraSlot(pick.group, intention), pick))
    }

    fun removeExtra(day: Int, extraIndex: Int): BuilderState = updateDay(day) {
        it.copy(extras = it.extras.filterIndexed { i, _ -> i != extraIndex })
    }

    fun withCardio(exerciseId: String?): BuilderState = copy(cardioExerciseId = exerciseId)

    /** Minutes by which the extras of [day] push the routine past the session length; 0 when none. */
    fun extrasOverMinutes(day: Int): Int {
        val d = days[day]
        val fit = Fit(d.slots.map { it.slot }, d.supersets, d.cutSets, d.liftingSeconds)
        return TimeFit.overMinutes(fit, d.extras.map { it.slot }, sessionMinutes)
    }

    private fun updateDay(day: Int, change: (BuilderDay) -> BuilderDay): BuilderState =
        copy(days = days.mapIndexed { i, d -> if (i == day) change(d) else d })

    /** The block as the server stores it, or null while a required slot is empty. */
    fun toBuiltBlock(): BuiltBlock? {
        if (!canSave) return null
        val cardio = cardioPlan
        return BuiltBlock(
            daysPerWeek = days.size,
            intention = intention,
            equipment = equipment,
            sessionMinutes = sessionMinutes,
            routineTotalMin = cardio.perRoutineMin.map { sessionMinutes + it },
            cardio = cardio,
            source = BuiltBlock.SOURCE_BUILDER,
            sessions = days.mapIndexed { i, day ->
                day.toSession(intention, cardio.perRoutineMin[i], cardioExerciseId)
            },
        )
    }

    companion object {
        /** Starts a builder: prescribes [types], fits every routine to [sessionMinutes] and leaves every slot empty. */
        @Suppress("LongParameterList") // The preferences the block is built from.
        fun start(
            types: List<RoutineType>,
            intention: TrainingIntention?,
            sessionMinutes: Int,
            equipment: List<Equipment>,
            cardioTargetMinWeek: Int,
            cardioExerciseId: String?,
            targets: Map<MuscleGroup, Int> = emptyMap(),
            hasMainLift: (MuscleGroup) -> Boolean = { true },
        ): BuilderStart {
            val plans = SectionPrescription.prescribe(types, intention, targets, hasMainLift)
            val names = routineNames(types)
            val days = plans.mapIndexed { i, plan ->
                when (val fit = TimeFit.fit(plan, intention, sessionMinutes)) {
                    is DoesNotFit -> return BuilderStart.NoFit(i, fit)

                    is Fit -> BuilderDay(
                        type = plan.type,
                        name = names[i],
                        slots = fit.slots.map { FilledSlot(it) },
                        supersets = fit.supersets,
                        extras = emptyList(),
                        cutSets = fit.cutSets,
                        liftingSeconds = fit.seconds,
                    )
                }
            }
            return BuilderStart.Ready(
                BuilderState(intention, sessionMinutes, equipment, days, cardioTargetMinWeek, cardioExerciseId),
            )
        }

        /** "Torso A", "Torso B" when a type repeats, else the plain type name. */
        fun routineNames(types: List<RoutineType>): List<String> = types.mapIndexed { i, type ->
            if (types.count { it == type } ==
                1
            ) {
                type.displayName
            } else {
                "${type.displayName} ${'A' + types.take(i).count { it == type }}"
            }
        }
    }
}

private fun roleOf(pattern: MovementPattern?) =
    if (pattern in FolderReviewCalculator.MAIN_PATTERNS) BuiltExercise.ROLE_MAIN else BuiltExercise.ROLE_ISOLATION

private fun BuilderDay.toSession(intention: TrainingIntention?, cardioMin: Int, cardioId: String?): BuiltSession {
    val pairOf = supersets.flatMapIndexed { n, p -> listOf(p.first to n + 1, p.second to n + 1) }.toMap()
    val required = slots.mapIndexed { i, s -> s to pairOf[i] }
    val all = required + extras.map { it to null }
    val lifts = all.map { (filled, group) ->
        val slot = filled.slot
        val pick = checkNotNull(filled.pick)
        BuiltExercise(
            role = roleOf(pick.movementPattern),
            exerciseId = pick.exerciseId,
            sets = slot.sets,
            repMin = slot.repMin,
            repMax = slot.repMax,
            restSeconds = slot.restSeconds,
            targetRpe = slot.rpe,
            plannedWeightKg = pick.plannedWeightKg,
            supersetGroup = group,
            durationMin = null,
            pace = null,
        )
    }
    // 07 (c): "Ganar fuerza" moves main-pattern exercises first, keeping the rest of the order (C4b).
    val ordered = if (intention == TrainingIntention.Strength) {
        lifts.filter { it.role == BuiltExercise.ROLE_MAIN } + lifts.filter { it.role != BuiltExercise.ROLE_MAIN }
    } else {
        lifts
    }
    val cardio = if (cardioMin > 0 && cardioId != null) {
        listOf(BuiltExercise.cardio(cardioId, cardioMin))
    } else {
        emptyList()
    }
    return BuiltSession(name, type.groups.map { it.name.lowercase() }, ordered + cardio)
}

/** Stored shape of a block built on the device (`save_built_coach_block`); mapped to JSON in the data layer. */
data class BuiltBlock(
    val daysPerWeek: Int,
    val intention: TrainingIntention?,
    val equipment: List<Equipment>,
    val sessionMinutes: Int,
    val routineTotalMin: List<Int>,
    val cardio: CardioPlan,
    val source: String,
    val sessions: List<BuiltSession>,
) {
    companion object {
        const val SOURCE_BUILDER = "builder"
        const val SOURCE_FOLDER = "folder"
        const val BLOCK_WEEKS = 4
    }
}

data class BuiltSession(val name: String, val focus: List<String>, val exercises: List<BuiltExercise>)

data class BuiltExercise(
    val role: String,
    val exerciseId: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val targetRpe: Int,
    val plannedWeightKg: Double?,
    val supersetGroup: Int?,
    val durationMin: Int?,
    val pace: String?,
) {
    companion object {
        const val ROLE_MAIN = "main"
        const val ROLE_ISOLATION = "isolation"
        const val ROLE_CARDIO = "cardio"
        const val PACE = "suave"

        fun cardio(exerciseId: String, minutes: Int) = BuiltExercise(
            ROLE_CARDIO, exerciseId, 1, 0, 0, 0, 0, null, null, minutes, PACE,
        )
    }
}
