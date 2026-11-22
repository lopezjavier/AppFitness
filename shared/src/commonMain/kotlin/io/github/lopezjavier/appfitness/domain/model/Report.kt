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
    val teaser: ReportTeaser? = null,
)

/**
 * The progress teaser of one "Informes" row (`07` §3 (B)), stored by the server: a week has [ups] only, a block has
 * [ups] of [total]. [weightChangeKg] is null unless the report's own weight gate passed. Build it with
 * [reportTeaserOf]; the client never recomputes or re-gates these figures.
 */
data class ReportTeaser(val ups: Int?, val total: Int?, val weightChangeKg: Double?)

/**
 * The one place that normalises the stored teaser figures: null when nothing is left, a [total] without [ups] is
 * dropped (a ratio needs both), [ups] above [total] is rendered as stored, and a NaN or infinite weight is null.
 */
fun reportTeaserOf(ups: Int?, total: Int?, weightChangeKg: Double?): ReportTeaser? {
    val ratio = ups != null && total != null
    val finiteKg = weightChangeKg?.takeIf { it.isFinite() }
    return if (ups == null && finiteKg == null) {
        null
    } else {
        ReportTeaser(ups, total.takeIf { ratio }, finiteKg)
    }
}

/** One block of the "Informes" list: [number] counts blocks from the oldest (1); the review comes first in [rows]. */
data class ReportGroup(val planId: String, val number: Int, val rows: List<ReportItem>)

/**
 * Groups the list by plan in order of first appearance (the server serves newest first, so the newest block comes
 * first), numbers the blocks from the oldest, and puts the block review(s) before the weeks, each in served order.
 */
fun groupReportsByBlock(items: List<ReportItem>): List<ReportGroup> {
    val byPlan = items.groupBy { it.planId }
    return byPlan.entries.mapIndexed { index, (planId, inPlan) ->
        val (reviews, weeks) = inPlan.partition { it.kind == ReportKind.BlockReview }
        ReportGroup(planId, byPlan.size - index, reviews + weeks)
    }
}

/** A stored report opened read-only: the same models the live screens show. */
sealed interface PastReport {
    val item: ReportItem

    data class Week(override val item: ReportItem, val summary: WeekSummary) : PastReport

    data class Block(override val item: ReportItem, val review: BlockReview) : PastReport
}
