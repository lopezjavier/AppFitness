package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachUsage
import io.github.lopezjavier.appfitness.domain.repository.CoachUsageRepository
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredCoachUsageRepository : CoachUsageRepository {
    override suspend fun currentMonth(today: LocalDate): Result<CoachUsage> =
        Result.failure(AppException(AppError.Unknown))
}
