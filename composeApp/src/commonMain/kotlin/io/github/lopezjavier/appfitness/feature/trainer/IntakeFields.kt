package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_info
import io.github.lopezjavier.appfitness.composeapp.resources.intake_area_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_best_object
import io.github.lopezjavier.appfitness.composeapp.resources.intake_best_remove
import io.github.lopezjavier.appfitness.composeapp.resources.intake_best_reps_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_best_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.intake_best_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.intake_best_unknown_exercise
import io.github.lopezjavier.appfitness.composeapp.resources.intake_best_weight_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_bests_helper
import io.github.lopezjavier.appfitness.composeapp.resources.intake_bests_title
import io.github.lopezjavier.appfitness.composeapp.resources.intake_cut_helper
import io.github.lopezjavier.appfitness.composeapp.resources.intake_cut_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_kcal_range
import io.github.lopezjavier.appfitness.composeapp.resources.intake_known_title
import io.github.lopezjavier.appfitness.composeapp.resources.intake_maintenance_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_marker
import io.github.lopezjavier.appfitness.composeapp.resources.intake_situation_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_unset
import io.github.lopezjavier.appfitness.composeapp.resources.intake_years_label
import io.github.lopezjavier.appfitness.composeapp.resources.intake_years_less
import io.github.lopezjavier.appfitness.composeapp.resources.intake_years_more
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.NumberInputDefaults
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.OptionPickerSheet
import io.github.lopezjavier.appfitness.designsystem.PickerOption
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import io.github.lopezjavier.appfitness.domain.model.InjuredArea
import io.github.lopezjavier.appfitness.domain.model.PreBreakBest
import io.github.lopezjavier.appfitness.domain.model.Situation
import io.github.lopezjavier.appfitness.feature.exercises.tagName
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val MAX_WEIGHT_DECIMALS = 2
private const val MAX_WEIGHT_DIGITS = 4
private const val MAX_REPS_DIGITS = 2
private const val MAX_KCAL_DIGITS = 4

/** "Rellenado desde tu texto. Revísalo.": icon plus text under a field the extraction filled, until it is touched. */
@Composable
internal fun ExtractedMarker(field: IntakeField, extracted: Set<IntakeField>) {
    if (field !in extracted) return
    Row(
        Modifier.testTag("intake_extracted_marker_${field.tagName()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_info),
            contentDescription = null,
            tint = AppTheme.colors.athleticCyan,
            modifier = Modifier.size(Spacing.lg),
        )
        Text(
            text = stringResource(Res.string.intake_marker),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
    }
}

private const val FIRST_YEARS = 1.0

/** "Sin indicar" is the lowest step of the stepper: the coach never guesses (R-47). */
@Composable
internal fun YearsField(years: Double?, extracted: Set<IntakeField>, enabled: Boolean, onChange: (Double?) -> Unit) {
    val haptics = LocalAppHaptics.current
    val unset = stringResource(Res.string.intake_unset)
    val text = years?.let { yearsText(it) } ?: unset
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(Res.string.intake_years_label),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            NumberStepper(
                valueText = text,
                onDecrement = {
                    haptics.tick()
                    onChange(previousYears(years))
                },
                onIncrement = {
                    haptics.tick()
                    onChange(nextYears(years))
                },
                decrementEnabled = enabled && years != null,
                incrementEnabled = enabled && (years == null || years < IntakeLimits.MAX_YEARS),
                decrementContentDescription = stringResource(Res.string.intake_years_less),
                incrementContentDescription = stringResource(Res.string.intake_years_more),
                stateDescription = text,
                testTagPrefix = "intake_years",
                valueColor = if (years == null) AppTheme.colors.textSecondary else AppTheme.colors.textPrimary,
            )
        }
        ExtractedMarker(IntakeField.Years, extracted)
    }
}

/** "+" from "Sin indicar" gives one year; otherwise half a year more. */
internal fun nextYears(years: Double?): Double =
    if (years == null) FIRST_YEARS else minOf(years + IntakeLimits.YEARS_STEP, IntakeLimits.MAX_YEARS)

/** "-" at zero returns to "Sin indicar" (null). */
internal fun previousYears(years: Double?): Double? =
    years?.let { if (it <= 0.0) null else it - IntakeLimits.YEARS_STEP }

@Composable
internal fun SituationField(
    situation: Situation?,
    extracted: Set<IntakeField>,
    pickerOpen: Boolean,
    enabled: Boolean,
    onEvent: (IntakeEvent) -> Unit,
) {
    val unset = stringResource(Res.string.intake_unset)
    Column {
        NavigationRow(
            label = stringResource(Res.string.intake_situation_label),
            value = situation?.let { stringResource(it.intakeLabel()) } ?: unset,
            onClick = { onEvent(IntakeEvent.OpenPicker(IntakePicker.Situation)) },
            modifier = Modifier.testTag("intake_situation"),
            enabled = enabled,
        )
        ExtractedMarker(IntakeField.Situation, extracted)
    }
    if (pickerOpen) {
        val none = PickerOption("none", unset, situation == null) { onEvent(IntakeEvent.SelectSituation(null)) }
        val options = Situation.entries.map {
            PickerOption(it.tagName(), stringResource(it.intakeLabel()), it == situation) {
                onEvent(IntakeEvent.SelectSituation(it))
            }
        }
        OptionPickerSheet(
            title = stringResource(Res.string.intake_situation_label),
            tagPrefix = "intake_situation_picker",
            options = listOf(none) + options,
            onClose = { onEvent(IntakeEvent.ClosePicker) },
        )
    }
}

@Composable
internal fun InjuredAreaField(
    area: InjuredArea?,
    extracted: Set<IntakeField>,
    pickerOpen: Boolean,
    enabled: Boolean,
    onEvent: (IntakeEvent) -> Unit,
) {
    val unset = stringResource(Res.string.intake_unset)
    Column {
        NavigationRow(
            label = stringResource(Res.string.intake_area_label),
            value = area?.let { stringResource(it.intakeLabel()) } ?: unset,
            onClick = { onEvent(IntakeEvent.OpenPicker(IntakePicker.InjuredArea)) },
            modifier = Modifier.testTag("intake_injured_area"),
            enabled = enabled,
        )
        ExtractedMarker(IntakeField.InjuredArea, extracted)
    }
    if (pickerOpen) {
        val none = PickerOption("none", unset, area == null) { onEvent(IntakeEvent.SelectInjuredArea(null)) }
        val options = InjuredArea.entries.map {
            PickerOption(it.tagName(), stringResource(it.intakeLabel()), it == area) {
                onEvent(IntakeEvent.SelectInjuredArea(it))
            }
        }
        OptionPickerSheet(
            title = stringResource(Res.string.intake_area_label),
            tagPrefix = "intake_injured_area_picker",
            options = listOf(none) + options,
            onClose = { onEvent(IntakeEvent.ClosePicker) },
        )
    }
}

/** Callbacks of the bests list: open the exercise picker, edit one best. */
internal class BestsActions(val onAdd: () -> Unit, val onEdit: (String) -> Unit)

/** The best lifts from before a pause: one row each (swipe "Quitar"), one "Añadir marca" at the end (R-19). */
@Composable
internal fun BestsField(
    state: IntakeUiState.Content,
    names: Map<String, String>,
    enabled: Boolean,
    onEvent: (IntakeEvent) -> Unit,
    actions: BestsActions,
) {
    val unknown = stringResource(Res.string.intake_best_unknown_exercise)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ListSectionHeader(stringResource(Res.string.intake_bests_title))
        Text(
            text = stringResource(Res.string.intake_bests_helper),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
        ExtractedMarker(IntakeField.Bests, state.extracted)
        state.form.bests.forEach { best ->
            val name = names[best.exerciseId] ?: unknown
            SwipeToDeleteRow(
                deleteLabel = "${stringResource(Res.string.intake_best_remove)} $name",
                enabled = enabled,
                onDelete = { onEvent(IntakeEvent.RemoveBest(best.exerciseId)) },
                backgroundTag = "intake_best_remove_${best.exerciseId}",
                label = stringResource(Res.string.intake_best_remove),
            ) {
                ExerciseRow(
                    name = name,
                    supporting = stringResource(
                        Res.string.intake_best_supporting,
                        AppNumberFormat.decimal(best.weightKg, maxDecimals = MAX_WEIGHT_DECIMALS),
                        best.reps,
                    ),
                    onOpen = { if (enabled) actions.onEdit(best.exerciseId) },
                    tagPrefix = "intake_best_row_${best.exerciseId}",
                )
            }
        }
        AddItemButton(
            objectLabel = stringResource(Res.string.intake_best_object),
            onClick = actions.onAdd,
            modifier = Modifier.testTag("intake_add_best"),
            enabled = enabled && state.canAddBest,
        )
    }
}

/** Weight and repetitions of one best lift; "Listo" applies it to the form (the screen's "Guardar" persists, R-12). */
@Composable
internal fun BestSheet(
    exerciseName: String,
    current: PreBreakBest?,
    onApply: (weightKg: Double, reps: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var weight by rememberSaveable {
        mutableStateOf(
            current?.let {
                AppNumberFormat.decimal(it.weightKg, MAX_WEIGHT_DECIMALS, grouping = false)
            }.orEmpty(),
        )
    }
    var reps by rememberSaveable { mutableStateOf(current?.reps?.toString().orEmpty()) }
    val weightValue = NumberInputDefaults.parse(weight)
    val repsValue = reps.toIntOrNull()
    val valid = weightValue != null && weightValue > 0.0 && repsValue != null &&
        repsValue in IntakeLimits.MIN_REPS..IntakeLimits.MAX_REPS
    AppEditSheet(
        title = stringResource(Res.string.intake_best_sheet_title, exerciseName),
        commit = SheetCommit.Apply,
        onCommit = { if (valid && weightValue != null && repsValue != null) onApply(weightValue, repsValue) },
        onDismiss = onDismiss,
        commitEnabled = valid,
        testTagPrefix = "intake_best_sheet",
    ) {
        NumberInput(
            value = weight,
            onValueChange = { weight = it },
            label = stringResource(Res.string.intake_best_weight_label),
            modifier = Modifier.testTag("intake_best_weight"),
            maxDecimals = MAX_WEIGHT_DECIMALS,
            maxIntegerDigits = MAX_WEIGHT_DIGITS,
        )
        NumberInput(
            value = reps,
            onValueChange = { reps = it },
            label = stringResource(Res.string.intake_best_reps_label),
            modifier = Modifier.testTag("intake_best_reps"),
            maxIntegerDigits = MAX_REPS_DIGITS,
        )
    }
}

/** The two optional calorie figures ("Si las conoces"); the cutting one is context only. */
@Composable
internal fun KnownCaloriesFields(state: IntakeUiState.Content, enabled: Boolean, onEvent: (IntakeEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ListSectionHeader(stringResource(Res.string.intake_known_title))
        KcalField(
            label = stringResource(Res.string.intake_maintenance_label),
            kcal = state.form.maintenanceKcal,
            invalid = IntakeField.Maintenance in state.form.errors,
            tag = "intake_maintenance_kcal",
            helper = null,
            enabled = enabled,
        ) { onEvent(IntakeEvent.EditMaintenance(it)) }
        ExtractedMarker(IntakeField.Maintenance, state.extracted)
        KcalField(
            label = stringResource(Res.string.intake_cut_label),
            kcal = state.form.cutKcal,
            invalid = IntakeField.CutKcal in state.form.errors,
            tag = "intake_cut_kcal",
            helper = stringResource(Res.string.intake_cut_helper),
            enabled = enabled,
        ) { onEvent(IntakeEvent.EditCutKcal(it)) }
        ExtractedMarker(IntakeField.CutKcal, state.extracted)
    }
}

@Composable
@Suppress("LongParameterList") // Label, value, validity, tag, helper and handler of one field.
private fun KcalField(
    label: String,
    kcal: Int?,
    invalid: Boolean,
    tag: String,
    helper: String?,
    enabled: Boolean,
    onChange: (Int?) -> Unit,
) {
    // Local text so the cursor never jumps; an outside change (the extraction) rebuilds it.
    var text by remember(kcal) { mutableStateOf(kcal?.toString().orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        NumberInput(
            value = text,
            onValueChange = {
                text = it
                onChange(it.toIntOrNull())
            },
            label = label,
            modifier = Modifier.fillMaxWidth().testTag(tag),
            readOnly = !enabled,
            maxIntegerDigits = MAX_KCAL_DIGITS,
            errorText = if (invalid) stringResource(Res.string.intake_kcal_range) else null,
            errorTestTag = "${tag}_error",
        )
        if (helper != null) {
            Text(text = helper, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
        }
    }
}
