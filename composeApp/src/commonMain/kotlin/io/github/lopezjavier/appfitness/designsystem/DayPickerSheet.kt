package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.date_today
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource

/**
 * "Calendario" picker sheet (R-12, no footer): a month calendar to jump to a day (never after [today]) with the
 * [markedDays] of the shown month dotted. A day applies on tap and closes; the "Hoy" chip (shown when `!isToday`)
 * goes back to today and closes. [onMonthChange] reports every shown month (the first one included) so the caller can
 * load its marks. The shown month survives recreation (ADR-40) and starts at the month of [selected]. Tags:
 * `<tagPrefix>_sheet` (+ `_close`) and `<tagPrefix>_today`.
 */
@Composable
@Suppress("LongParameterList") // The shown day, the marks, three actions, the texts and the dismissal.
fun DayPickerSheet(
    title: String,
    todayContentDescription: String,
    tagPrefix: String,
    selected: LocalDate,
    today: LocalDate,
    isToday: Boolean,
    markedDays: Set<LocalDate>,
    onPickDay: (LocalDate) -> Unit,
    onGoToToday: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Declared outside the sheet window so it is restored with the screen.
    var monthText by rememberSaveable { mutableStateOf(selected.yearMonth.toString()) }
    val month = YearMonth.parse(monthText)
    val currentOnMonthChange by rememberUpdatedState(onMonthChange)
    LaunchedEffect(month) { currentOnMonthChange(month) }
    AppPickerSheet(
        title = title,
        onDismiss = onDismiss,
        modifier = modifier,
        testTagPrefix = "${tagPrefix}_sheet",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            if (!isToday) {
                AppButtonChip(
                    label = stringResource(Res.string.date_today),
                    onClick = {
                        onGoToToday()
                        onDismiss()
                    },
                    contentDescription = todayContentDescription,
                    modifier = Modifier.testTag("${tagPrefix}_today"),
                )
            }
            MonthCalendarGrid(
                month = month,
                markedDays = markedDays,
                selected = selected,
                today = today,
                onSelect = {
                    onPickDay(it)
                    onDismiss()
                },
                onPrevMonth = { monthText = month.minus(1, DateTimeUnit.MONTH).toString() },
                onNextMonth = { monthText = month.plus(1, DateTimeUnit.MONTH).toString() },
                canGoNext = month < today.yearMonth,
            )
        }
    }
}
