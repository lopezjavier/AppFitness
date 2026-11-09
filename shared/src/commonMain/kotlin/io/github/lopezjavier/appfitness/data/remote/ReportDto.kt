package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.PastReport
import io.github.lopezjavier.appfitness.domain.model.ReportItem
import io.github.lopezjavier.appfitness.domain.model.ReportKind
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

@Serializable
internal data class GetReportParams(@SerialName("p_id") val id: String)

@Serializable
internal data class RecordPausedWeekParams(@SerialName("p_today") val today: LocalDate)

private val REPORT_KINDS = mapOf("week_summary" to ReportKind.WeekSummary, "block_review" to ReportKind.BlockReview)

/**
 * Reads one `list_coach_reports` row (or the header of a `get_coach_report` answer). An unknown kind or a missing
 * identity key (id, plan, period, creation time) yields null, so the row is dropped, never a crash.
 */
internal fun reportItemOf(obj: JsonObject): ReportItem? {
    val kind = obj.text("kind")?.let(REPORT_KINDS::get)
    val createdAt = obj.text("created_at")?.let { runCatching { Instant.parse(it) }.getOrNull() }
    return runCatching {
        ReportItem(
            id = requireNotNull(obj.text("id")),
            kind = requireNotNull(kind),
            planId = requireNotNull(obj.text("plan_id")),
            week = obj.int("week"),
            paused = obj.truth("paused"),
            periodStart = requireNotNull(obj.day("period_start")),
            periodEnd = requireNotNull(obj.day("period_end")),
            createdAt = requireNotNull(createdAt),
            phaseDecision = DECISIONS[obj.text("phase_decision")],
        )
    }.getOrNull()
}

/** The `list_coach_reports` answer in server order; anything but an array is an empty list. */
internal fun reportItemsOf(element: JsonElement): List<ReportItem> =
    (element as? JsonArray).orEmpty().mapNotNull { it.obj()?.let(::reportItemOf) }

/**
 * The `get_coach_report` answer: the header plus the snapshot read by the same mappers the live screens use
 * ([weekSummaryOf], [reviewBodyOf]). JSON null, an unknown kind or an unreadable body yields null.
 */
internal fun pastReportOf(element: JsonElement): PastReport? {
    val obj = element.obj()
    val item = obj?.let(::reportItemOf)
    val snapshot = obj?.get("snapshot")
    return when {
        item == null || snapshot == null -> null
        item.kind == ReportKind.WeekSummary -> weekSummaryOf(snapshot)?.let { PastReport.Week(item, it) }
        else -> snapshot.obj()?.let(::reviewBodyOf)?.let { PastReport.Block(item, it) }
    }
}
