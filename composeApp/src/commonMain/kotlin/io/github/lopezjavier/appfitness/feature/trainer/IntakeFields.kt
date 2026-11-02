package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.intake_kcal_range
import io.github.lopezjavier.appfitness.composeapp.resources.intake_maintenance_field
import io.github.lopezjavier.appfitness.composeapp.resources.intake_maintenance_hint
import io.github.lopezjavier.appfitness.composeapp.resources.intake_maintenance_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_save_action
import io.github.lopezjavier.appfitness.composeapp.resources.intake_save_failed
import io.github.lopezjavier.appfitness.composeapp.resources.intake_unset
import io.github.lopezjavier.appfitness.composeapp.resources.intake_years_hint
import io.github.lopezjavier.appfitness.composeapp.resources.intake_years_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_years_less
import io.github.lopezjavier.appfitness.composeapp.resources.intake_years_more
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_provisional
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import org.jetbrains.compose.resources.stringResource

private const val MAX_KCAL_DIGITS = 4

/**
 * The intake rows of [kinds] (R-55): value or "Sin indicar", a "Provisional" chip for a stated number, and a tap that
 * opens only that row's editor through [onOpen]. [screen] prefixes the test tags.
 */
@Composable
internal fun IntakeRows(
    intake: IntakeSummary,
    kinds: List<IntakeRowKind>,
    screen: String,
    onOpen: (IntakeRowKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unset = stringResource(Res.string.intake_unset)
    Column(modifier) {
        kinds.forEach { kind ->
            val tag = kind.rowTag(screen)
            NavigationRow(
                label = stringResource(kind.intakeLabel()),
                value = kind.valueText(intake) ?: unset,
                onClick = { onOpen(kind) },
                modifier = Modifier.testTag(tag),
            )
            if (kind != IntakeRowKind.FreeText && intake.isProvisional(kind)) {
                StatusChip(
                    text = stringResource(Res.string.trainer_provisional),
                    tone = StatusTone.Caution,
                    modifier = Modifier.testTag("${tag}_chip_provisional"),
                )
            }
        }
    }
}

/**
 * The small sheet of the open row of [editor], if any: the years stepper or the maintenance field, each with a
 * pinned "Guardar" (R-12). Closing drops the draft silently (R-14). Shared by "Antes de empezar" and "Tus datos".
 */
@Composable
internal fun IntakeRowSheetHost(editor: IntakeRowEditorState, online: Boolean, onEvent: (IntakeRowEvent) -> Unit) {
    val haptics = LocalAppHaptics.current
    LaunchedEffect(editor.notice) {
        if (editor.notice != null) haptics.reject()
    }
    val hint = when {
        !online -> offlineHint(stringResource(Res.string.intake_save_action)) + "."
        editor.notice != null -> stringResource(Res.string.intake_save_failed)
        else -> null
    }
    when (val sheet = editor.sheet) {
        is IntakeRowSheet.Years -> YearsSheet(sheet, editor, online, hint, onEvent)
        is IntakeRowSheet.Maintenance -> MaintenanceSheet(sheet, editor, online, hint, onEvent)
        null -> Unit
    }
}

@Composable
private fun YearsSheet(
    sheet: IntakeRowSheet.Years,
    editor: IntakeRowEditorState,
    online: Boolean,
    hint: String?,
    onEvent: (IntakeRowEvent) -> Unit,
) {
    val haptics = LocalAppHaptics.current
    val years = sheet.years
    val text = years?.let { yearsText(it) } ?: stringResource(Res.string.intake_unset)
    AppEditSheet(
        title = stringResource(Res.string.intake_years_label),
        commit = SheetCommit.Save,
        onCommit = { onEvent(IntakeRowEvent.Save) },
        onDismiss = { onEvent(IntakeRowEvent.Close) },
        commitEnabled = editor.canSave && online,
        saving = editor.saving,
        commitHint = hint,
        testTagPrefix = "intake_years_sheet",
    ) {
        Text(
            text = stringResource(Res.string.intake_years_hint),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally),
        ) {
            NumberStepper(
                modifier = Modifier.testTag("intake_years_sheet_stepper"),
                valueText = text,
                onDecrement = {
                    haptics.tick()
                    onEvent(IntakeRowEvent.YearsDown)
                },
                onIncrement = {
                    haptics.tick()
                    onEvent(IntakeRowEvent.YearsUp)
                },
                decrementEnabled = !editor.saving && years != null,
                incrementEnabled = !editor.saving && (years == null || years < IntakeLimits.MAX_YEARS),
                decrementContentDescription = stringResource(Res.string.intake_years_less),
                incrementContentDescription = stringResource(Res.string.intake_years_more),
                stateDescription = text,
                testTagPrefix = "intake_years_sheet_stepper",
                valueColor = if (years == null) AppTheme.colors.textSecondary else AppTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun MaintenanceSheet(
    sheet: IntakeRowSheet.Maintenance,
    editor: IntakeRowEditorState,
    online: Boolean,
    hint: String?,
    onEvent: (IntakeRowEvent) -> Unit,
) {
    AppEditSheet(
        title = stringResource(Res.string.intake_maintenance_label),
        commit = SheetCommit.Save,
        onCommit = { onEvent(IntakeRowEvent.Save) },
        onDismiss = { onEvent(IntakeRowEvent.Close) },
        commitEnabled = editor.canSave && online,
        saving = editor.saving,
        commitHint = hint,
        testTagPrefix = "intake_maintenance_sheet",
    ) {
        Text(
            text = stringResource(Res.string.intake_maintenance_hint),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        NumberInput(
            value = sheet.text,
            onValueChange = { onEvent(IntakeRowEvent.EditMaintenance(it)) },
            label = stringResource(Res.string.intake_maintenance_field),
            modifier = Modifier.fillMaxWidth().testTag("intake_maintenance_sheet_field"),
            readOnly = editor.saving,
            maxIntegerDigits = MAX_KCAL_DIGITS,
            errorText = if (sheet.invalid) stringResource(Res.string.intake_kcal_range) else null,
            errorTestTag = "intake_maintenance_error",
        )
    }
}

/** A tap on an intake row: years and maintenance open their small sheet, the free text opens "Para conocerte". */
internal fun openIntakeRow(
    kind: IntakeRowKind,
    intake: IntakeSummary,
    onEditorEvent: (IntakeRowEvent) -> Unit,
    onFix: (PlanFixTarget) -> Unit,
) {
    when (kind) {
        IntakeRowKind.TrainingYears -> onEditorEvent(IntakeRowEvent.OpenYears(intake.trainingYears))
        IntakeRowKind.Maintenance -> onEditorEvent(IntakeRowEvent.OpenMaintenance(intake.maintenanceKcal))
        IntakeRowKind.FreeText -> onFix(PlanFixTarget.FreeText)
    }
}
