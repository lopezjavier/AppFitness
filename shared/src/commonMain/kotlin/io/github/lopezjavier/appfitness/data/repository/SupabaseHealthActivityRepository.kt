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
import io.github.lopezjavier.appfitness.domain.model.HealthActivityDay
import io.github.lopezjavier.appfitness.domain.model.HealthActivityHistory
import io.github.lopezjavier.appfitness.domain.repository.HealthActivityRepository
import io.github.lopezjavier.appfitness.domain.usecase.ActivityDayValues
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/** Reads `daily_activity`, `sleep_sessions` and `resting_heart_rate` of the signed-in user (RLS owner policies). */
class SupabaseHealthActivityRepository(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : HealthActivityRepository {
    override suspend fun history(from: LocalDate, toExclusive: LocalDate): Result<HealthActivityHistory> = signedIn {
        val daily = postgrest.from("daily_activity")
            .select(Columns.raw(DailyActivityRowDto.COLUMNS)) {
                filter {
                    and {
                        gte("day", from.toString())
                        lt("day", toExclusive.toString())
                    }
                }
                order("day", Order.ASCENDING)
            }
            .decodeList<DailyActivityRowDto>()
        val sleep = postgrest.from("sleep_sessions")
            .select(Columns.raw(SleepSessionRowDto.COLUMNS)) {
                filter {
                    and {
                        gte("ended_at", from.atStartOfDayIn(zone).toString())
                        lt("ended_at", toExclusive.atStartOfDayIn(zone).toString())
                    }
                }
                order("ended_at", Order.ASCENDING)
            }
            .decodeList<SleepSessionRowDto>()
        val resting = postgrest.from("resting_heart_rate")
            .select(Columns.raw(RestingHeartRateRowDto.COLUMNS)) {
                filter {
                    and {
                        gte("day", from.toString())
                        lt("day", toExclusive.toString())
                    }
                }
                order("day", Order.ASCENDING)
            }
            .decodeList<RestingHeartRateRowDto>()
        HealthActivityHistory(daily.map { it.toDomain() }, sleep.map { it.toDomain() }, resting.map { it.toDomain() })
    }

    override suspend fun day(day: LocalDate): Result<HealthActivityDay> =
        history(day, day.plus(DatePeriod(days = 1))).map { ActivityDayValues.of(it, day, zone) }

    private suspend fun <T> signedIn(block: suspend () -> T): Result<T> {
        if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
        return runMappingErrors(block)
    }
}

/** There is no server to read from. */
class UnconfiguredHealthActivityRepository : HealthActivityRepository {
    override suspend fun history(from: LocalDate, toExclusive: LocalDate): Result<HealthActivityHistory> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun day(day: LocalDate): Result<HealthActivityDay> = Result.failure(AppException(AppError.Unknown))
}
