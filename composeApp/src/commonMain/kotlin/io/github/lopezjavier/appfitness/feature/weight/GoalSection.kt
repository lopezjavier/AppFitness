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
import io.github.lopezjavier.appfitness.composeapp.resources.goal_card_change_cd
import io.github.lopezjavier.appfitness.composeapp.resources.goal_card_empty_hint
import io.github.lopezjavier.appfitness.composeapp.resources.goal_card_empty_value
import io.github.lopezjavier.appfitness.composeapp.resources.goal_card_set
import io.github.lopezjavier.appfitness.composeapp.resources.goal_card_title
import io.github.lopezjavier.appfitness.composeapp.resources.goal_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.goal_progress_cd
import io.github.lopezjavier.appfitness.composeapp.resources.goal_since_first
import io.github.lopezjavier.appfitness.composeapp.resources.goal_target_value
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.metric_tile_open_label
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppLinearProgress
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.MetricCard
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val SkeletonCardHeight = Spacing.xl * 4

/** Font scale from which the BMI and body-fat cards stack instead of sharing a row. */
private const val STACK_FONT_SCALE = 1.5f
private const val PERCENT = 100

/**
 * The "Objetivo" card (US-27): the target with the distance, the "desde el primero" line and the progress bar, and
 * the pencil that edits it. Without a target it shows the hint and "Fijar objetivo". Loading is a skeleton and a
 * failed load an error with "Reintentar".
 */
@Composable
internal fun GoalCardSection(state: GoalUiState, onEvent: (GoalEvent) -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        GoalUiState.Loading -> SkeletonBox(
            modifier.fillMaxWidth().height(SkeletonCardHeight).testTag("weight_goal_skeleton"),
            AppShapes.lg,
        )

        GoalUiState.Error -> ErrorState(
            message = stringResource(Res.string.goal_error_message),
            action = AppStateAction(stringResource(Res.string.state_retry)) { onEvent(GoalEvent.Retry) },
            modifier = modifier.fillMaxWidth(),
            testTagPrefix = "weight_goal_error",
        )

        is GoalUiState.Content -> GoalCard(state.content, onEvent, modifier)
    }
}

@Composable
private fun GoalCard(content: GoalContent, onEvent: (GoalEvent) -> Unit, modifier: Modifier = Modifier) {
    val target = content.targetKg
    val distance = distanceText(content.distance)
    val sinceFirst = content.sinceFirstKg?.let { stringResource(Res.string.goal_since_first, signedTenths(it)) }
    val progress = content.progress
    val progressText = progress?.let { stringResource(Res.string.goal_progress_cd, (it * PERCENT).roundToInt()) }
    MetricCard(
        label = stringResource(Res.string.goal_card_title),
        value = target?.let { stringResource(Res.string.goal_target_value, decimalText(it)) }
            ?: stringResource(Res.string.goal_card_empty_value),
        modifier = modifier.testTag("weight_goal_card"),
        supporting = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (target == null) {
                    SupportingText(stringResource(Res.string.goal_card_empty_hint), "weight_goal_empty_hint")
                    SecondaryButton(
                        text = stringResource(Res.string.goal_card_set),
                        onClick = { onEvent(GoalEvent.OpenTargetSheet) },
                        modifier = Modifier.testTag("weight_goal_set_button"),
                    )
                } else {
                    distance?.let { SupportingText(it, "weight_goal_distance") }
                    sinceFirst?.let { SupportingText(it, "weight_goal_since_first") }
                }
            }
        },
        trailingAction = if (target != null) {
            {
                AppIconButton(
                    contentDescription = stringResource(Res.string.goal_card_change_cd),
                    icon = Res.drawable.ic_edit,
                    testTag = "weight_goal_change_button",
                    onClick = { onEvent(GoalEvent.OpenTargetSheet) },
                    tint = AppTheme.colors.textSecondary,
                )
            }
        } else {
            null
        },
        progress = if (progress != null && progressText != null) {
            { AppLinearProgress(progress.toFloat(), progressText, Modifier.testTag("weight_goal_progress")) }
        } else {
            null
        },
    )
}

@Composable
private fun SupportingText(text: String, tag: String) {
    Text(
        text = text,
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.testTag(tag),
    )
}

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
