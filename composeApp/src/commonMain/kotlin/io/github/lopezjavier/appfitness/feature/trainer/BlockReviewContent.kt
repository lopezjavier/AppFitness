@file:Suppress("SpreadOperator") // stringResource takes varargs; the argument lists hold a few short strings.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private const val PREFIX = "block_review"

/**
 * The review body shared by the live and the stored screen (`readOnly`): the coach card, the progress cards in the
 * order of the goal (the weekly order, R7), "Objetivos", then the stall, phase and calorie cards. The coach card's
 * action scrolls to the pending decision card on the live screen; on a stored review it is plain text (R-60).
 */
@Composable
internal fun ReviewContent(
    state: BlockReviewUiState.Content,
    online: Boolean,
    onEvent: (BlockReviewEvent) -> Unit,
    readOnly: Boolean = false,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val animate = LocalAnimationsEnabled.current
    val cards = blockReviewCards(state, online, onEvent, readOnly)
    val coach = state.report?.let { report ->
        val target = if (readOnly) null else blockActionTarget(report.coach.action)
        val index = cards.indexOfFirst { it.key == target }
        // The coach card is item 0, so a card's grid index is its position in [cards] plus one.
        val open: (() -> Unit)? = if (index >= 0) {
            {
                scope.launch {
                    // R-97: no animation under "remove animations".
                    if (animate) gridState.animateScrollToItem(index + 1) else gridState.scrollToItem(index + 1)
                }
            }
        } else {
            null
        }
        SummaryItem("coach") { BlockReportCoachCard(report.coach, state.exerciseNames, open) }
    }
    HubGrid(state = gridState) {
        (listOfNotNull(coach) + cards).forEach { item ->
            if (item.fullSpan) fullSpanItem(item.key, item.content) else item(key = item.key) { item.content() }
        }
    }
}

/** The card a coach action points at on the same screen, or null when the action is not a link. */
private fun blockActionTarget(action: BlockReportAction): String? = when (action) {
    is BlockReportAction.PendingPhase -> "phase"
    is BlockReportAction.PendingCalorie -> "calorie"
    BlockReportAction.Stall -> "stalls"
    else -> null
}

/** Every card below the coach card, in order; a card with nothing to show is left out. */
private fun blockReviewCards(
    state: BlockReviewUiState.Content,
    online: Boolean,
    onEvent: (BlockReviewEvent) -> Unit,
    readOnly: Boolean,
): List<SummaryItem> = buildList {
    val review = state.review
    val names = state.exerciseNames
    val report = state.report
    val progress = report?.progress
    if (report != null && progress != null) {
        weekReportOrder(report.intention, progress.waist != null).forEach { kind ->
            blockProgressItem(kind, progress, names, review.endedOn)?.let { add(it) }
        }
    }
    report?.outcomes?.takeIf { it.isNotEmpty() }?.let { add(SummaryItem("outcomes") { OutcomesCard(it, names) }) }
    if (review.stalls.any { it.exerciseId in names }) {
        add(SummaryItem("stalls") { StallCard(review.stalls, names, PREFIX) })
    }
    review.phaseProposal?.let {
        add(
            SummaryItem("phase") {
                if (readOnly) StoredPhaseSection(it) else PhaseProposalSection(state, it, online, onEvent)
            },
        )
    }
    if (!readOnly && state.calorieProposal != null) {
        add(SummaryItem("calorie") { CalorieSection(state, online, onEvent) })
    }
}

/** One progress card of the block; a card with nothing to show (no records, no weigh-in, no waist) is null. */
private fun blockProgressItem(
    kind: ReportCardKind,
    progress: BlockProgressUi,
    names: Map<String, String>,
    reportEnd: LocalDate,
): SummaryItem? {
    val scope = ReportScope.Block
    return when (kind) {
        ReportCardKind.Strength -> SummaryItem("strength", fullSpan = false) {
            // The health note belongs to the live weekly report only: the block review never shows it.
            WeekReportStrengthCard(progress.strength, names, live = false, scope = scope)
        }

        ReportCardKind.Lifts -> if (progress.lifts.any { it.exerciseId in names }) {
            SummaryItem("lifts", fullSpan = false) { WeekReportLiftsCard(progress.lifts, names, scope) }
        } else {
            null
        }

        ReportCardKind.Records -> if (progress.records.any { it.exerciseId in names }) {
            SummaryItem("records", fullSpan = false) {
                WeekReportRecordsCard(progress.records, names, scope, progress.recordsTotal)
            }
        } else {
            null
        }

        ReportCardKind.Weight -> progress.weight?.let {
            SummaryItem("weight", fullSpan = false) { WeekReportWeightCard(it, reportEnd, live = false, scope = scope) }
        }

        ReportCardKind.Waist -> progress.waist?.let {
            SummaryItem("waist", fullSpan = false) { WeekReportWaistCard(it, scope) }
        }
    }
}
