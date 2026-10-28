package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.lopezjavier.appfitness.data.remote.DailyActivityRowDto
import io.github.lopezjavier.appfitness.data.remote.RestingHeartRateRowDto
import io.github.lopezjavier.appfitness.data.remote.SleepSessionRowDto
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.HealthActivitySummary
import io.github.lopezjavier.appfitness.domain.repository.HealthActivityRepository

private const val RECENT_DAYS = 7L

/** Reads `daily_activity`, `sleep_sessions` and `resting_heart_rate` of the signed-in user (RLS owner policies). */
class SupabaseHealthActivityRepository(private val auth: Auth, private val postgrest: Postgrest) :
    HealthActivityRepository {
    override suspend fun latestSummary(): Result<HealthActivitySummary> = signedIn {
        val days = postgrest.from("daily_activity")
            .select(Columns.raw(DailyActivityRowDto.COLUMNS)) {
                order("day", Order.DESCENDING)
                limit(RECENT_DAYS)
            }
            .decodeList<DailyActivityRowDto>()
        val sleep = postgrest.from("sleep_sessions")
            .select(Columns.raw(SleepSessionRowDto.COLUMNS)) {
                order("ended_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<SleepSessionRowDto>()
            .firstOrNull()
        val resting = postgrest.from("resting_heart_rate")
            .select(Columns.raw(RestingHeartRateRowDto.COLUMNS)) {
                order("day", Order.DESCENDING)
                limit(1)
            }
            .decodeList<RestingHeartRateRowDto>()
            .firstOrNull()
        HealthActivitySummary(sleep?.toDomain(), days.map { it.toDomain() }, resting?.toDomain())
    }

    private suspend fun <T> signedIn(block: suspend () -> T): Result<T> {
        if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
        return runMappingErrors(block)
    }
}

/** There is no server to read from. */
class UnconfiguredHealthActivityRepository : HealthActivityRepository {
    override suspend fun latestSummary(): Result<HealthActivitySummary> = Result.failure(AppException(AppError.Unknown))
}
