package io.github.lopezjavier.appfitness.feature.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.activity_back_to_today_cd
import io.github.lopezjavier.appfitness.composeapp.resources.activity_card_click
import io.github.lopezjavier.appfitness.composeapp.resources.activity_day_empty
import io.github.lopezjavier.appfitness.composeapp.resources.activity_day_error
import io.github.lopezjavier.appfitness.composeapp.resources.activity_no_data
import io.github.lopezjavier.appfitness.composeapp.resources.date_today
import io.github.lopezjavier.appfitness.designsystem.AppButtonChip
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.MetricCard
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.HealthActivityDay
import io.github.lopezjavier.appfitness.domain.usecase.ActivityMetric
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val STACK_FONT_SCALE = 1.5f
private const val COLUMNS = 2
private val IconSize = Spacing.xl
private val SkeletonCardHeight = Spacing.xl * 3

/** The value text of [metric] on [day], or null when the watch gave none. */
@Composable
private fun valueText(metric: ActivityMetric, day: HealthActivityDay): String? = when (metric) {
    ActivityMetric.Steps -> day.steps?.let { AppNumberFormat.integer(it) }
    ActivityMetric.Sleep -> day.sleep?.let { sleepText(it.minutes) }
    ActivityMetric.ActiveKcal -> day.activeKcal?.let { kcalText(it) }
    ActivityMetric.TotalKcal -> day.totalKcal?.let { kcalText(it) }
    ActivityMetric.RestingHeartRate -> day.restingHeartRate?.let { bpmText(it) }
}

/**
 * The summary of the selected day: its title ("Hoy" or "21 de octubre") with the "back to today" chip when it is not
 * today, the inline day states and one card per metric. A tap on a card selects that metric in the chart.
 */
@Composable
internal fun ActivitySummary(state: ActivityUiState.Content, onEvent: (ActivityEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        DayHeader(state, onEvent)
        when {
            state.dayError -> ErrorState(
                message = stringResource(Res.string.activity_day_error),
                action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(ActivityEvent.Retry) },
                modifier = Modifier.fillMaxWidth(),
                testTagPrefix = "activity_day_error",
                actionTestTag = "activity_day_retry",
            )

            state.dayLoading -> repeat(COLUMNS) {
                SkeletonBox(
                    Modifier.fillMaxWidth().height(SkeletonCardHeight).testTag("activity_day_loading"),
                    AppShapes.lg,
                )
            }

            else -> {
                if (state.summary.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.activity_day_empty),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.testTag("activity_day_empty"),
                    )
                }
                MetricCards(state, onEvent)
            }
        }
    }
}

private fun HealthActivityDay.isEmpty(): Boolean =
    steps == null && activeKcal == null && totalKcal == null && sleep == null && restingHeartRate == null

@Composable
private fun DayHeader(state: ActivityUiState.Content, onEvent: (ActivityEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (state.isToday) stringResource(Res.string.date_today) else AppDateFormat.title(state.day),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).testTag("activity_summary_card").semantics { heading() },
        )
        if (!state.isToday) {
            AppButtonChip(
                label = stringResource(Res.string.date_today),
                onClick = { onEvent(ActivityEvent.GoToToday) },
                contentDescription = stringResource(Res.string.activity_back_to_today_cd),
                modifier = Modifier.testTag("activity_back_to_today"),
            )
        }
    }
}

@Composable
private fun MetricCards(state: ActivityUiState.Content, onEvent: (ActivityEvent) -> Unit) {
    val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
    val card: @Composable (ActivityMetric, Modifier) -> Unit = { metric, modifier ->
        ActivityMetricCard(metric, state, onEvent, modifier)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (stacked) {
            ActivityMetric.entries.forEach { card(it, Modifier) }
        } else {
            ActivityMetric.entries.chunked(COLUMNS).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    pair.forEach { card(it, Modifier.weight(1f)) }
                    if (pair.size < COLUMNS) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActivityMetricCard(
    metric: ActivityMetric,
    state: ActivityUiState.Content,
    onEvent: (ActivityEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(metric.nameRes())
    val value = valueText(metric, state.summary) ?: stringResource(Res.string.activity_no_data)
    val selected = metric == state.chart.metric
    MetricCard(
        label = label,
        value = value,
        modifier = modifier.testTag("activity_${metric.tagName()}_card").semantics { this.selected = selected },
        onClick = { onEvent(ActivityEvent.SelectMetric(metric)) },
        clickLabel = stringResource(Res.string.activity_card_click),
        leading = {
            Icon(
                painter = painterResource(metric.icon()),
                contentDescription = null,
                tint = if (selected) AppTheme.colors.athleticCyan else AppTheme.colors.textSecondary,
                modifier = Modifier.size(IconSize),
            )
        },
        highlighted = selected,
    )
}

@Composable
internal fun ActivitySummarySkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        repeat(COLUMNS * COLUMNS) {
            SkeletonBox(Modifier.fillMaxWidth().height(SkeletonCardHeight), AppShapes.lg)
        }
    }
}
