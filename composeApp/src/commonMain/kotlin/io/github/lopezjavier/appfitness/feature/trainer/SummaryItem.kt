package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.coach_deload_headline
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** One grid item of the report: [fullSpan] cards take the row, the progress cards share it on wide screens. */
internal class SummaryItem(val key: String, val fullSpan: Boolean = true, val content: @Composable () -> Unit)

/** The kept card an action points at on the same screen, or null when the action is not a link. */
internal fun actionTarget(action: WeekReportAction): String? = when (action) {
    WeekReportAction.WeightDiagnosis -> "diagnosis"
    WeekReportAction.Stall -> "stalls"
    else -> null
}

/** Every card below the coach card, in order; a card with nothing to show is left out. */
internal fun summaryCards(state: WeekSummaryUiState.Content): List<SummaryItem> = buildList {
    val summary = state.summary
    val report = state.report
    report?.progress?.let { progress ->
        weekReportOrder(report.intention, progress.waist != null).forEach { kind ->
            progressItem(kind, progress, report, state, summary.endDate)?.let { add(it) }
        }
    }
    summary.nutrition?.diagnosis?.let { diagnosis ->
        add(
            SummaryItem("diagnosis") {
                DiagnosisCard(
                    diagnosis,
                    hasProposal = summary.nutrition?.decisions?.any { it.status == ProposalStatus.Pending } == true,
                )
            },
        )
    }
    val changeRows = visibleChangeRows(state)
    if (changeRows.isNotEmpty()) add(SummaryItem("changes") { ChangesSection(changeRows) })
    if (summary.stalls.any { it.exerciseId in state.exerciseNames }) {
        add(SummaryItem("stalls") { StallCard(summary.stalls, state.exerciseNames) })
    }
    if (WeekSummaryText.showsSeparateDeload(summary)) {
        add(
            SummaryItem("deload") {
                ReportNoteCard("week_summary_section_deload") {
                    SummaryLine(stringResource(Res.string.coach_deload_headline))
                }
            },
        )
    }
}

/** One progress card; a card that has nothing to show (no records, no waist) is null. */
private fun progressItem(
    kind: ReportCardKind,
    progress: WeekProgressUi,
    report: WeekReportUi,
    state: WeekSummaryUiState.Content,
    reportEnd: LocalDate,
): SummaryItem? {
    val names = state.exerciseNames
    return when (kind) {
        ReportCardKind.Strength ->
            SummaryItem("strength", fullSpan = false) { WeekReportStrengthCard(progress.strength, names, report.live) }

        ReportCardKind.Lifts -> if (progress.lifts.any { it.exerciseId in names }) {
            SummaryItem("lifts", fullSpan = false) { WeekReportLiftsCard(progress.lifts, names) }
        } else {
            null
        }

        ReportCardKind.Records -> if (progress.records.any { it.exerciseId in names }) {
            SummaryItem("records", fullSpan = false) { WeekReportRecordsCard(progress.records, names) }
        } else {
            null
        }

        ReportCardKind.Weight ->
            SummaryItem("weight", fullSpan = false) { WeekReportWeightCard(progress.weight, reportEnd, report.live) }

        ReportCardKind.Waist -> progress.waist?.let {
            SummaryItem("waist", fullSpan = false) { WeekReportWaistCard(it) }
        }
    }
}
