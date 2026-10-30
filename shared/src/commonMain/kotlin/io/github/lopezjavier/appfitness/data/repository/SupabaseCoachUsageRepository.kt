package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.lopezjavier.appfitness.data.remote.CoachUsageDto
import io.github.lopezjavier.appfitness.domain.model.CoachUsage
import io.github.lopezjavier.appfitness.domain.repository.CoachUsageRepository
import kotlinx.datetime.LocalDate

/** The signed-in user's `coach_usage` row (RLS select only; only the function writes it). */
class SupabaseCoachUsageRepository(private val auth: Auth, private val postgrest: Postgrest) : CoachUsageRepository {
    override suspend fun currentMonth(today: LocalDate): Result<CoachUsage> = auth.ownRow {
        val month = LocalDate(today.year, today.month, 1)
        postgrest.from("coach_usage")
            .select { filter { eq("month", month.toString()) } }
            .decodeList<CoachUsageDto>()
            .firstOrNull()?.toDomain() ?: CoachUsage.empty(month)
    }
}
