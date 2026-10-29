package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_equipment_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_metric_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_muscle_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_per_hand_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_per_hand_help_body_edit
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_per_hand_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_per_hand_help_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_per_hand_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_per_hand_off
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_per_hand_on
import io.github.lopezjavier.appfitness.composeapp.resources.movement_pattern_label
import io.github.lopezjavier.appfitness.composeapp.resources.my_training_unset
import io.github.lopezjavier.appfitness.designsystem.AppEditSheet
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppSwitch
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.OptionPickerSheet
import io.github.lopezjavier.appfitness.designsystem.PickerOption
import io.github.lopezjavier.appfitness.designsystem.SelectableCard
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import org.jetbrains.compose.resources.stringResource

/**
 * What the create and edit sheets render; the metric type is shown only on create ([metricType] null hides it, as it
 * cannot change once sets are logged). [tagPrefix] is `exercise_create` or `exercise_edit`; the sheet template adds
 * `_sheet` (so the commit button is `<tagPrefix>_sheet_commit`). [canSubmit] already accounts for being online and
 * [commitHint] is the reason shown under a button disabled offline.
 */
internal data class ExerciseFormUi(
    val title: String,
    val commit: SheetCommit,
    val tagPrefix: String,
    val name: String,
    val muscleGroup: MuscleGroup?,
    val equipment: Equipment?,
    val perHand: Boolean,
    val movementPattern: MovementPattern?,
    val metricType: ExerciseMetricType?,
    val canSubmit: Boolean,
    val isSubmitting: Boolean,
    val commitHint: String?,
    val isEdit: Boolean,
)

/** Everything the form can ask for; nothing is applied before [onSubmit] (explicit "Añadir" / "Guardar"). */
@Suppress("LongParameterList") // One callback per form event.
internal class ExerciseFormActions(
    val onName: (String) -> Unit,
    val onMuscleGroup: (MuscleGroup) -> Unit,
    val onEquipment: (Equipment) -> Unit,
    val onPerHand: (Boolean) -> Unit,
    val onMovementPattern: (MovementPattern?) -> Unit,
    val onMetricType: (ExerciseMetricType) -> Unit,
    val onSubmit: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * The sheet shared by "Nuevo ejercicio" and "Editar ejercicio" on [AppEditSheet]: name, muscle group and equipment
 * chips, the "pesas por mano" switch with its "?" help and optionally the metric type. The X, back, swipe-down and
 * scrim discard ([ExerciseFormActions.onDismiss]) without a dialog; a failed save is reported by the caller through
 * the app messages.
 */
@Composable
internal fun ExerciseFormSheet(ui: ExerciseFormUi, actions: ExerciseFormActions, modifier: Modifier = Modifier) {
    AppEditSheet(
        title = ui.title,
        commit = ui.commit,
        onCommit = actions.onSubmit,
        onDismiss = actions.onDismiss,
        modifier = modifier,
        commitEnabled = ui.canSubmit,
        saving = ui.isSubmitting,
        commitHint = ui.commitHint,
        testTagPrefix = "${ui.tagPrefix}_sheet",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            AppTextField(
                value = ui.name,
                onValueChange = actions.onName,
                label = stringResource(Res.string.exercise_create_name_label),
                testTagPrefix = "${ui.tagPrefix}_name",
            )
            FormChoices(ui, actions)
            MovementPatternField(ui, actions.onMovementPattern)
            PerHandRow(ui, actions.onPerHand)
        }
    }
}

/** The muscle group and equipment chip rows (and the metric type on create); nothing is pre-selected on create. */
@Composable
private fun FormChoices(ui: ExerciseFormUi, actions: ExerciseFormActions) {
    val muscleOptions =
        MuscleGroup.entries.map { AppOption(stringResource(it.label), "${ui.tagPrefix}_muscle_${it.tagName()}") }
    val equipmentOptions =
        Equipment.entries.map { AppOption(stringResource(it.label), "${ui.tagPrefix}_equipment_${it.tagName()}") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        ChipGroup(stringResource(Res.string.exercise_create_muscle_label)) {
            FilterChipRow(
                options = muscleOptions,
                selectedIndex = ui.muscleGroup?.ordinal,
                onSelect = { actions.onMuscleGroup(MuscleGroup.entries[it]) },
            )
        }
        ChipGroup(stringResource(Res.string.exercise_create_equipment_label)) {
            FilterChipRow(
                options = equipmentOptions,
                selectedIndex = ui.equipment?.ordinal,
                onSelect = { actions.onEquipment(Equipment.entries[it]) },
            )
        }
        if (ui.metricType != null) {
            ChipGroup(stringResource(Res.string.exercise_create_metric_label)) {
                MetricTypeChoices(ui.metricType, actions.onMetricType)
            }
        }
    }
}

/**
 * Optional "Patrón de movimiento": a [NavigationRow] with the current value that opens a picker sibling sheet. The
 * picker is a second modal over the form: closing it never discards the form, and a tap only updates the draft.
 */
@Composable
private fun MovementPatternField(ui: ExerciseFormUi, onChange: (MovementPattern?) -> Unit) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(Res.string.movement_pattern_label)
    val unset = stringResource(Res.string.my_training_unset)
    NavigationRow(
        label = label,
        value = ui.movementPattern?.let { stringResource(it.label) } ?: unset,
        onClick = { pickerOpen = true },
        modifier = Modifier.testTag("${ui.tagPrefix}_movement_pattern"),
    )
    if (pickerOpen) {
        val none = PickerOption("none", unset, ui.movementPattern == null) { onChange(null) }
        val options = listOf(none) + MovementPattern.entries.map {
            PickerOption(it.tagName(), stringResource(it.label), it == ui.movementPattern) { onChange(it) }
        }
        OptionPickerSheet(
            title = label,
            tagPrefix = "${ui.tagPrefix}_movement_pattern_picker",
            options = options,
            onClose = { pickerOpen = false },
        )
    }
}

/** "Se usa una pesa en cada mano": label, "?" help and the switch; each target is at least 48 dp. */
@Composable
private fun PerHandRow(ui: ExerciseFormUi, onChange: (Boolean) -> Unit) {
    val label = stringResource(Res.string.exercise_per_hand_label)
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = label,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        HelpButton(
            contentDescription = stringResource(Res.string.exercise_per_hand_help_cd),
            title = stringResource(Res.string.exercise_per_hand_help_title),
            body = stringResource(
                if (ui.isEdit) Res.string.exercise_per_hand_help_body_edit else Res.string.exercise_per_hand_help_body,
            ),
            testTag = "${ui.tagPrefix}_per_hand_help",
        )
        AppSwitch(
            checked = ui.perHand,
            onCheckedChange = onChange,
            contentDescription = label,
            stateDescription = stringResource(
                if (ui.perHand) Res.string.exercise_per_hand_on else Res.string.exercise_per_hand_off,
            ),
            modifier = Modifier.testTag("${ui.tagPrefix}_per_hand_switch"),
        )
    }
}

/** Single-choice list of what the exercise logs; "Peso y repeticiones" comes first and is the default. */
@Composable
private fun MetricTypeChoices(selected: ExerciseMetricType, onSelect: (ExerciseMetricType) -> Unit) {
    Column(
        Modifier.selectableGroup().testTag("create_exercise_metric_type_picker"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ExerciseMetricType.entries.forEach { type ->
            SelectableCard(
                title = stringResource(type.label),
                description = stringResource(type.description),
                selected = type == selected,
                onClick = { onSelect(type) },
                modifier = Modifier.testTag("create_exercise_metric_type_${type.tagName()}"),
            )
        }
    }
}

@Composable
private fun ChipGroup(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = label, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
        content()
    }
}
