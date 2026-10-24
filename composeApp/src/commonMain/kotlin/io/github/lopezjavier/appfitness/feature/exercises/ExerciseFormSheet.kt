package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppSwitch
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SelectableCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import org.jetbrains.compose.resources.stringResource

/**
 * What the create and edit sheets render; the metric type is shown only on create ([metricType] null hides it, as it
 * cannot change once sets are logged). [tagPrefix] is `exercise_create` or `exercise_edit`; [submitTag] is the tag of
 * the main button.
 */
internal data class ExerciseFormUi(
    val title: String,
    val submitLabel: String,
    val errorText: String,
    val tagPrefix: String,
    val submitTag: String,
    val name: String,
    val muscleGroup: MuscleGroup?,
    val equipment: Equipment?,
    val perHand: Boolean,
    val metricType: ExerciseMetricType?,
    val canSubmit: Boolean,
    val isSubmitting: Boolean,
    val hasError: Boolean,
    val isEdit: Boolean,
)

/** Everything the form can ask for; nothing is applied before [onSubmit] (explicit "Crear" / "Guardar"). */
@Suppress("LongParameterList") // One callback per form event.
internal class ExerciseFormActions(
    val onName: (String) -> Unit,
    val onMuscleGroup: (MuscleGroup) -> Unit,
    val onEquipment: (Equipment) -> Unit,
    val onPerHand: (Boolean) -> Unit,
    val onMetricType: (ExerciseMetricType) -> Unit,
    val onSubmit: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * The bottom sheet shared by "Nuevo ejercicio" and "Editar ejercicio": name, muscle group and equipment chips, the
 * "pesas por mano" switch with its "?" help, optionally the metric type, and the explicit submit button. The X, back,
 * swipe-down and scrim discard ([ExerciseFormActions.onDismiss]).
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
internal fun ExerciseFormSheet(
    ui: ExerciseFormUi,
    actions: ExerciseFormActions,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(
        onDismissRequest = actions.onDismiss,
        modifier = modifier,
        testTagPrefix = "${ui.tagPrefix}_sheet",
    ) {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Text(
                text = ui.title,
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            AppTextField(
                value = ui.name,
                onValueChange = actions.onName,
                label = stringResource(Res.string.exercise_create_name_label),
                testTagPrefix = "${ui.tagPrefix}_name",
            )
            FormChoices(ui, actions)
            PerHandRow(ui, actions.onPerHand)
            if (ui.hasError) {
                Text(
                    text = ui.errorText,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.error,
                    modifier = Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("${ui.tagPrefix}_error"),
                )
            }
            PrimaryButton(
                text = ui.submitLabel,
                onClick = actions.onSubmit,
                enabled = ui.canSubmit,
                loading = ui.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag(ui.submitTag),
            )
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
