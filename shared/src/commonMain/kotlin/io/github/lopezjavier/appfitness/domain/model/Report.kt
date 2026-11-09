package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** What a stored report is: a weekly summary or an end-of-block review (US-129). */
enum class ReportKind { WeekSummary, BlockReview }

/**
 * One row of the "Informes" list. [week] is null for a block review and for a paused week; [phaseDecision] is only
 * set on a block review the user decided.
 */
data class ReportItem(
    val id: String,
    val kind: ReportKind,
    val planId: String,
    val week: Int?,
    val paused: Boolean,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val createdAt: Instant,
    val phaseDecision: PhaseDecision?,
)

/** A stored report opened read-only: the same models the live screens show. */
sealed interface PastReport {
    val item: ReportItem

    data class Week(override val item: ReportItem, val summary: WeekSummary) : PastReport

    data class Block(override val item: ReportItem, val review: BlockReview) : PastReport
}
