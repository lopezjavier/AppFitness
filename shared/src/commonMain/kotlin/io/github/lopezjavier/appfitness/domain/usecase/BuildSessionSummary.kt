package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import kotlin.time.Instant

/** What a finished session shows: duration, completed sets and, per exercise, volume and personal record. */
data class SessionSummary(val durationSeconds: Long, val completedSets: Int, val exercises: List<ExerciseSummary>)

data class ExerciseSummary(
    val position: Int,
    val exerciseId: String,
    val volume: Double,
    val isPersonalRecord: Boolean,
)

object BuildSessionSummary {
    /**
     * [history] maps exercise id to its previous sessions; null (history unavailable) shows no records. Records
     * compare the best estimated 1RM of the completed working sets (the first session of an exercise is a record).
     */
    operator fun invoke(
        session: WorkoutSession,
        endedAt: Instant,
        history: Map<String, List<ExerciseHistoryPoint>>?,
    ): SessionSummary {
        val best = session.exercises.groupBy { it.exerciseId }.mapValues { (_, rows) -> bestOneRepMax(rows) }
        val records = if (history == null) emptySet() else DetectPersonalRecords(best, history, session.id)
        return SessionSummary(
            durationSeconds = (endedAt - session.startedAt).inWholeSeconds,
            completedSets = session.exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } },
            exercises = session.exercises.map {
                ExerciseSummary(it.position, it.exerciseId, CalculateVolume(it.sets), it.exerciseId in records)
            },
        )
    }

    private fun bestOneRepMax(rows: List<SessionExercise>): Double? = rows.flatMap { it.sets }
        .filter { it.isCompleted && it.setType != SetType.WarmUp }
        .mapNotNull { set -> (set.entry as? SetEntry.WeightReps)?.let { EstimateOneRepMax(it.weightKg, it.reps) } }
        .maxOrNull()
}
