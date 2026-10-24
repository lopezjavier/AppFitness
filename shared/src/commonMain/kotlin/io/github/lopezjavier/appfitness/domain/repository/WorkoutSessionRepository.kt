package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Live workout sessions of the signed-in user. A user has at most one in-progress session. */
@Suppress("TooManyFunctions") // One function per session operation.
interface WorkoutSessionRepository {
    /** Starts a session with one pending set per routine target set; returns its id. */
    suspend fun startFromRoutine(routineId: String): Result<String>

    suspend fun getInProgress(): Result<WorkoutSession?>

    /** Creates or replaces the set at (session, exercisePosition, setIndex); [completedAt] null keeps it pending. */
    @Suppress("LongParameterList")
    suspend fun upsertSet(
        sessionId: String,
        exerciseId: String,
        exercisePosition: Int,
        supersetGroup: Int?,
        setIndex: Int,
        entry: SetEntry,
        completedAt: Instant?,
        setType: SetType = SetType.Working,
        rpe: Double? = null,
    ): Result<Unit>

    /** Completed sessions, newest first; a page is [limit] rows starting at [offset]. */
    suspend fun listCompleted(offset: Int, limit: Int): Result<List<WorkoutSession>>

    /**
     * Completed sessions containing the exercise, newest first; each holds one exercise entry with only that
     * exercise's completed sets, by set index. A page is [limit] sessions from [offset].
     */
    suspend fun exerciseSessions(exerciseId: String, offset: Int, limit: Int): Result<List<WorkoutSession>>

    /** A completed session with its sets; null when it does not exist or is not completed. */
    suspend fun getSession(sessionId: String): Result<WorkoutSession?>

    /** Deletes the session and its sets. */
    suspend fun deleteSession(sessionId: String): Result<Unit>

    /** Persists the rest of one exercise of the in-progress session (survives closing the app). */
    suspend fun setExerciseRest(sessionId: String, exercisePosition: Int, seconds: Int): Result<Unit>

    /** Closes the session as completed and drops its pending sets. */
    suspend fun finish(sessionId: String): Result<Unit>

    suspend fun discard(sessionId: String): Result<Unit>

    /** Pauses the session at [at]; pausing an already paused session keeps the first pause. */
    suspend fun pause(sessionId: String, at: Instant): Result<Unit>

    /** Resumes the session at [at], adding the paused span to its paused total; a no-op when not paused. */
    suspend fun resume(sessionId: String, at: Instant): Result<Unit>

    /** Deletes the set and shifts the later sets of that exercise down by one, atomically. */
    suspend fun deleteSet(sessionId: String, exercisePosition: Int, setIndex: Int): Result<Unit>

    /**
     * Completed sets of the latest finished session containing the exercise, by set index; empty if never done.
     * With a [routineId] only sessions of that routine count.
     */
    suspend fun lastPerformance(exerciseId: String, routineId: String? = null): Result<List<PreviousSet>>

    suspend fun exerciseHistory(exerciseId: String, from: LocalDate? = null): Result<List<ExerciseHistoryPoint>>

    /** Current best per record family of the exercise and the session that set it (04 §4.8); empty if never done. */
    suspend fun personalRecords(exerciseId: String): Result<List<PersonalRecord>>

    /** Record aggregates of every completed session containing the exercise, oldest first. */
    suspend fun sessionRecords(exerciseId: String): Result<List<ExerciseSessionRecords>>
}
