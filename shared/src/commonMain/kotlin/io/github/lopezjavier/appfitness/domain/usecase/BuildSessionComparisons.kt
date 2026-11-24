package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ComparableSession
import io.github.lopezjavier.appfitness.domain.model.ComparisonSession
import io.github.lopezjavier.appfitness.domain.model.ComparisonSet
import io.github.lopezjavier.appfitness.domain.model.ExerciseComparisonInput
import io.github.lopezjavier.appfitness.domain.model.SessionComparison
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * "Frente a la última vez" of a finished session (US-147): one [SessionComparison] per strength exercise with at least
 * one completed working set, in session order.
 */
object BuildSessionComparisons {
    /**
     * [remote] is the `session_comparison` answer; null when it could not be read. Then every exercise is built on
     * the device from the finished session's own working sets and reads "Sin comparar": there is nothing to compare
     * with and no discomfort state (C-221), never an error.
     */
    operator fun invoke(
        finished: WorkoutSession,
        remote: List<ExerciseComparisonInput>?,
        inCut: Boolean,
    ): List<SessionComparison> {
        val inputs = remote ?: onDevice(finished)
        return inputs.mapNotNull { input ->
            val now = ComparableSession.of(input.current) ?: return@mapNotNull null
            val before = input.previous?.let { ComparableSession.of(it) }
            CompareToPreviousSession(input.exerciseId, now, before, inCut, input.openDiscomfort)
        }
    }

    private fun onDevice(finished: WorkoutSession): List<ExerciseComparisonInput> {
        val date = finished.startedAt.toLocalDateTime(TimeZone.UTC).date
        return finished.exercises
            .sortedBy { it.position }
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, occurrences) ->
                val sets = occurrences.flatMap { occurrence ->
                    occurrence.sets.sortedBy { it.setIndex }
                        .filter { it.isCompleted && it.setType == SetType.Working }
                        .mapNotNull { set ->
                            loadAndReps(set.entry)?.let { (kg, reps) -> ComparisonSet(kg, reps, set.rpe) }
                        }
                }
                if (sets.isEmpty()) {
                    null
                } else {
                    ExerciseComparisonInput(
                        exerciseId = exerciseId,
                        openDiscomfort = false,
                        current = ComparisonSession(finished.id, date, deload = false, sets = sets),
                        previous = null,
                    )
                }
            }
    }

    /** The load and reps of a loaded strength set; cardio and other entries have none. */
    private fun loadAndReps(entry: SetEntry): Pair<Double, Int>? = when (entry) {
        is SetEntry.WeightReps -> entry.weightKg to entry.reps
        is SetEntry.Weighted -> entry.addedKg to entry.reps
        is SetEntry.Bodyweight -> entry.addedKg to entry.reps
        else -> null
    }
}
