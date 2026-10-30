package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CalorieChange
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredCoachPlanRepository : CoachPlanRepository {
    override suspend fun requestPlan(today: LocalDate): Result<CoachPlan> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun currentPlan(): Result<CoachPlan?> = Result.failure(AppException(AppError.Unknown))

    override suspend fun acceptedPlan(): Result<CoachPlan?> = Result.failure(AppException(AppError.Unknown))

    override suspend fun acceptPlan(planId: String, today: LocalDate): Result<CoachPlan> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun discardPlan(planId: String): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override suspend fun pendingCalorieChange(today: LocalDate): Result<CalorieChange?> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun decideCalorieChange(id: String, accept: Boolean, today: LocalDate): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))
}
