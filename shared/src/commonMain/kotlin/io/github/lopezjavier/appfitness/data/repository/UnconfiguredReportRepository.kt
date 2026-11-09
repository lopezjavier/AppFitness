package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.PastReport
import io.github.lopezjavier.appfitness.domain.model.ReportItem
import io.github.lopezjavier.appfitness.domain.repository.ReportRepository
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredReportRepository : ReportRepository {
    override suspend fun list(): Result<List<ReportItem>> = Result.failure(AppException(AppError.Unknown))

    override suspend fun get(id: String): Result<PastReport?> = Result.failure(AppException(AppError.Unknown))

    override suspend fun recordPausedWeek(today: LocalDate): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))
}
