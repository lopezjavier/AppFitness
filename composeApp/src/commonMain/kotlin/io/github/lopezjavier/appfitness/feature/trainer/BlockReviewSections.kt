@file:Suppress("SpreadOperator") // stringResource takes varargs; the argument lists hold at most four short strings.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_lifts_header
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_lifts_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_lifts_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcomes_header
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcomes_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcomes_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_sessions
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_sessions_header
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_sessions_pct
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_stalled
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_waist_header
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_waist_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_waist_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_weight_header
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_weight_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_weight_help_cd
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.domain.model.BlockOutcome
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SessionsCard(state: BlockReviewUiState.Content) {
    val adherence = state.review.adherence
    val text = adherence.pct?.let {
        stringResource(Res.string.block_review_sessions_pct, adherence.completed, adherence.planned, "$it %")
    } ?: stringResource(Res.string.block_review_sessions, adherence.completed, adherence.planned)
    SummaryCard("block_review_section_sessions") {
        ListSectionHeader(stringResource(Res.string.block_review_sessions_header))
        SummaryLine(text)
    }
}

/** A section header with its "?" (R-57): the explanation lives in the sheet, not under the figures. */
@Composable
internal fun HeaderWithHelp(title: String, helpCd: String, helpBody: String, helpTag: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ListSectionHeader(title, Modifier.weight(1f))
        HelpButton(contentDescription = helpCd, title = title, body = helpBody, testTag = helpTag)
    }
}

@Composable
internal fun LiftsCard(state: BlockReviewUiState.Content) {
    val lifts = state.review.lifts.mapNotNull { lift -> state.exerciseNames[lift.exerciseId]?.let { lift to it } }
    if (lifts.isEmpty()) return
    val stalledLabel = stringResource(Res.string.block_review_stalled)
    val header = stringResource(Res.string.block_review_lifts_header)
    SummaryCard("block_review_section_lifts") {
        HeaderWithHelp(
            header,
            stringResource(Res.string.block_review_lifts_help_cd),
            stringResource(Res.string.block_review_lifts_help_body),
            "block_review_help_lifts",
        )
        lifts.forEach { (lift, name) ->
            val trend = BlockReviewText.trend(lift)
            AppListRow(
                headline = name,
                modifier = Modifier.testTag("block_review_lift_${lift.exerciseId}"),
                supporting = stringResource(trend.res, *trend.args.toTypedArray()),
                trailing = if (lift.stalled) {
                    {
                        StatusChip(
                            stalledLabel,
                            StatusTone.Caution,
                            Modifier.testTag("block_review_lift_${lift.exerciseId}_stall"),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
internal fun WeightCard(state: BlockReviewUiState.Content) {
    val line = BlockReviewText.weight(state.review.weight)
    SummaryCard("block_review_section_weight") {
        HeaderWithHelp(
            stringResource(Res.string.block_review_weight_header),
            stringResource(Res.string.block_review_weight_help_cd),
            stringResource(Res.string.block_review_weight_help_body),
            "block_review_help_weight",
        )
        SummaryLine(stringResource(line.res, *line.args.toTypedArray()))
    }
}

@Composable
internal fun OutcomesCard(state: BlockReviewUiState.Content) {
    val rows = state.review.outcomes.mapNotNull { outcome ->
        val name = if (outcome.kind == OutcomeKind.E1rm) state.exerciseNames[outcome.key] else null
        BlockReviewText.outcomeLine(outcome, name)?.let { outcome to it }
    }
    if (rows.isEmpty()) return
    SummaryCard("block_review_section_outcomes") {
        HeaderWithHelp(
            stringResource(Res.string.block_review_outcomes_header),
            stringResource(Res.string.block_review_outcomes_help_cd),
            stringResource(Res.string.block_review_outcomes_help_body),
            "block_review_help_outcomes",
        )
        rows.forEachIndexed { index, (outcome, line) -> OutcomeRow(index, outcome, line) }
    }
}

/** One outcome: its target and where it stands, with "Conseguido" / "No conseguido" as icon and text (R-41). */
@Composable
internal fun OutcomeRow(index: Int, outcome: BlockOutcome, line: BlockReviewText.Line) {
    val label = BlockReviewText.outcomeLabel(outcome)
    val tone = when (label.tone) {
        BlockReviewText.OutcomeTone.Success -> StatusTone.Good
        BlockReviewText.OutcomeTone.Neutral -> StatusTone.Caution
    }
    AppListRow(
        headline = stringResource(line.res, *line.args.toTypedArray()),
        modifier = Modifier.testTag("block_review_outcome_$index"),
        trailing = {
            StatusChip(stringResource(label.res), tone, Modifier.testTag("block_review_outcome_${index}_chip"))
        },
    )
}

@Composable
internal fun WaistCard(state: BlockReviewUiState.Content) {
    val waist = state.review.waist ?: return
    val line = BlockReviewText.waist(waist)
    SummaryCard("block_review_section_waist") {
        HeaderWithHelp(
            stringResource(Res.string.block_review_waist_header),
            stringResource(Res.string.block_review_waist_help_cd),
            stringResource(Res.string.block_review_waist_help_body),
            "block_review_help_waist",
        )
        SummaryLine(stringResource(line.res, *line.args.toTypedArray()), Modifier.testTag("block_review_waist_line"))
    }
}
