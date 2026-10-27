package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.chart_date_short
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.weekly_rate_no_data
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_detail_action
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_detail_close
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_detail_ma7
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_detail_ma7_none
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_detail_value
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_empty
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_empty_metric
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_legend
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_summary
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_summary_goal
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_summary_target
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_target_legend
import io.github.lopezjavier.appfitness.composeapp.resources.weight_trend_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.weight_trend_pill
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppLineChart
import io.github.lopezjavier.appfitness.designsystem.AppLineChartDefaults
import io.github.lopezjavier.appfitness.designsystem.AppLineChartSkeleton
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TimeRange
import io.github.lopezjavier.appfitness.designsystem.TimeRangeSelector
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.usecase.TrendPoint
import io.github.lopezjavier.appfitness.feature.workout.shortLabel
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private val LegendSwatchSize = Spacing.sm

private val ChartRanges = listOf(
    TimeRange.Week,
    TimeRange.Month,
    TimeRange.ThreeMonths,
    TimeRange.SixMonths,
    TimeRange.Year,
)

/** Tolerance when matching the first plotted weight with the onboarding weight (both have one decimal). */
private const val START_MATCH_KG = 0.05

/**
 * The trend card (US-27): the metric name as a title button that opens the metric picker, the "?" help of "Media 7
 * días", the range selector, the chart of the daily dots with the MA7 line, its legend, the goal overlay (dashed
 * target line, band to the starting weight) and the detail of the selected point. Loading shows a skeleton and an
 * error a retry; with too little data the chart is replaced by a hint but the title and ranges stay.
 */
@Composable
fun WeightChart(state: WeightTrendUiState, onEvent: (WeightTrendEvent) -> Unit, modifier: Modifier = Modifier) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    AppCard(modifier.fillMaxWidth().testTag("weight_chart_card")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ChartTitleRow(
                metric = (state as? WeightTrendUiState.Content)?.metric ?: BodyMetric.Weight,
                canChange = state is WeightTrendUiState.Content,
                onOpenPicker = { pickerOpen = true },
            )
            when (state) {
                WeightTrendUiState.Loading -> AppLineChartSkeleton(Modifier.testTag("weight_chart_loading"))

                WeightTrendUiState.Error -> ErrorState(
                    message = stringResource(Res.string.weight_trend_error_message),
                    action = AppStateAction(stringResource(Res.string.action_retry)) {
                        onEvent(WeightTrendEvent.Retry)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTagPrefix = "weight_trend_error",
                )

                is WeightTrendUiState.Content -> ChartContent(state, onEvent)
            }
        }
    }
    if (pickerOpen && state is WeightTrendUiState.Content) {
        MetricPickerSheet(
            selected = state.metric,
            onSelect = {
                onEvent(WeightTrendEvent.SelectMetric(it))
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun ChartContent(state: WeightTrendUiState.Content, onEvent: (WeightTrendEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        TimeRangeSelector(
            selected = state.range.toTimeRange(),
            onSelect = { onEvent(WeightTrendEvent.SelectRange(it.toWeightChartRange())) },
            ranges = ChartRanges,
            tagPrefix = "weight_chart_range",
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.hasPlot) {
            TrendPlot(state, onEvent)
        } else {
            Text(
                text = if (state.metric == BodyMetric.Weight) {
                    stringResource(Res.string.weight_chart_empty)
                } else {
                    stringResource(Res.string.weight_chart_empty_metric, metricName(state.metric))
                },
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.fillMaxWidth().testTag("weight_chart_empty"),
            )
        }
    }
}

@Composable
private fun TrendPlot(state: WeightTrendUiState.Content, onEvent: (WeightTrendEvent) -> Unit) {
    val trend = state.trend
    val series = remember(trend.points) { chartSeries(trend.points) }
    val selected = state.selectedPoint
    val labels = List(series.dots.size) { shortDate(series.dateAt(it)) }
    val overlay = remember(state.targetKg, state.startKg, series) { goalOverlay(state.targetKg, state.startKg, series) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        AppLineChart(
            values = series.ma7,
            labels = labels,
            description = chartSummary(state),
            modifier = Modifier.fillMaxWidth().testTag("weight_chart"),
            valueLabel = { metricAxisText(it, state.metric) },
            dots = series.dots,
            lineThickness = AppLineChartDefaults.MaLineThickness,
            onPointLongPress = { index -> onEvent(WeightTrendEvent.SelectPoint(series.pointAt(index)?.date)) },
            onPointTap = { index ->
                val date = series.pointAt(index)?.date
                onEvent(WeightTrendEvent.SelectPoint(if (date == selected?.date) null else date))
            },
            detailActionLabel = stringResource(Res.string.weight_chart_detail_action),
            animated = LocalAnimationsEnabled.current,
            referenceLine = state.targetKg,
            connectGaps = true,
            markers = overlay.markers,
            band = overlay.band,
        )
        Legend(state.targetKg != null)
        selected?.let { PointDetail(it, state.metric) { onEvent(WeightTrendEvent.SelectPoint(null)) } }
    }
}

@Composable
private fun Legend(hasTarget: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendEntry("weight_chart_legend", stringResource(Res.string.weight_chart_legend)) {
            Box(
                Modifier
                    .size(LegendSwatchSize)
                    .clip(CircleShape)
                    .background(AppTheme.colors.athleticCyan),
            )
        }
        if (hasTarget) {
            LegendEntry("weight_chart_target_legend", stringResource(Res.string.weight_chart_target_legend)) {
                DashedSwatch()
            }
        }
    }
}

@Composable
private fun LegendEntry(tag: String, text: String, swatch: @Composable () -> Unit) {
    Row(
        Modifier.semantics(mergeDescendants = true) {}.testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clearAndSetSemantics {}) { swatch() }
        Text(text = text, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
    }
}

/** Three short dashes: the target line is told apart by its shape, not only by its colour. */
@Composable
private fun DashedSwatch() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        repeat(DASH_COUNT) {
            Box(Modifier.size(width = Spacing.sm, height = Spacing.xs / 2).background(AppTheme.colors.textSecondary))
        }
    }
}

private const val DASH_COUNT = 3

@Composable
private fun PointDetail(point: TrendPoint, metric: BodyMetric, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag("weight_chart_point_detail"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = shortDate(point.date),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(
                    Res.string.weight_chart_detail_value,
                    metricName(metric),
                    metricValueText(point.dailyKg, metric),
                ),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text =
                point.ma7Kg?.let { stringResource(Res.string.weight_chart_detail_ma7, metricValueText(it, metric)) }
                    ?: stringResource(Res.string.weight_chart_detail_ma7_none),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.athleticCyan,
            )
        }
        AppIconButton(
            contentDescription = stringResource(Res.string.weight_chart_detail_close),
            icon = Res.drawable.ic_close,
            testTag = "weight_chart_point_detail_close",
            onClick = onClose,
        )
    }
}

@Composable
private fun shortDate(date: LocalDate): String =
    stringResource(Res.string.chart_date_short, date.day, date.month.shortLabel())

/** The goal overlay of the weight chart: a marker on the first point when it is the starting weight, and the band. */
private class GoalOverlay(val markers: List<Int>, val band: ClosedFloatingPointRange<Double>?)

private fun goalOverlay(targetKg: Double?, startKg: Double?, series: ChartSeries): GoalOverlay {
    if (targetKg == null || startKg == null) return GoalOverlay(emptyList(), null)
    val first = series.dots.indexOfFirst { it != null }
    val startsAtFirstWeight = first >= 0 && abs((series.dots[first] ?: 0.0) - startKg) < START_MATCH_KG
    return GoalOverlay(
        markers = if (startsAtFirstWeight) listOf(first) else emptyList(),
        band = minOf(startKg, targetKg)..maxOf(startKg, targetKg),
    )
}

@Composable
private fun chartSummary(state: WeightTrendUiState.Content): String {
    val trend = state.trend
    val metric = state.metric
    val base = stringResource(
        Res.string.weight_chart_summary,
        metricName(metric),
        stringResource(state.range.toTimeRange().spoken),
        trend.points.size.toString(),
        metricValueText(trend.points.first().dailyKg, metric),
        metricValueText(trend.points.last().dailyKg, metric),
    )
    // The pace sentence and the goal only describe the weight.
    val rate = if (metric == BodyMetric.Weight) {
        state.weeklyRate.value?.let { stringResource(Res.string.weight_trend_pill, rateText(it)) }
            ?: stringResource(Res.string.weekly_rate_no_data)
    } else {
        null
    }
    val withRate = rate?.let { "$base $it" } ?: base
    val target = state.targetKg ?: return withRate
    val start = state.startKg
    val goal = if (start != null) {
        stringResource(Res.string.weight_chart_summary_goal, decimalText(target), decimalText(start))
    } else {
        stringResource(Res.string.weight_chart_summary_target, decimalText(target))
    }
    return "$withRate $goal"
}
