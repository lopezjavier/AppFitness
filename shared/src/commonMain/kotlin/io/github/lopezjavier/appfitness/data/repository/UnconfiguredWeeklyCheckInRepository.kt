package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CheckIn
import io.github.lopezjavier.appfitness.domain.repository.WeeklyCheckInRepository
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: nothing can be read or stored. */
class UnconfiguredWeeklyCheckInRepository : WeeklyCheckInRepository {
    override suspend fun get(weekStart: LocalDate): Result<CheckIn?> = Result.failure(AppException(AppError.Unknown))

    override suspend fun save(checkIn: CheckIn): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}
