@file:Suppress("SpreadOperator") // stringResource takes varargs; the argument lists hold at most four short strings.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
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
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_stalled
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_trend_none
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_waist_header
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_waist_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_waist_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_weight_header
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_weight_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_weight_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flag
import io.github.lopezjavier.appfitness.composeapp.resources.ic_monitor_weight
import io.github.lopezjavier.appfitness.composeapp.resources.px_trainer_record
import io.github.lopezjavier.appfitness.composeapp.resources.report_lift_change
import io.github.lopezjavier.appfitness.composeapp.resources.report_lift_range
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppPaint
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.RecordBadge
import io.github.lopezjavier.appfitness.designsystem.SectionCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.designsystem.TrainerBeforeAfter
import io.github.lopezjavier.appfitness.designsystem.TrainerTintedRow
import io.github.lopezjavier.appfitness.designsystem.TrendDirection
import io.github.lopezjavier.appfitness.designsystem.TrendPill
import io.github.lopezjavier.appfitness.designsystem.TrendTone
import io.github.lopezjavier.appfitness.domain.model.BlockLift
import io.github.lopezjavier.appfitness.domain.model.BlockOutcome
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BlockSessionsCard(state: BlockReviewUiState.Content) {
    val adherence = state.review.adherence
    SessionsCard(
        tag = "block_review_section_sessions",
        completed = adherence.completed,
        planned = adherence.planned,
        percent = adherence.pct?.let { "$it %" },
    )
}

/** The "?" of a section card header (R-57): the explanation lives in the sheet, not under the figures. */
@Composable
private fun SectionHelp(title: String, helpCd: String, helpBody: String, helpTag: String) {
    HelpButton(contentDescription = helpCd, title = title, body = helpBody, testTag = helpTag)
}

@Composable
internal fun LiftsCard(state: BlockReviewUiState.Content) {
    val lifts = state.review.lifts.mapNotNull { lift -> state.exerciseNames[lift.exerciseId]?.let { lift to it } }
    if (lifts.isEmpty()) return
    val stalledLabel = stringResource(Res.string.block_review_stalled)
    val header = stringResource(Res.string.block_review_lifts_header)
    SectionCard(
        header,
        testTag = "block_review_section_lifts",
        help = {
            SectionHelp(
                header,
                stringResource(Res.string.block_review_lifts_help_cd),
                stringResource(Res.string.block_review_lifts_help_body),
                "block_review_help_lifts",
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            lifts.forEach { (lift, name) -> LiftRow(lift, name, stalledLabel) }
        }
    }
}

/** One main lift: icon badge, name, the e1RM before and after as two bars, the weekly trend and the change pill. */
@Composable
private fun LiftRow(lift: BlockLift, name: String, stalledLabel: String) {
    val first = lift.firstE1rm
    val last = lift.lastE1rm
    val change = if (first != null && last != null) lift.changeKg ?: (last - first) else null
    val record = change != null && change > 0 && lift.weeks.isNotEmpty() && last == lift.weeks.maxOf { it.e1rm }
    TrainerTintedRow(
        accent = if (record) AppPaint.Gold else AppTheme.colors.athleticCyan,
        modifier = Modifier.testTag("block_review_lift_${lift.exerciseId}"),
        leading = { IconBadge(Res.drawable.ic_fitness_center, AppTheme.colors.athleticCyan) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = name, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            if (first != null && last != null) {
                TrainerBeforeAfter(
                    before = first,
                    after = last,
                    beforeLabel = WeekSummaryText.kg(first),
                    afterLabel = WeekSummaryText.kg(last),
                    description = stringResource(
                        Res.string.report_lift_range,
                        WeekSummaryText.kg(first),
                        WeekSummaryText.kg(last),
                    ),
                )
            } else {
                Text(
                    text = stringResource(Res.string.block_review_trend_none),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
            if (change != null || lift.stalled || record) LiftBadges(lift, change, record, stalledLabel)
        }
    }
}

@Composable
private fun LiftBadges(lift: BlockLift, change: Double?, record: Boolean, stalledLabel: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (change != null) LiftChangePill(lift, change)
        if (record) RecordBadge(stringResource(Res.string.px_trainer_record))
        if (lift.stalled) {
            StatusChip(
                stalledLabel,
                StatusTone.Caution,
                Modifier.testTag("block_review_lift_${lift.exerciseId}_stall"),
            )
        }
    }
}

/** "+5 kg" as a pill: favourable up, unfavourable down, neutral flat (the glyph and the arrow never carry it alone). */
@Composable
private fun LiftChangePill(lift: BlockLift, change: Double) {
    val tone = when {
        change > 0 -> TrendTone.Favorable
        change < 0 -> TrendTone.Unfavorable
        else -> TrendTone.Neutral
    }
    val direction = when {
        change > 0 -> TrendDirection.Up
        change < 0 -> TrendDirection.Down
        else -> TrendDirection.Flat
    }
    TrendPill(
        text = stringResource(Res.string.report_lift_change, WeekSummaryText.signedKg(change)),
        tone = tone,
        modifier = Modifier.testTag("block_review_lift_${lift.exerciseId}_change"),
        direction = direction,
    )
}

@Composable
internal fun WeightCard(state: BlockReviewUiState.Content) {
    val line = BlockReviewText.weight(state.review.weight)
    val header = stringResource(Res.string.block_review_weight_header)
    SectionCard(
        header,
        testTag = "block_review_section_weight",
        help = {
            SectionHelp(
                header,
                stringResource(Res.string.block_review_weight_help_cd),
                stringResource(Res.string.block_review_weight_help_body),
                "block_review_help_weight",
            )
        },
    ) {
        val first = state.review.weight?.firstAvgKg
        val last = state.review.weight?.lastAvgKg
        if (first != null && last != null) {
            TrainerTintedRow(
                accent = AppTheme.colors.athleticCyan,
                modifier = Modifier.testTag("block_review_weight_graphic"),
                leading = { IconBadge(Res.drawable.ic_monitor_weight, AppTheme.colors.athleticCyan) },
            ) {
                TrainerBeforeAfter(
                    first,
                    last,
                    WeekSummaryText.kg(first),
                    WeekSummaryText.kg(last),
                    color = AppTheme.colors.athleticCyan,
                    dropColor = AppTheme.colors.neonLime,
                )
            }
        }
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
    val header = stringResource(Res.string.block_review_outcomes_header)
    SectionCard(
        header,
        testTag = "block_review_section_outcomes",
        help = {
            SectionHelp(
                header,
                stringResource(Res.string.block_review_outcomes_help_cd),
                stringResource(Res.string.block_review_outcomes_help_body),
                "block_review_help_outcomes",
            )
        },
    ) {
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
        leading = {
            IconBadge(
                if (outcome.reached) Res.drawable.ic_check else Res.drawable.ic_flag,
                if (outcome.reached) AppTheme.colors.electricGreen else AppTheme.colors.athleticCyan,
            )
        },
        trailing = {
            StatusChip(stringResource(label.res), tone, Modifier.testTag("block_review_outcome_${index}_chip"))
        },
    )
}

@Composable
internal fun WaistCard(state: BlockReviewUiState.Content) {
    val waist = state.review.waist ?: return
    val line = BlockReviewText.waist(waist)
    val header = stringResource(Res.string.block_review_waist_header)
    SectionCard(
        header,
        testTag = "block_review_section_waist",
        help = {
            SectionHelp(
                header,
                stringResource(Res.string.block_review_waist_help_cd),
                stringResource(Res.string.block_review_waist_help_body),
                "block_review_help_waist",
            )
        },
    ) {
        SummaryLine(stringResource(line.res, *line.args.toTypedArray()), Modifier.testTag("block_review_waist_line"))
    }
}
