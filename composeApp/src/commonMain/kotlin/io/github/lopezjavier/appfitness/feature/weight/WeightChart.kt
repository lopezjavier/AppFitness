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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.chart_date_short
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.weekly_rate_no_data
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_detail_action
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_detail_close
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_detail_ma7
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_detail_ma7_none
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_detail_weight
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_empty
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_filter_1a
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_filter_1a_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_filter_1m
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_filter_1m_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_filter_1s
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_filter_1s_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_filter_3m
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_filter_3m_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_filter_6m
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_filter_6m_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_legend
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_summary
import io.github.lopezjavier.appfitness.composeapp.resources.weight_trend_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.weight_trend_pill
import io.github.lopezjavier.appfitness.composeapp.resources.weight_trend_title
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppLineChart
import io.github.lopezjavier.appfitness.designsystem.AppLineChartDefaults
import io.github.lopezjavier.appfitness.designsystem.AppLineChartSkeleton
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.usecase.TrendData
import io.github.lopezjavier.appfitness.domain.usecase.TrendPoint
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import io.github.lopezjavier.appfitness.feature.workout.shortLabel
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val LegendSwatchSize = Spacing.sm

private class RangeFilter(
    val range: WeightChartRange,
    val tag: String,
    val label: StringResource,
    val description: StringResource,
)

private val RangeFilters = listOf(
    RangeFilter(
        WeightChartRange.Week,
        "weight_chart_filter_1s",
        Res.string.weight_chart_filter_1s,
        Res.string.weight_chart_filter_1s_cd,
    ),
    RangeFilter(
        WeightChartRange.Month,
        "weight_chart_filter_1m",
        Res.string.weight_chart_filter_1m,
        Res.string.weight_chart_filter_1m_cd,
    ),
    RangeFilter(
        WeightChartRange.Quarter,
        "weight_chart_filter_3m",
        Res.string.weight_chart_filter_3m,
        Res.string.weight_chart_filter_3m_cd,
    ),
    RangeFilter(
        WeightChartRange.HalfYear,
        "weight_chart_filter_6m",
        Res.string.weight_chart_filter_6m,
        Res.string.weight_chart_filter_6m_cd,
    ),
    RangeFilter(
        WeightChartRange.Year,
        "weight_chart_filter_1a",
        Res.string.weight_chart_filter_1a,
        Res.string.weight_chart_filter_1a_cd,
    ),
)

/**
 * The weight trend card (US-27): range chips, the chart of the daily dots with the MA7 line, the "MA 7D" legend and
 * the detail of the selected point. Loading shows a skeleton and an error a retry; with too little data the chart
 * is replaced by a hint but the chips stay.
 */
@Composable
fun WeightChart(state: WeightTrendUiState, onEvent: (WeightTrendEvent) -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth().testTag("weight_chart_card")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = stringResource(Res.string.weight_trend_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            when (state) {
                WeightTrendUiState.Loading -> AppLineChartSkeleton(Modifier.testTag("weight_chart_loading"))

                WeightTrendUiState.Error -> ErrorState(
                    message = stringResource(Res.string.weight_trend_error_message),
                    action = AppStateAction(stringResource(Res.string.state_retry)) {
                        onEvent(WeightTrendEvent.Retry)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTagPrefix = "weight_trend_error",
                )

                is WeightTrendUiState.Content -> {
                    RangeChips(state.range) { onEvent(WeightTrendEvent.SelectRange(it)) }
                    if (state.trend.hasEnoughData) {
                        TrendPlot(state, onEvent)
                    } else {
                        Text(
                            text = stringResource(Res.string.weight_chart_empty),
                            style = AppTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.fillMaxWidth().testTag("weight_chart_empty"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeChips(selected: WeightChartRange, onSelect: (WeightChartRange) -> Unit) {
    val options = RangeFilters.map {
        AppOption(stringResource(it.label), it.tag, stringResource(it.description))
    }
    FilterChipRow(
        options = options,
        selectedIndex = RangeFilters.indexOfFirst { it.range == selected },
        onSelect = { onSelect(RangeFilters[it].range) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TrendPlot(state: WeightTrendUiState.Content, onEvent: (WeightTrendEvent) -> Unit) {
    val trend = state.trend
    val series = remember(trend.points) { chartSeries(trend.points) }
    val selected = state.selectedPoint
    val labels = List(series.dots.size) { shortDate(series.dateAt(it)) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        AppLineChart(
            values = series.ma7,
            labels = labels,
            description = chartSummary(state.range, trend),
            modifier = Modifier.fillMaxWidth().testTag("weight_chart"),
            valueLabel = { decimalText(it) },
            dots = series.dots,
            lineThickness = AppLineChartDefaults.MaLineThickness,
            onPointLongPress = { index -> onEvent(WeightTrendEvent.SelectPoint(series.pointAt(index)?.date)) },
            onPointTap = { index ->
                val date = series.pointAt(index)?.date
                onEvent(WeightTrendEvent.SelectPoint(if (date == selected?.date) null else date))
            },
            detailActionLabel = stringResource(Res.string.weight_chart_detail_action),
            animated = LocalAnimationsEnabled.current,
        )
        Legend()
        selected?.let { PointDetail(it) { onEvent(WeightTrendEvent.SelectPoint(null)) } }
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.semantics(mergeDescendants = true) {}.testTag("weight_chart_legend"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(LegendSwatchSize)
                .clip(CircleShape)
                .background(AppTheme.colors.athleticCyan)
                .clearAndSetSemantics {},
        )
        Text(
            text = stringResource(Res.string.weight_chart_legend),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun PointDetail(point: TrendPoint, onClose: () -> Unit) {
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
                text = stringResource(Res.string.weight_chart_detail_weight, decimalText(point.dailyKg)),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text = point.ma7Kg?.let { stringResource(Res.string.weight_chart_detail_ma7, decimalText(it)) }
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

@Composable
private fun chartSummary(range: WeightChartRange, trend: TrendData): String {
    val rangeText = stringResource(RangeFilters.first { it.range == range }.description)
    val rate = trend.rate.value?.let { stringResource(Res.string.weight_trend_pill, rateText(it)) }
        ?: stringResource(Res.string.weekly_rate_no_data)
    return stringResource(
        Res.string.weight_chart_summary,
        rangeText,
        trend.points.size.toString(),
        decimalText(trend.points.first().dailyKg),
        decimalText(trend.points.last().dailyKg),
        rate,
    )
}
