package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.WorkoutSession

/** Aggregates of a session, computed on the client: duration, completed sets and working volume. */
data class SessionStats(val durationSeconds: Long, val completedSets: Int, val volumeKg: Double)

object ComputeSessionStats {
    operator fun invoke(session: WorkoutSession): SessionStats = SessionStats(
        durationSeconds = session.endedAt?.let { (it - session.startedAt).inWholeSeconds } ?: 0L,
        completedSets = session.exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } },
        volumeKg = session.exercises.sumOf { CalculateVolume(it.sets) },
    )
}
