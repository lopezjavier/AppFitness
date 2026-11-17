package io.github.lopezjavier.appfitness.feature.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.chart_date_short
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_card_cd
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_chart_cd
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_chart_cd_week
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_chart_title
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_current_value
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_last_week
import io.github.lopezjavier.appfitness.composeapp.resources.px_stats_week_delta
import io.github.lopezjavier.appfitness.designsystem.AppBarChart
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppLineChart
import io.github.lopezjavier.appfitness.designsystem.AppLineChartBlock
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TrendDirection
import io.github.lopezjavier.appfitness.designsystem.TrendPill
import io.github.lopezjavier.appfitness.designsystem.TrendPillDefaults
import io.github.lopezjavier.appfitness.designsystem.TrendTone
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleVolumeHistory
import io.github.lopezjavier.appfitness.feature.exercises.label
import io.github.lopezjavier.appfitness.feature.exercises.tagName
import io.github.lopezjavier.appfitness.feature.workout.shortLabel
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val COMPACT_COLUMNS = 4
private const val WIDE_COLUMNS = 8
private val OverviewChartHeight = Spacing.lg * 4

@Composable
private fun LocalDate.weekLabel(): String = stringResource(Res.string.chart_date_short, day, month.shortLabel())

/** One card per group with its current-week volume and the 8 weekly values; the whole card selects the group. */
@Composable
internal fun GroupOverviewCard(
    group: MuscleGroup,
    history: MuscleVolumeHistory,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = history.series.getValue(group)
    val name = stringResource(group.label)
    val current = formatVolume(values.last())
    val description = stringResource(Res.string.muscle_volume_card_cd, name, current)
    AppCard(
        modifier
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onSelect)
            .semantics { contentDescription = description }
            .testTag("muscle_volume_card_${group.tagName()}"),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Res.drawable.ic_fitness_center, AppTheme.colors.athleticCyan)
            Column(Modifier.weight(1f)) {
                Text(name, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
                Text(
                    text = stringResource(Res.string.muscle_volume_last_week),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(Res.string.muscle_volume_current_value, current),
                    style = AppTheme.typography.headlineMetric,
                    color = AppTheme.colors.athleticCyan,
                    modifier = Modifier.testTag("muscle_volume_current_${group.tagName()}"),
                )
                WeekTrend(values)
            }
        }
        val labels = history.weeks.map { it.weekLabel() }
        val perWeek = labels.zip(values).map { (label, value) ->
            stringResource(Res.string.muscle_volume_chart_cd_week, label, formatVolume(value))
        }.joinToString("; ")
        AppBarChart(
            values = values.map { it.roundToInt() },
            labels = labels,
            description = stringResource(Res.string.muscle_volume_chart_cd, name, perWeek),
            modifier = Modifier.fillMaxWidth().height(OverviewChartHeight).padding(top = Spacing.md)
                .testTag("muscle_volume_overview_chart_${group.tagName()}"),
        )
    }
}

/** The change of the latest week against the one before it; flat or lower stays neutral, never red. */
@Composable
private fun WeekTrend(values: List<Double>) {
    if (values.size < 2) return
    val delta = values.last() - values[values.size - 2]
    val direction = TrendPillDefaults.direction(delta)
    val rounded = delta.roundToInt()
    val text = stringResource(
        Res.string.px_stats_week_delta,
        (if (rounded > 0) "+" else "") + AppNumberFormat.integer(rounded),
    )
    val tone = if (direction == TrendDirection.Up) TrendTone.Favorable else TrendTone.Neutral
    TrendPill(text, tone, Modifier, direction, showToneIcon = false)
}

/** The chart of one group over the 8 weeks with its values underneath. */
@Composable
internal fun GroupChartCard(group: MuscleGroup, history: MuscleVolumeHistory, modifier: Modifier = Modifier) {
    val values = history.series.getValue(group)
    val name = stringResource(group.label)
    val labels = history.weeks.map { it.weekLabel() }
    val perWeek = labels.zip(values).map { (label, value) ->
        stringResource(Res.string.muscle_volume_chart_cd_week, label, formatVolume(value))
    }.joinToString("; ")
    AppCard(modifier) {
        AppLineChartBlock(stringResource(Res.string.muscle_volume_chart_title, name)) {
            AppLineChart(
                values = values,
                labels = labels,
                description = stringResource(Res.string.muscle_volume_chart_cd, name, perWeek),
                valueLabel = { formatVolume(it) },
                modifier = Modifier.fillMaxWidth().testTag("muscle_volume_chart"),
            )
        }
        WeekGrid(group, history, Modifier.fillMaxWidth().padding(top = Spacing.md))
    }
}

/** Week label over value, 4 columns by 2 rows on compact widths and 8 by 1 on wider ones; zero weeks are dimmed. */
@Composable
private fun WeekGrid(group: MuscleGroup, history: MuscleVolumeHistory, modifier: Modifier = Modifier) {
    val values = history.series.getValue(group)
    BoxWithConstraints(modifier) {
        val columns = if (windowWidthClassFor(maxWidth) == WindowWidthClass.Compact) COMPACT_COLUMNS else WIDE_COLUMNS
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            history.weeks.indices.chunked(columns).forEach { rowIndices ->
                Row(Modifier.fillMaxWidth()) {
                    rowIndices.forEach { index ->
                        WeekCell(group, index, history.weeks[index], values[index], Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCell(group: MuscleGroup, index: Int, week: LocalDate, value: Double, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(week.weekLabel(), style = AppTheme.typography.labelTechnical, color = AppTheme.colors.textSecondary)
        Text(
            text = formatVolume(value),
            style = AppTheme.typography.bodyMedium,
            color = if (value > 0.0) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("muscle_volume_week_${group.tagName()}_$index"),
        )
    }
}
