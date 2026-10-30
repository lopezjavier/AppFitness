package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.DeloadProposal
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.repository.CoachWeeksRepository
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredCoachWeeksRepository : CoachWeeksRepository {
    override suspend fun weeks(planId: String, today: LocalDate): Result<List<PlanWeek>> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun pendingDeload(today: LocalDate): Result<DeloadProposal?> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun decideDeload(id: String, accept: Boolean): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))
}
