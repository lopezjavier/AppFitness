package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.repository.CoachSummaryRepository
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredCoachSummaryRepository : CoachSummaryRepository {
    override suspend fun getLatest(today: LocalDate): Result<WeekSummary?> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun markSeen(planId: String, week: Int): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))
}
