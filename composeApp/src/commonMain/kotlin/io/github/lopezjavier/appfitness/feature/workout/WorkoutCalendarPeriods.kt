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
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

private const val MONTHS = 12
private const val COLUMNS_COMPACT = 3
private const val COLUMNS_WIDE = 4

/**
 * Year view: the 12 months of [year] as tiles with their workout counts ([monthCounts], month number 1-12); a tile
 * opens that month. [wide] puts four tiles per row instead of three. Tags `calendar_year_month_<n>`.
 */
@Composable
internal fun YearMonthTiles(
    year: Int,
    monthCounts: Map<Int, Int>,
    wide: Boolean,
    onSelectMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        Text(
            text = stringResource(Res.string.calendar_year_title, year),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() }.testTag("calendar_year_title"),
        )
        for (months in (1..MONTHS).chunked(if (wide) COLUMNS_WIDE else COLUMNS_COMPACT)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                months.forEach { month ->
                    MonthTile(month, monthCounts[month] ?: 0, { onSelectMonth(month) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MonthTile(month: Int, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = monthName(month)
    val description = countDescription(name, count)
    AppCard(
        modifier.heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("calendar_year_month_$month")
            .clearAndSetSemantics { contentDescription = description },
        highlighted = count > 0,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
        ) {
            Text(name, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary, maxLines = 1)
            Text(
                text = workoutsText(count),
                style = AppTheme.typography.labelNumeric,
                color = if (count > 0) AppTheme.colors.athleticCyan else AppTheme.colors.textSecondary,
            )
        }
    }
}

/** Multi-year view: one row per year with its workout count, newest first; a row opens that year. */
@Composable
internal fun YearRows(yearCounts: Map<Int, Int>, onSelectYear: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        yearCounts.keys.sortedDescending().forEach { year ->
            val count = yearCounts.getValue(year)
            val description = countDescription(year.toString(), count)
            AppCard(
                Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget)
                    .clickable(role = Role.Button, onClick = { onSelectYear(year) })
                    .testTag("calendar_years_year_$year")
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(year.toString(), style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
                    Text(
                        text = workoutsText(count),
                        style = AppTheme.typography.labelNumeric,
                        color = AppTheme.colors.athleticCyan,
                    )
                }
            }
        }
    }
}
