package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import io.github.lopezjavier.appfitness.domain.repository.CoachIntakeRepository

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredCoachIntakeRepository : CoachIntakeRepository {
    override suspend fun get(): Result<CoachIntake?> = Result.failure(AppException(AppError.Unknown))

    override suspend fun save(update: IntakeUpdate): Result<CoachIntake> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun acknowledgeMaintenanceHandover(): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}
