package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_health_connect
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_value
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hc_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hc_icon_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hc_note
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hc_section
import io.github.lopezjavier.appfitness.composeapp.resources.weight_sheet_title_day
import io.github.lopezjavier.appfitness.composeapp.resources.weight_sheet_title_today
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.GhostTone
import io.github.lopezjavier.appfitness.designsystem.MeasurementRow
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.feature.workout.shortLabel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val DAY_SHEET_TAG = "weight_day_sheet"

/**
 * The sheet of a tapped day (edit archetype): the Health Connect rows (swipe to delete), the manual weight form and
 * the pinned "Guardar" with "Eliminar" when editing. A day owned by Health Connect has no form, so it is an info
 * sheet with the rows only. Closing it by any route discards the draft (ADR-45); "Guardar" is the only write.
 */
@Composable
internal fun WeightDaySheet(
    sheet: WeightSheet,
    isOnline: Boolean,
    timeZone: TimeZone,
    onEvent: (WeightEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val creatingToday = sheet.isToday && sheet.form?.isEditing != true
    val title = if (creatingToday) {
        stringResource(Res.string.weight_sheet_title_today)
    } else {
        stringResource(Res.string.weight_sheet_title_day, sheet.day.day, sheet.day.month.shortLabel())
    }
    val form = sheet.form
    val body: @Composable ColumnScope.() -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            if (sheet.healthConnect.isNotEmpty()) HealthConnectRows(sheet.healthConnect, isOnline, timeZone, onEvent)
            if (sheet.manualLocked) HealthConnectNote()
            sheet.dayComposition?.let { CompositionLines(it) }
            form?.let { MeasurementFormFields(sheet.day, it, onEvent) }
        }
    }
    if (form == null) {
        AppInfoSheet(
            title = title,
            onDismiss = { onEvent(WeightEvent.CloseSheet) },
            modifier = modifier,
            testTagPrefix = DAY_SHEET_TAG,
            content = body,
        )
    } else {
        AppEditSheet(
            title = title,
            commit = SheetCommit.Save,
            onCommit = { onEvent(WeightEvent.Save) },
            onDismiss = { onEvent(WeightEvent.CloseSheet) },
            modifier = modifier,
            commitEnabled = form.canSave,
            saving = form.isSaving,
            commitHint = if (isOnline) null else offlineHint("guardar"),
            secondaryAction = if (form.isEditing) {
                {
                    GhostButton(
                        text = stringResource(Res.string.action_delete),
                        onClick = { onEvent(WeightEvent.DeleteManual) },
                        modifier = Modifier.testTag("weight_sheet_delete"),
                        enabled = form.canDelete,
                        tone = GhostTone.Destructive,
                    )
                }
            } else {
                null
            },
            testTagPrefix = DAY_SHEET_TAG,
            content = body,
        )
    }
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
        rows.forEach { row -> HealthConnectRow(row, isOnline, timeZone, onEvent) }
    }
}

/** A Health Connect measurement: its source icon, weight and time; swipe to delete, no edit affordance. */
@Composable
private fun HealthConnectRow(
    row: BodyMeasurement,
    isOnline: Boolean,
    timeZone: TimeZone,
    onEvent: (WeightEvent) -> Unit,
) {
    val local = row.measuredAt.toLocalDateTime(timeZone)
    val time = timeText(local.hour, local.minute)
    SwipeToDeleteRow(
        deleteLabel = stringResource(Res.string.weight_hc_delete_cd, time),
        enabled = isOnline,
        onDelete = { onEvent(WeightEvent.DeleteHealthConnect(row.id)) },
        backgroundTag = "weight_hc_delete_bg_${row.id}",
        modifier = Modifier.testTag("weight_hc_row_${row.id}"),
        containerColor = AppTheme.colors.surfaceBase,
    ) {
        MeasurementRow(
            value = stringResource(Res.string.weight_current_value, decimalText(row.weightKg)),
            supporting = time,
            onClick = null,
            clickLabel = null,
            leading = {
                Icon(
                    painter = painterResource(Res.drawable.ic_health_connect),
                    contentDescription = stringResource(Res.string.weight_hc_icon_cd),
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(Spacing.xl).testTag("weight_hc_icon_${row.id}"),
                )
            },
        )
    }
}
