package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.GetReportParams
import io.github.lopezjavier.appfitness.data.remote.RecordPausedWeekParams
import io.github.lopezjavier.appfitness.data.remote.pastReportOf
import io.github.lopezjavier.appfitness.data.remote.reportItemsOf
import io.github.lopezjavier.appfitness.domain.model.PastReport
import io.github.lopezjavier.appfitness.domain.model.ReportItem
import io.github.lopezjavier.appfitness.domain.repository.ReportRepository
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonElement

/** The stored reports through `list_coach_reports`, `get_coach_report` and `record_paused_week_report`. */
class SupabaseReportRepository(private val auth: Auth, private val postgrest: Postgrest) : ReportRepository {
    override suspend fun list(): Result<List<ReportItem>> = auth.ownRow {
        reportItemsOf(postgrest.rpc("list_coach_reports").decodeAs<JsonElement>())
    }

    override suspend fun get(id: String): Result<PastReport?> = auth.ownRow {
        pastReportOf(postgrest.rpc("get_coach_report", GetReportParams(id)).decodeAs<JsonElement>())
    }

    override suspend fun recordPausedWeek(today: LocalDate): Result<Unit> = auth.ownRow {
        postgrest.rpc("record_paused_week_report", RecordPausedWeekParams(today))
        Unit
    }
}
