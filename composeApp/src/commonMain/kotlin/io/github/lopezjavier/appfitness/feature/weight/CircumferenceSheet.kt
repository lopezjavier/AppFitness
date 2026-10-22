@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.background
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
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_help_arm
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_help_calf
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_help_chest
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_help_hip
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_help_neck
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_help_thigh
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_help_toggle_cd
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_help_waist
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_offline_reason
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_range_hint
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_sheet_title_add
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_sheet_title_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_left
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.ic_help
import io.github.lopezjavier.appfitness.composeapp.resources.weight_delete
import io.github.lopezjavier.appfitness.composeapp.resources.weight_save
import io.github.lopezjavier.appfitness.composeapp.resources.weight_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.weight_save_error_offline
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppShapes
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val VALUE_INTEGER_DIGITS = 3
private const val SHEET_TAG = "circumference_sheet"

/**
 * The add/edit sheet of a circumference: one value field per site (only the row's own when editing) and the day
 * stepper, with "Eliminar" (edit only) and "Guardar" pinned at the bottom. Closing it by any route discards the draft.
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
                ValueFields(sheet, onEvent)
                DayStepper(sheet, today, onEvent)
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

/** Editing shows the locked site and its value; adding lists every site. */
@Composable
private fun ValueFields(sheet: CircumferenceSheetState, onEvent: (CircumferencesEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        sheet.values.keys.forEach { site -> SiteValueField(sheet, site, onEvent) }
    }
}

/** The typed text lives here too ([NumberInput] needs a synchronous update); every edit also goes to the view model. */
@Composable
private fun SiteValueField(
    sheet: CircumferenceSheetState,
    site: CircumferenceSite,
    onEvent: (CircumferencesEvent) -> Unit,
) {
    var value by remember(sheet.existingId, site) { mutableStateOf(sheet.values.getValue(site)) }
    val tag = site.name.lowercase()
    val label = circumferenceSiteLabel(site)
    val helpOpen = sheet.helpSite == site
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label.uppercase(),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            if (!sheet.isEditing) {
                AppIconButton(
                    contentDescription = stringResource(Res.string.circumference_help_toggle_cd, label.lowercase()),
                    icon = Res.drawable.ic_help,
                    testTag = "${SHEET_TAG}_help_$tag",
                    onClick = { onEvent(CircumferencesEvent.ToggleHelp(site)) },
                    tint = AppTheme.colors.textSecondary,
                )
            }
        }
        if (helpOpen) SiteHelpText(site, tag)
        NumberInput(
            value = value,
            onValueChange = {
                value = it
                onEvent(CircumferencesEvent.SetValue(site, it))
            },
            label = label,
            modifier = Modifier.fillMaxWidth().testTag("${SHEET_TAG}_value_input_$tag"),
            maxDecimals = 1,
            maxIntegerDigits = VALUE_INTEGER_DIGITS,
            showLabel = false,
        )
        if (sheet.problems.containsKey(site)) {
            Text(
                text = stringResource(Res.string.circumference_range_hint),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier.testTag("${SHEET_TAG}_range_hint_$tag"),
            )
        }
    }
}

@Composable
private fun SiteHelpText(site: CircumferenceSite, tag: String) {
    Text(
        text = stringResource(helpTextRes(site)),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surfaceRaised, AppShapes.base)
            .padding(Spacing.md)
            .testTag("${SHEET_TAG}_help_text_$tag"),
    )
}

private fun helpTextRes(site: CircumferenceSite): StringResource = when (site) {
    CircumferenceSite.Neck -> Res.string.circumference_help_neck
    CircumferenceSite.Chest -> Res.string.circumference_help_chest
    CircumferenceSite.Waist -> Res.string.circumference_help_waist
    CircumferenceSite.Hip -> Res.string.circumference_help_hip
    CircumferenceSite.Arm -> Res.string.circumference_help_arm
    CircumferenceSite.Thigh -> Res.string.circumference_help_thigh
    CircumferenceSite.Calf -> Res.string.circumference_help_calf
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
