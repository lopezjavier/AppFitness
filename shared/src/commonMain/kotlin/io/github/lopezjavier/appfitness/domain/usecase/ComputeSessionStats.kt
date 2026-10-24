package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession

/** Aggregates of a session, computed on the client: duration, completed sets and working volume. */
data class SessionStats(val durationSeconds: Long, val completedSets: Int, val volumeKg: Double)

object ComputeSessionStats {
    /** Completed sets and volume skip warm-up sets unless [includeWarmUp] is true;
     * [bodyWeightKg] feeds the bodyweight and assisted volume (see [CalculateVolume]). */
    operator fun invoke(
        session: WorkoutSession,
        includeWarmUp: Boolean = false,
        bodyWeightKg: Double? = null,
        perHandExerciseIds: Set<String> = emptySet(),
    ): SessionStats = SessionStats(
        durationSeconds = session.endedAt?.let {
            SessionDuration.seconds(session.startedAt, it, session.pausedAt, session.pausedTotalSeconds, it)
        } ?: 0L,
        completedSets = session.exercises.sumOf { exercise ->
            exercise.sets.count { it.isCompleted && (includeWarmUp || it.setType != SetType.WarmUp) }
        },
        volumeKg = session.exercises.sumOf {
            CalculateVolume(it.sets, includeWarmUp, bodyWeightKg, it.exerciseId in perHandExerciseIds)
        },
    )
}
