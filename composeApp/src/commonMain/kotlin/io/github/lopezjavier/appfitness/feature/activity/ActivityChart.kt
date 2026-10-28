package io.github.lopezjavier.appfitness.feature.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.activity_chart_empty
import io.github.lopezjavier.appfitness.composeapp.resources.activity_chart_hours_unit
import io.github.lopezjavier.appfitness.composeapp.resources.activity_chart_summary
import io.github.lopezjavier.appfitness.composeapp.resources.chart_date_short
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_picker_title
import io.github.lopezjavier.appfitness.composeapp.resources.weight_chart_metric_title_cd
import io.github.lopezjavier.appfitness.designsystem.AppBarChart
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppLineChart
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ChartMetricPickerSheet
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TimeRange
import io.github.lopezjavier.appfitness.designsystem.TimeRangeSelector
import io.github.lopezjavier.appfitness.domain.usecase.ActivityMetric
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import io.github.lopezjavier.appfitness.feature.weight.toTimeRange
import io.github.lopezjavier.appfitness.feature.weight.toWeightChartRange
import io.github.lopezjavier.appfitness.feature.workout.shortLabel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val MINUTES_PER_HOUR = 60.0

private val ChartRanges = listOf(
    TimeRange.Week,
    TimeRange.Month,
    TimeRange.ThreeMonths,
    TimeRange.SixMonths,
    TimeRange.Year,
)

/**
 * The chart card of "Actividad": the metric name as a title button that opens the metric picker (same control as
 * Peso), the range selector and the chart of the selected metric (bars for steps and calories, a line for sleep and
 * resting heart rate). A metric with no points in the range says so instead of drawing. Sleep adds the stage
 * distribution of the selected day.
 */
@Composable
internal fun ActivityChartCard(
    state: ActivityUiState.Content,
    onEvent: (ActivityEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val chart = state.chart
    AppCard(modifier.fillMaxWidth().testTag("activity_chart_card")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            MetricTrigger(chart.metric) { pickerOpen = true }
            TimeRangeSelector(
                selected = chart.range.toTimeRange(),
                onSelect = { onEvent(ActivityEvent.SelectRange(it.toWeightChartRange())) },
                ranges = ChartRanges,
                tagPrefix = "activity_chart_range",
                modifier = Modifier.fillMaxWidth(),
            )
            if (chart.points.isEmpty()) {
                Text(
                    text = stringResource(
                        Res.string.activity_chart_empty,
                        stringResource(chart.metric.nameRes()).lowercase(),
                    ),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.fillMaxWidth().testTag("activity_chart_empty"),
                )
            } else {
                ActivityPlot(state)
            }
            if (chart.metric == ActivityMetric.Sleep) SleepDetail(state)
        }
    }
    if (pickerOpen) {
        ChartMetricPickerSheet(
            title = stringResource(Res.string.weight_chart_metric_picker_title),
            options = activityMetricOptions(),
            selected = chart.metric,
            onSelect = {
                onEvent(ActivityEvent.SelectMetric(it))
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
            tagPrefix = "activity_chart_metric",
        )
    }
}

@Composable
private fun MetricTrigger(metric: ActivityMetric, onClick: () -> Unit) {
    val name = stringResource(metric.nameRes())
    val description = stringResource(Res.string.weight_chart_metric_title_cd, name)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag("activity_chart_metric_trigger"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = name, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
        Icon(
            painter = painterResource(Res.drawable.ic_expand_more),
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(Spacing.xl),
        )
    }
}

/** Every day of the range window in order: the chart has one slot per day, a day without data is a gap. */
private fun windowDays(range: WeightChartRange, today: LocalDate): List<LocalDate> {
    val window = range.window(today)
    return generateSequence(window.visibleStart) { it.plus(1, DateTimeUnit.DAY) }
        .takeWhile { it < window.endExclusive }
        .toList()
}

@Composable
private fun ActivityPlot(state: ActivityUiState.Content) {
    val chart = state.chart
    val days = remember(chart.range, state.today) { windowDays(chart.range, state.today) }
    val byDay = remember(chart.points) { chart.points.associate { it.day to it.value } }
    val labels = days.map { stringResource(Res.string.chart_date_short, it.day, it.month.shortLabel()) }
    val description = stringResource(
        Res.string.activity_chart_summary,
        stringResource(chart.metric.nameRes()),
        stringResource(chart.range.toTimeRange().spoken),
        chart.points.size,
    )
    val hoursUnit = stringResource(Res.string.activity_chart_hours_unit)
    val modifier = Modifier.fillMaxWidth().testTag("activity_chart")
    when (chart.metric) {
        ActivityMetric.Steps, ActivityMetric.ActiveKcal, ActivityMetric.TotalKcal -> AppBarChart(
            values = days.map { (byDay[it] ?: 0.0).roundToInt() },
            labels = labels,
            description = description,
            modifier = modifier,
            valueLabel = { AppNumberFormat.integer(it) },
        )

        ActivityMetric.Sleep -> AppLineChart(
            values = days.map { byDay[it]?.div(MINUTES_PER_HOUR) },
            labels = labels,
            description = description,
            modifier = modifier,
            valueLabel = { "${AppNumberFormat.decimal(it)} $hoursUnit" },
            dots = days.map { byDay[it]?.div(MINUTES_PER_HOUR) },
            animated = LocalAnimationsEnabled.current,
            connectGaps = true,
        )

        ActivityMetric.RestingHeartRate -> AppLineChart(
            values = days.map { byDay[it] },
            labels = labels,
            description = description,
            modifier = modifier,
            valueLabel = { AppNumberFormat.integer(it.roundToInt()) },
            dots = days.map { byDay[it] },
            animated = LocalAnimationsEnabled.current,
            connectGaps = true,
        )
    }
}
