package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.goal_card_change_cd
import io.github.lopezjavier.appfitness.composeapp.resources.goal_card_empty_hint
import io.github.lopezjavier.appfitness.composeapp.resources.goal_card_set
import io.github.lopezjavier.appfitness.composeapp.resources.goal_card_title
import io.github.lopezjavier.appfitness.composeapp.resources.goal_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.goal_since_first
import io.github.lopezjavier.appfitness.composeapp.resources.goal_target_value
import io.github.lopezjavier.appfitness.composeapp.resources.metric_tile_open_label
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.GoalCard
import io.github.lopezjavier.appfitness.designsystem.MetricTile
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.domain.usecase.MetricInterpretation
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind
import org.jetbrains.compose.resources.stringResource

private val SkeletonCardHeight = Spacing.xl * 4

/**
 * The goal card and the BMI / body-fat tiles under the weight panel (US-27). Loading is a skeleton, a failed load an
 * error with "Reintentar"; the card's empty state offers "Fijar objetivo"; the BMI tile hides without a height.
 */
@Composable
internal fun GoalSection(state: GoalUiState, onEvent: (GoalEvent) -> Unit, modifier: Modifier = Modifier) {
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

        is GoalUiState.Content -> Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            GoalCardContent(state.content, onEvent)
            MetricTiles(state.content, onEvent)
        }
    }
}

@Composable
private fun GoalCardContent(content: GoalContent, onEvent: (GoalEvent) -> Unit) {
    val distance = distanceText(content.distance)
    GoalCard(
        title = stringResource(Res.string.goal_card_title),
        targetText = content.targetKg?.let { stringResource(Res.string.goal_target_value, decimalText(it)) },
        distanceText = distance,
        sinceFirstText = content.sinceFirstKg?.let { stringResource(Res.string.goal_since_first, signedTenths(it)) },
        emptyHint = stringResource(Res.string.goal_card_empty_hint),
        setLabel = stringResource(Res.string.goal_card_set),
        changeLabel = stringResource(Res.string.goal_card_change_cd),
        onSet = { onEvent(GoalEvent.OpenTargetSheet) },
        onChange = { onEvent(GoalEvent.OpenTargetSheet) },
        testTagPrefix = "weight_goal",
    )
}

/** BMI and body fat share one row; each hides without its data. */
@Composable
private fun MetricTiles(content: GoalContent, onEvent: (GoalEvent) -> Unit) {
    val bmi = content.bmi
    val fat = content.bodyFat
    if (bmi == null && fat == null) return
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        bmi?.let {
            InterpretedTile(it, "weight_bmi_tile", onEvent, Modifier.weight(1f))
        }
        fat?.let {
            InterpretedTile(it, "weight_body_fat_tile", onEvent, Modifier.weight(1f))
        }
    }
}

/** A tappable tile: label, value and the status chip; tapping opens the metric detail. */
@Composable
private fun InterpretedTile(
    metric: MetricInterpretation,
    tag: String,
    onEvent: (GoalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(Res.string.metric_tile_open_label)
    val kind: MetricKind = metric.metric
    MetricTile(
        label = stringResource(metricLabelRes(kind)),
        value = metricValueText(metric),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .testTag(tag)
            .clickable(onClickLabel = openLabel, role = Role.Button) { onEvent(GoalEvent.OpenMetric(kind)) },
        supporting = {
            StatusChip(
                stringResource(statusTextRes(metric.status)),
                metric.status.tone.toStatusTone(),
                mergeSemantics = false,
            )
        },
    )
}
