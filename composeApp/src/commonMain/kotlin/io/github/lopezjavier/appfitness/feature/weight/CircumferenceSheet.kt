@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_day_next_cd
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_day_previous_cd
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_field_date
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_field_site
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_field_value
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_offline_reason
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_range_hint
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_sheet_title_add
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_sheet_title_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_left
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.weight_delete
import io.github.lopezjavier.appfitness.composeapp.resources.weight_save
import io.github.lopezjavier.appfitness.composeapp.resources.weight_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.weight_save_error_offline
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppDropdownField
import io.github.lopezjavier.appfitness.designsystem.AppDropdownOption
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

private const val VALUE_INTEGER_DIGITS = 3
private const val SHEET_TAG = "circumference_sheet"

/**
 * The add/edit sheet of a circumference: site (a dropdown, read-only text when editing), value, day stepper and time
 * wheels, with "Eliminar" (edit only) and "Guardar" pinned at the bottom. Closing it by any route discards the draft.
 */
@Composable
internal fun CircumferenceSheet(
    sheet: CircumferenceSheetState,
    isOnline: Boolean,
    today: LocalDate,
    onEvent: (CircumferencesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(
        onDismissRequest = { onEvent(CircumferencesEvent.CloseSheet) },
        modifier = modifier,
        testTagPrefix = SHEET_TAG,
    ) {
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
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                CircumferenceSheetTitle(sheet.isEditing)
                SiteField(sheet, onEvent)
                ValueField(sheet, onEvent)
                DayStepper(sheet, today, onEvent)
                TimeWheelFields(
                    hour = sheet.hour,
                    minute = sheet.minute,
                    maxMinuteOfDay = sheet.maxMinuteOfDay,
                    testTagPrefix = SHEET_TAG,
                    onTimeChange = { hour, minute -> onEvent(CircumferencesEvent.SetTime(hour, minute)) },
                )
            }
            SheetActions(sheet, isOnline, onEvent)
        }
    }
}

@Composable
private fun CircumferenceSheetTitle(editing: Boolean) {
    Text(
        text = stringResource(
            if (editing) Res.string.circumference_sheet_title_edit else Res.string.circumference_sheet_title_add,
        ),
        style = AppTheme.typography.titleMedium,
        color = AppTheme.colors.textPrimary,
        modifier = Modifier.semantics { heading() }.testTag("${SHEET_TAG}_title"),
    )
}

@Composable
private fun SiteField(sheet: CircumferenceSheetState, onEvent: (CircumferencesEvent) -> Unit) {
    val label = stringResource(Res.string.circumference_field_site)
    val siteLabel = circumferenceSiteLabel(sheet.site)
    if (sheet.isEditing) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = label, style = AppTheme.typography.labelTechnical, color = AppTheme.colors.textSecondary)
            Text(
                text = siteLabel,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.heightIn(min = Spacing.minTouchTarget).testTag("${SHEET_TAG}_site_readonly"),
            )
        }
    } else {
        val options = CircumferenceSite.entries.map { AppDropdownOption(siteKey(it), circumferenceSiteLabel(it)) }
        AppDropdownField(
            label = label,
            options = options,
            selectedKey = siteKey(sheet.site),
            onSelect = { key ->
                CircumferenceSite.entries.firstOrNull { siteKey(it) == key }
                    ?.let { onEvent(CircumferencesEvent.SelectSite(it)) }
            },
            testTagPrefix = "${SHEET_TAG}_site",
        )
    }
}

/** The typed text lives here too ([NumberInput] needs a synchronous update); every edit also goes to the view model. */
@Composable
private fun ValueField(sheet: CircumferenceSheetState, onEvent: (CircumferencesEvent) -> Unit) {
    var value by remember(sheet.existingId) { mutableStateOf(sheet.valueText) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        NumberInput(
            value = value,
            onValueChange = {
                value = it
                onEvent(CircumferencesEvent.SetValue(it))
            },
            label = stringResource(Res.string.circumference_field_value),
            modifier = Modifier.testTag("${SHEET_TAG}_value_input"),
            maxDecimals = 1,
            maxIntegerDigits = VALUE_INTEGER_DIGITS,
        )
        if (sheet.valueProblem != null) {
            Text(
                text = stringResource(Res.string.circumference_range_hint),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier.testTag("${SHEET_TAG}_range_hint"),
            )
        }
    }
}

/** "Día anterior" / "Día siguiente" around the day text; the next button stops at today. */
@Composable
private fun DayStepper(sheet: CircumferenceSheetState, today: LocalDate, onEvent: (CircumferencesEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(Res.string.circumference_field_date),
            style = AppTheme.typography.labelTechnical,
            color = AppTheme.colors.textSecondary,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppIconButton(
                contentDescription = stringResource(Res.string.circumference_day_previous_cd),
                icon = Res.drawable.ic_chevron_left,
                testTag = "${SHEET_TAG}_date_prev",
                onClick = { onEvent(CircumferencesEvent.SetDate(sheet.date.minus(1, DateTimeUnit.DAY))) },
            )
            Text(
                text = circumferenceDayText(sheet.date, today),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.testTag("${SHEET_TAG}_date_text"),
            )
            AppIconButton(
                contentDescription = stringResource(Res.string.circumference_day_next_cd),
                icon = Res.drawable.ic_chevron_right,
                testTag = "${SHEET_TAG}_date_next",
                onClick = { onEvent(CircumferencesEvent.SetDate(sheet.date.plus(1, DateTimeUnit.DAY))) },
                enabled = sheet.canGoNextDay,
            )
        }
    }
}

/** The pinned bottom block: why a write is unavailable, then "Eliminar" (editing only) and "Guardar". */
@Composable
private fun SheetActions(sheet: CircumferenceSheetState, isOnline: Boolean, onEvent: (CircumferencesEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val message = when {
            !isOnline -> stringResource(Res.string.circumference_offline_reason)
            sheet.saveError == WeightSaveError.Offline -> stringResource(Res.string.weight_save_error_offline)
            sheet.saveError == WeightSaveError.Failed -> stringResource(Res.string.weight_save_error)
            else -> null
        }
        message?.let {
            Text(
                text = it,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier.testTag("${SHEET_TAG}_message"),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            sheet.existingId?.let { id ->
                GhostButton(
                    text = stringResource(Res.string.weight_delete),
                    onClick = { onEvent(CircumferencesEvent.Delete(id)) },
                    modifier = Modifier.weight(1f).testTag("${SHEET_TAG}_delete"),
                    enabled = sheet.canDelete,
                    tone = GhostTone.Destructive,
                )
            }
            PrimaryButton(
                text = stringResource(Res.string.weight_save),
                onClick = { onEvent(CircumferencesEvent.Save) },
                modifier = Modifier.weight(1f).testTag("${SHEET_TAG}_save"),
                enabled = sheet.canSave,
                loading = sheet.isSaving,
            )
        }
    }
}

private fun siteKey(site: CircumferenceSite): String = site.name.lowercase()
