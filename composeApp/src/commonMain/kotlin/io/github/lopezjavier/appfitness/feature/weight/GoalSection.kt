package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.metric_tile_open_label
import io.github.lopezjavier.appfitness.composeapp.resources.plan_outcome_deadline
import io.github.lopezjavier.appfitness.composeapp.resources.plan_outcome_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.plan_outcome_help_title
import io.github.lopezjavier.appfitness.composeapp.resources.weight_plan_outcome_title
import io.github.lopezjavier.appfitness.composeapp.resources.weight_plan_outcome_value
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.MetricCard
import io.github.lopezjavier.appfitness.designsystem.MetricTileRow
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.domain.model.PlanOutcome
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import org.jetbrains.compose.resources.stringResource

/**
 * BMI and body fat share one row of equal cards ([MetricTileRow]: stacked from font scale 1.3, as the circumference
 * grid under them, C-160); each hides without its data.
 */
@Composable
internal fun MetricCardsSection(state: GoalUiState, onEvent: (GoalEvent) -> Unit, modifier: Modifier = Modifier) {
    val content = (state as? GoalUiState.Content)?.content ?: return
    val cards: List<@Composable (Modifier) -> Unit> = buildList {
        content.bmi?.let { bmi -> add { cell -> InterpretedCard(bmi, "weight_bmi_tile", onEvent, cell, content.sex) } }
        content.bodyFat?.let { fat ->
            add { cell -> InterpretedCard(fat, "weight_body_fat_tile", onEvent, cell, content.sex) }
        }
    }
    if (cards.isEmpty()) return
    MetricTileRow(cards, modifier, spacing = Spacing.cardGap)
}

/** A tappable card: label, value and the status chip; tapping opens the metric detail. */
@Composable
private fun InterpretedCard(
    metric: MetricInterpretation,
    tag: String,
    onEvent: (GoalEvent) -> Unit,
    modifier: Modifier = Modifier,
    sex: Sex? = null,
) {
    MetricCard(
        label = stringResource(metricLabelRes(metric.metric)),
        value = metricValueText(metric),
        modifier = modifier.testTag(tag),
        supporting = { MetricStatusGauge(metric, sex) },
        onClick = { onEvent(GoalEvent.OpenMetric(metric.metric)) },
        clickLabel = stringResource(Res.string.metric_tile_open_label),
    )
}

/**
 * The status chip over the gauge of [metric]: the zone in words and in place, so the gauge colour is never the only
 * cue (R-41). The supporting content of the BMI, body-fat and waist-to-hip cards.
 */
@Composable
internal fun MetricStatusGauge(metric: MetricInterpretation, sex: Sex?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatusChip(
            stringResource(statusTextRes(metric.status)),
            metric.status.tone.toStatusTone(),
            mergeSemantics = false,
        )
        MetricGauge(metric, sex)
    }
}

/**
 * "Objetivo del plan" (ADR-78): the accepted plan's weight outcome, read-only. It is the only place the target weight
 * shows; the user never enters it. The reason sits behind the "?" (R-46).
 */
@Composable
internal fun PlanOutcomeCard(outcome: PlanOutcome, modifier: Modifier = Modifier) {
    MetricCard(
        label = stringResource(Res.string.weight_plan_outcome_title),
        value = stringResource(Res.string.weight_plan_outcome_value, decimalText(outcome.targetValue)),
        modifier = modifier.testTag("weight_plan_outcome"),
        supporting = {
            Text(
                text = stringResource(Res.string.plan_outcome_deadline, AppDateFormat.title(outcome.deadline)),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        },
        trailingAction = {
            HelpButton(
                contentDescription = stringResource(Res.string.plan_outcome_help_cd),
                title = stringResource(Res.string.plan_outcome_help_title),
                body = outcome.reason,
                testTag = "weight_plan_outcome_help",
            )
        },
    )
}
