package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
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
    /** The record families this session beat for the exercise (never contains total reps). */
    val records: Set<PersonalRecordType> = emptySet(),
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
        includeWarmUp: Boolean = false,
        bodyWeightKg: Double? = null,
        doubleDumbbellExerciseIds: Set<String> = emptySet(),
        sessionRecords: Map<String, List<ExerciseSessionRecords>>? = null,
    ): SessionSummary {
        val beaten = PersonalRecordCatalogue.beatenIn(
            session,
            sessionRecords,
            includeWarmUp,
            bodyWeightKg,
            doubleDumbbellExerciseIds,
        )
        val best = session.exercises.groupBy { it.exerciseId }
            .mapValues { (_, rows) -> bestOneRepMax(rows, includeWarmUp) }
        val records = if (history == null) emptySet() else DetectPersonalRecords(best, history, session.id)
        return SessionSummary(
            durationSeconds = (endedAt - session.startedAt).inWholeSeconds,
            completedSets = session.exercises.sumOf { exercise ->
                exercise.sets.count { it.isCompleted && (includeWarmUp || it.setType != SetType.WarmUp) }
            },
            exercises = session.exercises.map {
                ExerciseSummary(
                    it.position,
                    it.exerciseId,
                    CalculateVolume(it.sets, includeWarmUp, bodyWeightKg, it.exerciseId in doubleDumbbellExerciseIds),
                    it.exerciseId in records,
                    beaten[it.exerciseId].orEmpty(),
                )
            },
        )
    }
}
