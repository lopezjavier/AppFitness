package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_year_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.px_routines_month_none
import io.github.lopezjavier.appfitness.composeapp.resources.px_routines_month_summary
import io.github.lopezjavier.appfitness.designsystem.ActivityHeatmap
import io.github.lopezjavier.appfitness.designsystem.ActivityHeatmapDefaults
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppLinearProgress
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.MiniBarChart
import io.github.lopezjavier.appfitness.designsystem.Spacing
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

private const val MONTHS = 12
private const val DECEMBER_LAST = 31
private const val DAYS_COMMON_YEAR = 365
private const val DAYS_LEAP_YEAR = 366
private const val COLUMNS_COMPACT = 3
private const val COLUMNS_WIDE = 4
private val MonthTileMinHeight = Spacing.minTouchTarget + Spacing.xl

/** Month view header: a hero card with the workouts of the shown month as one figure. */
@Composable
internal fun MonthSummary(workouts: Int, modifier: Modifier = Modifier) {
    val text = if (workouts == 0) {
        stringResource(Res.string.px_routines_month_none)
    } else {
        stringResource(Res.string.px_routines_month_summary, workoutsText(workouts))
    }
    AppCard(
        modifier.fillMaxWidth().testTag("calendar_month_summary"),
        glow = if (workouts > 0) AppTheme.colors.neonLime else null,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Res.drawable.ic_calendar, AppTheme.colors.neonLime)
            Text(
                text = text,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Year view: a hero card with the year's total and a bar per month ([monthCounts], month number 1-12; the bar of
 * [highlightMonth] is lit) over the 12 month tiles, each with its count and a bar relative to the best month; a tile
 * opens that month. [wide] puts four tiles per row instead of three. Tags `calendar_year_month_<n>`.
 */
@Composable
internal fun YearMonthTiles(
    year: Int,
    monthCounts: Map<Int, Int>,
    wide: Boolean,
    onSelectMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
    highlightMonth: Int? = null,
    dayCounts: Map<LocalDate, Int> = emptyMap(),
    today: LocalDate? = null,
) {
    val best = (1..MONTHS).maxOf { monthCounts[it] ?: 0 }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        AppCard(Modifier.fillMaxWidth(), glow = AppTheme.colors.athleticCyan) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = stringResource(Res.string.calendar_year_title, year, workoutsText(monthCounts.values.sum())),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() }.testTag("calendar_year_title"),
                )
                MiniBarChart(
                    values = (1..MONTHS).map { (monthCounts[it] ?: 0).toFloat() },
                    labels = (1..MONTHS).map { monthName(it).take(1) },
                    highlightIndex = highlightMonth?.minus(1),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (dayCounts.isNotEmpty()) YearHeatmap(year, dayCounts, today)
            }
        }
        for (months in (1..MONTHS).chunked(if (wide) COLUMNS_WIDE else COLUMNS_COMPACT)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                months.forEach { month ->
                    val count = monthCounts[month] ?: 0
                    MonthTile(
                        month = month,
                        count = count,
                        fraction = if (best == 0) 0f else count.toFloat() / best,
                        best = best > 0 && count == best,
                        onClick = { onSelectMonth(month) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One square per day of [year] (Monday first rows); days after [today] stay empty, today is outlined. */
@Composable
private fun YearHeatmap(year: Int, dayCounts: Map<LocalDate, Int>, today: LocalDate?) {
    val first = LocalDate(year, 1, 1)
    val leap = LocalDate(year, MONTHS, DECEMBER_LAST).dayOfYear > DAYS_COMMON_YEAR
    val length = if (leap) DAYS_LEAP_YEAR else DAYS_COMMON_YEAR
    val levels = (0 until length).map { offset ->
        val date = first.plus(offset, DateTimeUnit.DAY)
        val count = if (today != null && date > today) 0 else dayCounts[date] ?: 0
        count.coerceAtMost(ActivityHeatmapDefaults.MAX_LEVEL)
    }
    ActivityHeatmap(
        levels = levels,
        startRow = first.dayOfWeek.ordinal,
        highlightIndex = today?.takeIf { it.year == year }?.let { it.dayOfYear - 1 },
    )
}

@Composable
private fun MonthTile(
    month: Int,
    count: Int,
    fraction: Float,
    best: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = monthName(month)
    val description = countDescription(name, count)
    AppCard(
        modifier.heightIn(min = MonthTileMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("calendar_year_month_$month")
            .clearAndSetSemantics { contentDescription = description },
        highlighted = count > 0,
        glow = if (best) AppTheme.colors.athleticCyan else null,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
        ) {
            Text(name, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary, maxLines = 1)
            Text(
                text = count.toString(),
                style = AppTheme.typography.labelNumeric,
                color = if (count > 0) AppTheme.colors.athleticCyan else AppTheme.colors.textTertiary,
            )
            AppLinearProgress(fraction = fraction, stateDescription = description, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Multi-year view: one row per year with its workout count and a bar relative to the best year, newest first. */
@Composable
internal fun YearRows(yearCounts: Map<Int, Int>, onSelectYear: (Int) -> Unit, modifier: Modifier = Modifier) {
    val best = yearCounts.values.maxOrNull() ?: 0
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        yearCounts.keys.sortedDescending().forEach { year ->
            val count = yearCounts.getValue(year)
            val description = countDescription(year.toString(), count)
            AppCard(
                Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget)
                    .clickable(role = Role.Button, onClick = { onSelectYear(year) })
                    .testTag("calendar_years_year_$year")
                    .clearAndSetSemantics { contentDescription = description },
                glow = if (best > 0 && count == best) AppTheme.colors.athleticCyan else null,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = year.toString(),
                            style = AppTheme.typography.titleMedium,
                            color = AppTheme.colors.textPrimary,
                        )
                        Text(
                            text = workoutsText(count),
                            style = AppTheme.typography.labelNumeric,
                            color = AppTheme.colors.athleticCyan,
                        )
                    }
                    AppLinearProgress(
                        fraction = if (best == 0) 0f else count.toFloat() / best,
                        stateDescription = description,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
