@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds MonthCalendarGrid and its defaults.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_left
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.month_1
import io.github.lopezjavier.appfitness.composeapp.resources.month_10
import io.github.lopezjavier.appfitness.composeapp.resources.month_11
import io.github.lopezjavier.appfitness.composeapp.resources.month_12
import io.github.lopezjavier.appfitness.composeapp.resources.month_2
import io.github.lopezjavier.appfitness.composeapp.resources.month_3
import io.github.lopezjavier.appfitness.composeapp.resources.month_4
import io.github.lopezjavier.appfitness.composeapp.resources.month_5
import io.github.lopezjavier.appfitness.composeapp.resources.month_6
import io.github.lopezjavier.appfitness.composeapp.resources.month_7
import io.github.lopezjavier.appfitness.composeapp.resources.month_8
import io.github.lopezjavier.appfitness.composeapp.resources.month_9
import io.github.lopezjavier.appfitness.composeapp.resources.month_calendar_day_cd
import io.github.lopezjavier.appfitness.composeapp.resources.month_calendar_day_marked
import io.github.lopezjavier.appfitness.composeapp.resources.month_calendar_day_selected
import io.github.lopezjavier.appfitness.composeapp.resources.month_calendar_day_unmarked
import io.github.lopezjavier.appfitness.composeapp.resources.month_calendar_next
import io.github.lopezjavier.appfitness.composeapp.resources.month_calendar_prev
import io.github.lopezjavier.appfitness.composeapp.resources.month_calendar_title
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_1
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_2
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_3
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_4
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_5
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_6
import io.github.lopezjavier.appfitness.composeapp.resources.weekday_short_7
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Sizes of [MonthCalendarGrid]. */
object MonthCalendarGridDefaults {
    /** Smallest width and height of a day cell: the 48 dp touch target. */
    val CellMin: Dp = Spacing.minTouchTarget
    val MarkSize: Dp = 6.dp
    const val DAYS_PER_WEEK = 7
    const val DISABLED_ALPHA = 0.38f
}

private val WeekdayLabels: List<StringResource> = listOf(
    Res.string.weekday_short_1,
    Res.string.weekday_short_2,
    Res.string.weekday_short_3,
    Res.string.weekday_short_4,
    Res.string.weekday_short_5,
    Res.string.weekday_short_6,
    Res.string.weekday_short_7,
)

private val MonthLabels: List<StringResource> = listOf(
    Res.string.month_1,
    Res.string.month_2,
    Res.string.month_3,
    Res.string.month_4,
    Res.string.month_5,
    Res.string.month_6,
    Res.string.month_7,
    Res.string.month_8,
    Res.string.month_9,
    Res.string.month_10,
    Res.string.month_11,
    Res.string.month_12,
)

/** The state of one day cell. */
private class DayCell(val date: LocalDate, val marked: Boolean, val selected: Boolean, val isToday: Boolean)

/**
 * Month calendar, Monday first: a header with the previous/next arrows and the month title, the weekday names and one
 * 48 dp cell per day. [markedDays] carry a dot (they have diary entries), [selected] is filled and [today] outlined.
 * Days after [today] are inert; days without entries are selectable. [onNextMonth] is disabled while [canGoNext] is
 * false. Tags: `month_calendar_prev|next|title` and `month_calendar_day_<yyyy-MM-dd>`.
 */
@Composable
@Suppress("LongParameterList") // One parameter per state value and callback.
fun MonthCalendarGrid(
    month: YearMonth,
    markedDays: Set<LocalDate>,
    selected: LocalDate?,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoNext: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        MonthHeader(month, canGoNext, onPrevMonth, onNextMonth)
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            // Never narrower than the touch target: a very narrow window overflows its margins instead.
            val cellWidth = maxOf(MonthCalendarGridDefaults.CellMin, maxWidth / MonthCalendarGridDefaults.DAYS_PER_WEEK)
            val weeks = remember(month, markedDays, selected, today) {
                weeksOf(month).map { week ->
                    week.map { date ->
                        date?.let { DayCell(it, it in markedDays, it == selected, it == today) }
                    }
                }
            }
            Column(Modifier.requiredWidth(cellWidth * MonthCalendarGridDefaults.DAYS_PER_WEEK)) {
                WeekdayHeader(cellWidth)
                weeks.forEach { week ->
                    Row {
                        week.forEach { cell ->
                            if (cell == null) {
                                Spacer(Modifier.size(cellWidth, MonthCalendarGridDefaults.CellMin))
                            } else {
                                DayCellView(cell, cellWidth, cell.date <= today, onSelect)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, canGoNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppIconButton(
            contentDescription = stringResource(Res.string.month_calendar_prev),
            icon = Res.drawable.ic_chevron_left,
            testTag = "month_calendar_prev",
            onClick = onPrev,
        )
        Text(
            text = stringResource(
                Res.string.month_calendar_title,
                stringResource(MonthLabels[month.month.ordinal]),
                month.year,
            ),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() }.testTag("month_calendar_title"),
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.month_calendar_next),
            icon = Res.drawable.ic_chevron_right,
            testTag = "month_calendar_next",
            onClick = onNext,
            enabled = canGoNext,
        )
    }
}

@Composable
private fun WeekdayHeader(cellWidth: Dp) {
    Row(Modifier.clearAndSetSemantics {}) {
        WeekdayLabels.forEach { label ->
            Text(
                text = stringResource(label),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(cellWidth).padding(vertical = Spacing.xs),
            )
        }
    }
}

// The hit area is the whole 48 dp cell while the visible circle is padded inside it, so the selectable sits first.
@Composable
@Suppress("ModifierClickableOrder")
private fun DayCellView(cell: DayCell, cellWidth: Dp, enabled: Boolean, onSelect: (LocalDate) -> Unit) {
    val colors = AppTheme.colors
    val description = stringResource(
        Res.string.month_calendar_day_cd,
        cell.date.day,
        stringResource(MonthLabels[cell.date.month.ordinal]),
        stringResource(
            if (cell.marked) Res.string.month_calendar_day_marked else Res.string.month_calendar_day_unmarked,
        ),
    )
    val selectedText = stringResource(Res.string.month_calendar_day_selected)
    Box(
        Modifier
            .size(cellWidth, MonthCalendarGridDefaults.CellMin)
            .alpha(if (enabled) 1f else MonthCalendarGridDefaults.DISABLED_ALPHA)
            .semantics {
                contentDescription = description
                if (cell.selected) stateDescription = selectedText
            }
            .selectable(
                selected = cell.selected,
                enabled = enabled,
                role = Role.Button,
                onClick = { onSelect(cell.date) },
            )
            .testTag("month_calendar_day_${cell.date}")
            .padding(Spacing.xs)
            .clip(AppShapes.full)
            .background(if (cell.selected) colors.neonLime else colors.surfaceBase)
            .let { if (cell.isToday) it.border(AppBorder.focus, colors.neonLime, AppShapes.full) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cell.date.day.toString(),
            style = AppTheme.typography.bodyMedium,
            color = if (cell.selected) colors.onNeonLime else colors.textPrimary,
            modifier = Modifier.clearAndSetSemantics {},
        )
        if (cell.marked) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.xs)
                    .size(MonthCalendarGridDefaults.MarkSize)
                    .clip(AppShapes.full)
                    .background(if (cell.selected) colors.onNeonLime else colors.neonLime),
            )
        }
    }
}

/** The weeks of [month], Monday first; null pads the days outside the month. */
internal fun weeksOf(month: YearMonth): List<List<LocalDate?>> {
    val first = month.firstDay
    val leading = first.dayOfWeek.ordinal
    val days: List<LocalDate?> = List(leading) { null } +
        (1..month.numberOfDays).map { LocalDate(month.year, month.month, it) }
    val week = MonthCalendarGridDefaults.DAYS_PER_WEEK
    val padded = days + List((week - days.size % week) % week) { null }
    return padded.chunked(week)
}
