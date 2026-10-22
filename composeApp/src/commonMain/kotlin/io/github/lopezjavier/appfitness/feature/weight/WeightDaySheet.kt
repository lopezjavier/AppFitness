@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_health_connect
import io.github.lopezjavier.appfitness.composeapp.resources.weight_delete
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hc_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hc_icon_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hc_note
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hc_row_value
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hc_section
import io.github.lopezjavier.appfitness.composeapp.resources.weight_offline_reason
import io.github.lopezjavier.appfitness.composeapp.resources.weight_save
import io.github.lopezjavier.appfitness.composeapp.resources.weight_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.weight_save_error_offline
import io.github.lopezjavier.appfitness.composeapp.resources.weight_sheet_title_day
import io.github.lopezjavier.appfitness.composeapp.resources.weight_sheet_title_today
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.feature.workout.shortLabel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The sheet of a tapped day: its title, the Health Connect rows (delete only) and the manual weight form. Closing it
 * by any route discards the draft (ADR-45); the pinned "Guardar" and "Eliminar" are the only ways to write.
 */
@Composable
internal fun WeightDaySheet(
    sheet: WeightSheet,
    isOnline: Boolean,
    timeZone: TimeZone,
    onEvent: (WeightEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(
        onDismissRequest = { onEvent(WeightEvent.CloseSheet) },
        modifier = modifier,
        testTagPrefix = "weight_day_sheet",
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
                SheetTitle(sheet)
                if (sheet.healthConnect.isNotEmpty()) {
                    HealthConnectRows(sheet.healthConnect, isOnline, timeZone, onEvent)
                }
                if (sheet.manualLocked) HealthConnectNote()
                sheet.dayComposition?.let { CompositionLines(it) }
                sheet.form?.let { MeasurementFormFields(sheet.day, it, onEvent) }
            }
            sheet.form?.let { SheetActions(it, isOnline, onEvent) }
        }
    }
}

@Composable
private fun SheetTitle(sheet: WeightSheet) {
    val creatingToday = sheet.isToday && sheet.form?.isEditing != true
    val title = if (creatingToday) {
        stringResource(Res.string.weight_sheet_title_today)
    } else {
        stringResource(Res.string.weight_sheet_title_day, sheet.day.day, sheet.day.month.shortLabel())
    }
    Text(
        text = title,
        style = AppTheme.typography.titleMedium,
        color = AppTheme.colors.textPrimary,
        modifier = Modifier.semantics { heading() }.testTag("weight_sheet_title"),
    )
}

/** Why a day with a Health Connect weight has no manual entry (ADR-47). */
@Composable
private fun HealthConnectNote() {
    Text(
        text = stringResource(Res.string.weight_hc_note),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.testTag("weight_sheet_hc_note"),
    )
}

@Composable
private fun HealthConnectRows(
    rows: List<BodyMeasurement>,
    isOnline: Boolean,
    timeZone: TimeZone,
    onEvent: (WeightEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(Res.string.weight_hc_section),
            style = AppTheme.typography.labelTechnical,
            color = AppTheme.colors.textSecondary,
        )
        rows.forEach { row ->
            HealthConnectRow(row, isOnline, timeZone, onEvent)
        }
    }
}

/** A Health Connect measurement: its source icon, time and weight, and a delete button (no edit affordance). */
@Composable
private fun HealthConnectRow(
    row: BodyMeasurement,
    isOnline: Boolean,
    timeZone: TimeZone,
    onEvent: (WeightEvent) -> Unit,
) {
    val local = row.measuredAt.toLocalDateTime(timeZone)
    val time = timeText(local.hour, local.minute)
    Row(
        Modifier.fillMaxWidth().testTag("weight_hc_row_${row.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_health_connect),
            contentDescription = stringResource(Res.string.weight_hc_icon_cd),
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(Spacing.xl).testTag("weight_hc_icon_${row.id}"),
        )
        Text(
            text = stringResource(Res.string.weight_hc_row_value, time, decimalText(row.weightKg)),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.weight_hc_delete_cd, time),
            icon = Res.drawable.ic_delete,
            testTag = "weight_hc_delete_${row.id}",
            onClick = { onEvent(WeightEvent.DeleteHealthConnect(row.id)) },
            enabled = isOnline,
            tint = AppTheme.colors.error,
        )
    }
}

/** The pinned bottom row: "Eliminar" (editing only) and "Guardar", with why a write is unavailable above it. */
@Composable
private fun SheetActions(form: MeasurementForm, isOnline: Boolean, onEvent: (WeightEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val message = when {
            !isOnline -> stringResource(Res.string.weight_offline_reason)
            form.saveError == WeightSaveError.Offline -> stringResource(Res.string.weight_save_error_offline)
            form.saveError == WeightSaveError.Failed -> stringResource(Res.string.weight_save_error)
            else -> null
        }
        message?.let {
            Text(
                text = it,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier.testTag("weight_sheet_message"),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (form.isEditing) {
                GhostButton(
                    text = stringResource(Res.string.weight_delete),
                    onClick = { onEvent(WeightEvent.DeleteManual) },
                    modifier = Modifier.weight(1f).testTag("weight_sheet_delete"),
                    enabled = form.canDelete,
                    tone = GhostTone.Destructive,
                )
            }
            PrimaryButton(
                text = stringResource(Res.string.weight_save),
                onClick = { onEvent(WeightEvent.Save) },
                modifier = Modifier.weight(1f).testTag("weight_sheet_save"),
                enabled = form.canSave,
                loading = form.isSaving,
            )
        }
    }
}
