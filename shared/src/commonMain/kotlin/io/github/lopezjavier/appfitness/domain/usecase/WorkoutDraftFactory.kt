package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DraftExercise
import io.github.lopezjavier.appfitness.domain.model.DraftWorkoutSet
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.SetColumns
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraft
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import kotlinx.datetime.LocalDate

private const val SECONDS_PER_MINUTE = 60L

/** Builds the starting [WorkoutDraft] of a past workout or of an edit. Pure: the sources are never modified. */
object WorkoutDraftFactory {
    /**
     * A draft from [routine]: every planned set becomes a set with its planned weight and reps (0 weight when the
     * plan has none); rests and supersets are copied. [metricTypes] gives the entry shape of each exercise
     * (weight and reps when unknown).
     */
    fun fromRoutine(routine: Routine, metricTypes: Map<String, ExerciseMetricType>, date: LocalDate): WorkoutDraft =
        WorkoutDraft(
            name = routine.name,
            description = null,
            gym = null,
            date = date,
            routineId = routine.id,
            durationMinutes = 0,
            exercises = routine.exercises.sortedBy { it.position }.map { exercise ->
                val metric = metricTypes[exercise.exerciseId] ?: ExerciseMetricType.WeightReps
                DraftExercise(
                    exerciseId = exercise.exerciseId,
                    supersetGroup = exercise.supersetGroup,
                    restSeconds = exercise.restSeconds,
                    sets = exercise.plannedSets.map { planned ->
                        DraftWorkoutSet(
                            setType = planned.type,
                            rpe = null,
                            entry = SetColumns(weightKg = planned.weightKg ?: 0.0, reps = planned.reps).toEntry(metric),
                        )
                    },
                )
            },
        )

    /**
     * A draft from the completed sets of [session]. Exercises without completed sets are dropped and the rest are
     * renumbered; a superset whose members no longer agree on the set count or are no longer consecutive is
     * ungrouped. As a [asTemplate] the draft is a new workout (no routine link, no duration); otherwise it is the
     * editable copy of the session (routine link and duration kept, rounded to minutes).
     */
    fun fromSession(session: WorkoutSession, date: LocalDate, asTemplate: Boolean = false): WorkoutDraft {
        val exercises = session.exercises.sortedBy { it.position }.mapNotNull { exercise ->
            val sets = exercise.sets.filter { it.isCompleted }.sortedBy { it.setIndex }
            if (sets.isEmpty()) {
                null
            } else {
                DraftExercise(
                    exerciseId = exercise.exerciseId,
                    supersetGroup = exercise.supersetGroup,
                    restSeconds = session.exerciseRests[exercise.position] ?: RoutineBounds.DEFAULT_REST_SECONDS,
                    sets = sets.map {
                        DraftWorkoutSet(
                            it.setType,
                            it.rpe?.let(RpeScale::round),
                            it.entry,
                            it.notes.takeIf { !asTemplate },
                            it.noteKind.takeIf { !asTemplate },
                        )
                    },
                )
            }
        }
        return WorkoutDraft(
            name = session.name,
            description = null,
            gym = null,
            date = date,
            routineId = if (asTemplate) null else session.routineId,
            durationMinutes = if (asTemplate) 0 else session.durationMinutes(),
            exercises = withConsistentSupersets(exercises),
        )
    }

    private fun WorkoutSession.durationMinutes(): Int {
        val end = endedAt ?: return 0
        val seconds = (end - startedAt).inWholeSeconds.coerceAtLeast(0)
        return ((seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE).toInt()
    }

    private fun withConsistentSupersets(exercises: List<DraftExercise>): List<DraftExercise> {
        val broken = exercises.withIndex()
            .filter { it.value.supersetGroup != null }
            .groupBy { it.value.supersetGroup }
            .filterValues { members ->
                val consecutive = members.last().index - members.first().index + 1 == members.size
                !consecutive || members.map { it.value.sets.size }.distinct().size > 1
            }
            .keys
        return exercises.map { if (it.supersetGroup in broken) it.copy(supersetGroup = null) else it }
    }
}
