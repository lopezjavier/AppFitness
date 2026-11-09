package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.PastReport
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.ReportItem
import io.github.lopezjavier.appfitness.domain.model.ReportKind
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.repository.ReportRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

internal fun reportItem(
    id: String = "report-1",
    kind: ReportKind = ReportKind.WeekSummary,
    week: Int? = 2,
    paused: Boolean = false,
    phaseDecision: PhaseDecision? = null,
    createdAt: Instant = Instant.parse("2026-11-10T08:00:00Z"),
) = ReportItem(
    id = id,
    kind = kind,
    planId = "plan-1",
    week = week,
    paused = paused,
    periodStart = LocalDate(2026, 11, 3),
    periodEnd = LocalDate(2026, 11, 9),
    createdAt = createdAt,
    phaseDecision = phaseDecision,
)

internal fun weekReport(id: String = "report-1", summary: WeekSummary = weekSummary(seen = true)) =
    PastReport.Week(reportItem(id), summary)

internal fun blockReport(id: String = "report-2", review: BlockReview = blockReview(seen = true)) =
    PastReport.Block(reportItem(id, ReportKind.BlockReview, week = null, phaseDecision = review.phaseDecision), review)

/** In-memory [ReportRepository]: [items] and [reports] are served as set; failures are switchable. */
internal class FakeReportRepository : ReportRepository {
    var items: Result<List<ReportItem>> = Result.success(emptyList())
    val reports = mutableMapOf<String, Result<PastReport?>>()
    var recordResult: Result<Unit> = Result.success(Unit)
    var listCalls = 0
    val gets = mutableListOf<String>()
    val recorded = mutableListOf<LocalDate>()

    override suspend fun list(): Result<List<ReportItem>> {
        listCalls++
        return items
    }

    override suspend fun get(id: String): Result<PastReport?> {
        gets += id
        return reports[id] ?: Result.success(null)
    }

    override suspend fun recordPausedWeek(today: LocalDate): Result<Unit> {
        recorded += today
        return recordResult
    }
}
