package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import io.github.lopezjavier.appfitness.domain.model.WeeklyMuscleVolumeRow
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.repository.StrengthRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredStrengthRepository : StrengthRepository {
    private fun <T> fail(): Result<T> = Result.failure(AppException(AppError.Unknown))

    override suspend fun recentSessions(since: Instant): Result<List<WorkoutSession>> = fail()

    override suspend fun maxRepsHistory(exerciseId: String, from: LocalDate?): Result<List<MaxRepsPoint>> = fail()

    override suspend fun weeklyMuscleVolume(from: LocalDate): Result<List<WeeklyMuscleVolumeRow>> = fail()
}
