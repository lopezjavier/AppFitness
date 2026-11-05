package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.repository.CoachWeeksRepository
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredCoachWeeksRepository : CoachWeeksRepository {
    override suspend fun weeks(planId: String, today: LocalDate): Result<List<PlanWeek>> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun evaluateDeload(today: LocalDate): Result<CoachChange?> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun requestDeload(): Result<CoachChange> = Result.failure(AppException(AppError.Unknown))

    override suspend fun cancelDeload(changeId: String): Result<CoachChange> =
        Result.failure(AppException(AppError.Unknown))
}
