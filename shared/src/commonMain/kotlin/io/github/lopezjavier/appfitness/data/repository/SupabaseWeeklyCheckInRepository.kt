package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.lopezjavier.appfitness.data.remote.WeeklyCheckInDto
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CheckIn
import io.github.lopezjavier.appfitness.domain.repository.WeeklyCheckInRepository
import kotlinx.datetime.LocalDate

private const val CHECK_VIOLATION = "23514"

/** `weekly_check_ins` of the signed-in user (RLS owner policies, primary key `(user_id, week_start)`). */
class SupabaseWeeklyCheckInRepository(private val auth: Auth, private val postgrest: Postgrest) :
    WeeklyCheckInRepository {
    override suspend fun get(weekStart: LocalDate): Result<CheckIn?> = auth.ownRow {
        postgrest.from("weekly_check_ins")
            .select(Columns.raw(WeeklyCheckInDto.COLUMNS)) { filter { eq("week_start", weekStart.toString()) } }
            .decodeList<WeeklyCheckInDto>()
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun save(checkIn: CheckIn): Result<Unit> = auth.ownRow {
        postgrest.from("weekly_check_ins").upsert(WeeklyCheckInDto.of(checkIn)) { onConflict = "user_id,week_start" }
        Unit
    }.recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        if (cause.code == CHECK_VIOLATION) throw AppException(AppError.Validation, cause) else throw failure
    }
}
