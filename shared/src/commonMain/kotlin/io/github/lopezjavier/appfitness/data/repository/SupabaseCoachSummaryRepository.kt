package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.MarkWeekSummarySeenParams
import io.github.lopezjavier.appfitness.data.remote.WeekSummaryParams
import io.github.lopezjavier.appfitness.data.remote.weekSummaryOf
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.repository.CoachSummaryRepository
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonElement

/** The weekly summary through `coach_week_summary` and `mark_coach_week_summary_seen`. */
class SupabaseCoachSummaryRepository(private val auth: Auth, private val postgrest: Postgrest) :
    CoachSummaryRepository {
    override suspend fun getLatest(today: LocalDate): Result<WeekSummary?> = auth.ownRow {
        weekSummaryOf(postgrest.rpc("coach_week_summary", WeekSummaryParams(today)).decodeAs<JsonElement>())
    }

    override suspend fun markSeen(planId: String, week: Int): Result<Unit> = auth.ownRow {
        postgrest.rpc("mark_coach_week_summary_seen", MarkWeekSummarySeenParams(planId, week))
        Unit
    }
}
