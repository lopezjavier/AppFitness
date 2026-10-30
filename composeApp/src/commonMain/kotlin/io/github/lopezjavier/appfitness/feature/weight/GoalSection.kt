package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.domain.model.PlanOutcome
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import org.jetbrains.compose.resources.stringResource

/** Font scale from which the BMI and body-fat cards stack instead of sharing a row. */
private const val STACK_FONT_SCALE = 1.5f

/** BMI and body fat share one row (stacked at large font scales); each hides without its data. */
@Composable
internal fun MetricCardsSection(state: GoalUiState, onEvent: (GoalEvent) -> Unit, modifier: Modifier = Modifier) {
    val content = (state as? GoalUiState.Content)?.content ?: return
    val bmi = content.bmi
    val fat = content.bodyFat
    if (bmi == null && fat == null) return
    if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            bmi?.let { InterpretedCard(it, "weight_bmi_tile", onEvent) }
            fat?.let { InterpretedCard(it, "weight_body_fat_tile", onEvent) }
        }
    } else {
        Row(
            modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            bmi?.let { InterpretedCard(it, "weight_bmi_tile", onEvent, Modifier.weight(1f)) }
            fat?.let { InterpretedCard(it, "weight_body_fat_tile", onEvent, Modifier.weight(1f)) }
        }
    }
}

/** A tappable card: label, value and the status chip; tapping opens the metric detail. */
@Composable
private fun InterpretedCard(
    metric: MetricInterpretation,
    tag: String,
    onEvent: (GoalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    MetricCard(
        label = stringResource(metricLabelRes(metric.metric)),
        value = metricValueText(metric),
        modifier = modifier.testTag(tag),
        supporting = {
            StatusChip(
                stringResource(statusTextRes(metric.status)),
                metric.status.tone.toStatusTone(),
                mergeSemantics = false,
            )
        },
        onClick = { onEvent(GoalEvent.OpenMetric(metric.metric)) },
        clickLabel = stringResource(Res.string.metric_tile_open_label),
    )
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
