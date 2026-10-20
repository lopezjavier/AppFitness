@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.copy_entries_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.copy_entries_day_label
import io.github.lopezjavier.appfitness.composeapp.resources.copy_entries_offline_hint
import io.github.lopezjavier.appfitness.composeapp.resources.copy_entries_title
import io.github.lopezjavier.appfitness.composeapp.resources.date_tomorrow
import io.github.lopezjavier.appfitness.composeapp.resources.diary_next_day
import io.github.lopezjavier.appfitness.composeapp.resources.diary_prev_day
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_left
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.feature.food.MealSelector
import io.github.lopezjavier.appfitness.feature.workout.sessionDateLabel
import io.github.lopezjavier.appfitness.feature.workout.text
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

/** "Hoy", "Ayer", "Mañana" or "lun, 24 oct": how a destination day is named. */
@Composable
internal fun dayText(date: LocalDate, today: LocalDate): String = if (date == today.plus(1, DateTimeUnit.DAY)) {
    stringResource(Res.string.date_tomorrow)
} else {
    sessionDateLabel(date, today).text()
}

/**
 * Copy bottom sheet (US-19): pick the destination meal and day (any day, no cap: the previous / next arrows move one
 * day), then "Copiar". Copying needs the server, so offline the button is disabled with a visible reason.
 */
@Composable
@Suppress("LongParameterList") // The draft, the day it is relative to and one callback per action.
fun CopyEntriesSheet(
    slots: List<MealSlot>,
    selectedSlotId: String?,
    date: LocalDate,
    today: LocalDate,
    offline: Boolean,
    onSlotSelect: (String) -> Unit,
    onDayChange: (LocalDate) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = "copy_entries_sheet") {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Text(
                text = stringResource(Res.string.copy_entries_title),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
            )
            MealSelector(
                slots = slots,
                selectedSlotId = selectedSlotId,
                onSelect = onSlotSelect,
                containerTag = "copy_entries_slots",
                optionTagPrefix = "copy_entries_slot",
                emptyTag = "copy_entries_no_slot",
            )
            DayRow(date, today) { onDayChange(date.plus(it, DateTimeUnit.DAY)) }
            if (offline) {
                Text(
                    text = stringResource(Res.string.copy_entries_offline_hint),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("copy_entries_offline"),
                )
            }
            PrimaryButton(
                text = stringResource(Res.string.copy_entries_confirm),
                onClick = onConfirm,
                enabled = !offline && selectedSlotId != null,
                modifier = Modifier.fillMaxWidth().testTag("copy_entries_confirm"),
            )
        }
    }
}

/** The destination day between its previous / next arrows; [onStep] receives -1 or +1. */
@Composable
private fun DayRow(date: LocalDate, today: LocalDate, onStep: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(Res.string.copy_entries_day_label),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(
                contentDescription = stringResource(Res.string.diary_prev_day),
                icon = Res.drawable.ic_chevron_left,
                testTag = "copy_entries_prev_day",
                onClick = { onStep(-1) },
            )
            Text(
                text = dayText(date, today),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).testTag("copy_entries_day"),
            )
            AppIconButton(
                contentDescription = stringResource(Res.string.diary_next_day),
                icon = Res.drawable.ic_chevron_right,
                testTag = "copy_entries_next_day",
                onClick = { onStep(1) },
            )
        }
    }
}
