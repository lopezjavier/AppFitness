package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Used when Supabase is not configured: every call fails. */
@Suppress("TooManyFunctions") // One failing stub per repository operation.
class UnconfiguredWorkoutSessionRepository : WorkoutSessionRepository {
    private fun <T> fail(): Result<T> = Result.failure(AppException(AppError.Unknown))

    override suspend fun startFromRoutine(routineId: String): Result<String> = fail()

    override suspend fun getInProgress(): Result<WorkoutSession?> = fail()

    override suspend fun upsertSet(
        sessionId: String,
        exerciseId: String,
        exercisePosition: Int,
        supersetGroup: Int?,
        setIndex: Int,
        entry: SetEntry,
        completedAt: Instant?,
        setType: SetType,
        rpe: Double?,
    ): Result<Unit> = fail()

    override suspend fun listCompleted(offset: Int, limit: Int): Result<List<WorkoutSession>> = fail()

    override suspend fun exerciseSessions(exerciseId: String, offset: Int, limit: Int): Result<List<WorkoutSession>> =
        fail()

    override suspend fun getSession(sessionId: String): Result<WorkoutSession?> = fail()

    override suspend fun deleteSession(sessionId: String): Result<Unit> = fail()

    override suspend fun finish(sessionId: String): Result<Unit> = fail()

    override suspend fun discard(sessionId: String): Result<Unit> = fail()

    override suspend fun lastPerformance(exerciseId: String): Result<List<PreviousSet>> = fail()

    override suspend fun exerciseHistory(exerciseId: String, from: LocalDate?): Result<List<ExerciseHistoryPoint>> =
        fail()

    override suspend fun personalRecords(exerciseId: String): Result<List<PersonalRecord>> = fail()

    override suspend fun sessionRecords(exerciseId: String): Result<List<ExerciseSessionRecords>> = fail()
}
