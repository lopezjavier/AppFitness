package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
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

    suspend fun startEmpty(): Result<String>

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
    ): Result<Unit>

    /** Completed sessions, newest first; a page is [limit] rows starting at [offset]. */
    suspend fun listCompleted(offset: Int, limit: Int): Result<List<WorkoutSession>>

    /** A completed session with its sets; null when it does not exist or is not completed. */
    suspend fun getSession(sessionId: String): Result<WorkoutSession?>

    /** Deletes the session and its sets. */
    suspend fun deleteSession(sessionId: String): Result<Unit>

    /** Closes the session as completed and drops its pending sets. */
    suspend fun finish(sessionId: String): Result<Unit>

    suspend fun discard(sessionId: String): Result<Unit>

    /** Completed sets of the latest finished session containing the exercise, by set index; empty if never done. */
    suspend fun lastPerformance(exerciseId: String): Result<List<PreviousSet>>

    suspend fun exerciseHistory(exerciseId: String, from: LocalDate? = null): Result<List<ExerciseHistoryPoint>>
}
