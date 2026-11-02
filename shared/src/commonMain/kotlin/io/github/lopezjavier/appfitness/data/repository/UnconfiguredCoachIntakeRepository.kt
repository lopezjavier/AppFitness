package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import io.github.lopezjavier.appfitness.domain.repository.CoachIntakeRepository
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredCoachIntakeRepository : CoachIntakeRepository {
    override suspend fun get(): Result<CoachIntake?> = Result.failure(AppException(AppError.Unknown))

    override suspend fun save(update: IntakeUpdate): Result<CoachIntake> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun freeTextLines(today: LocalDate): Result<List<FreeTextLine>> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun setLineExcluded(kind: FreeTextKind, sourceId: String, excluded: Boolean): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun acknowledgeMaintenanceHandover(): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}
