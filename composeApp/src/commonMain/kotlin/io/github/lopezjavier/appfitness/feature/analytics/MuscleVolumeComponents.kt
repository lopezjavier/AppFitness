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
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_card_cd
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_chart_cd
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_chart_cd_week
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_chart_title
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_last_week
import io.github.lopezjavier.appfitness.designsystem.AppBarChart
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppLineChartBlock
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleSetsHistory
import io.github.lopezjavier.appfitness.feature.analytics.stats.formatSetsNumber
import io.github.lopezjavier.appfitness.feature.analytics.stats.setsText
import io.github.lopezjavier.appfitness.feature.exercises.label
import io.github.lopezjavier.appfitness.feature.exercises.tagName
import io.github.lopezjavier.appfitness.feature.workout.shortLabel
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val COMPACT_COLUMNS = 4
private const val WIDE_COLUMNS = 8
private const val HALF_SETS_PER_SET = 2.0
private val OverviewChartHeight = Spacing.lg * 4

@Composable
private fun LocalDate.weekLabel(): String = stringResource(Res.string.chart_date_short, day, month.shortLabel())

/**
 * One card per group: the name on its own row (it wraps by word, never cut), then "Esta semana" with the current-week
 * sets, and the 8 weekly bars; the whole card selects the group.
 */
@Composable
internal fun GroupOverviewCard(
    group: MuscleGroup,
    history: MuscleSetsHistory,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = history.series.getValue(group)
    val name = stringResource(group.label)
    val current = setsText(values.last())
    val description = stringResource(Res.string.muscle_volume_card_cd, name, current)
    AppCard(
        modifier
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onSelect)
            .semantics { contentDescription = description }
            .testTag("muscle_sets_group_${group.tagName()}"),
    ) {
        Text(
            text = name,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.muscle_volume_last_week),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = current,
                style = AppTheme.typography.headlineMetric,
                color = AppTheme.colors.athleticCyan,
                modifier = Modifier.testTag("muscle_sets_current_${group.tagName()}"),
            )
        }
        val labels = history.weeks.map { it.weekLabel() }
        AppBarChart(
            values = values.map { it.roundToInt() },
            highlightIndex = values.lastIndex,
            labels = labels,
            description = stringResource(Res.string.muscle_volume_chart_cd, name, weeklyDescription(labels, values)),
            modifier = Modifier.fillMaxWidth().height(OverviewChartHeight).padding(top = Spacing.md)
                .testTag("muscle_sets_overview_chart_${group.tagName()}"),
        )
    }
}

@Composable
private fun weeklyDescription(labels: List<String>, values: List<Double>): String =
    labels.zip(values).map { (label, value) ->
        stringResource(Res.string.muscle_volume_chart_cd_week, label, formatSetsNumber(value))
    }.joinToString("; ")

/** The chart of one group over the 8 weeks (zero baseline, current week lit) with its values underneath. */
@Composable
internal fun GroupChartCard(group: MuscleGroup, history: MuscleSetsHistory, modifier: Modifier = Modifier) {
    val values = history.series.getValue(group)
    val name = stringResource(group.label)
    val labels = history.weeks.map { it.weekLabel() }
    AppCard(modifier) {
        AppLineChartBlock(stringResource(Res.string.muscle_volume_chart_title, name)) {
            // Bars count in half sets so the 0,5 steps of secondary muscles keep their height.
            AppBarChart(
                values = values.map { (it * HALF_SETS_PER_SET).roundToInt() },
                labels = labels,
                description = stringResource(
                    Res.string.muscle_volume_chart_cd,
                    name,
                    weeklyDescription(labels, values),
                ),
                valueLabel = { formatSetsNumber(it / HALF_SETS_PER_SET) },
                highlightIndex = values.lastIndex,
                modifier = Modifier.fillMaxWidth().testTag("muscle_sets_chart"),
            )
        }
        WeekGrid(group, history, Modifier.fillMaxWidth().padding(top = Spacing.md))
    }
}

/** Week label over value, 4 columns by 2 rows on compact widths and 8 by 1 on wider ones; zero weeks are dimmed. */
@Composable
private fun WeekGrid(group: MuscleGroup, history: MuscleSetsHistory, modifier: Modifier = Modifier) {
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
            text = formatSetsNumber(value),
            style = AppTheme.typography.bodyMedium,
            color = if (value > 0.0) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("muscle_sets_week_${group.tagName()}_$index"),
        )
    }
}
