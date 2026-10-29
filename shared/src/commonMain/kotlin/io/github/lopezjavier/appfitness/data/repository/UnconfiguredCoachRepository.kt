package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.repository.CoachRepository
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredCoachRepository : CoachRepository {
    override suspend fun derivedValues(today: LocalDate): Result<CoachDerivedValues> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun confirm(today: LocalDate, activityLevel: ActivityLevel?): Result<CoachDerivedValues> =
        Result.failure(AppException(AppError.Unknown))
}
