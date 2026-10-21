@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_day_picker_title
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.MonthCalendarGrid
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet with a month calendar to jump to a day (never after [today]). Picking a day calls [onPickDay] and
 * [onDismiss]. The shown month survives recreation (ADR-40) and starts at the month of [selected].
 */
@Composable
internal fun DiaryDayPickerSheet(
    selected: LocalDate,
    today: LocalDate,
    onPickDay: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var monthText by rememberSaveable { mutableStateOf(selected.yearMonth.toString()) }
    val month = YearMonth.parse(monthText)
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = "diary_day_picker_sheet") {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Text(
                text = stringResource(Res.string.diary_day_picker_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() }.testTag("diary_day_picker_title"),
            )
            MonthCalendarGrid(
                month = month,
                markedDays = emptySet(),
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
