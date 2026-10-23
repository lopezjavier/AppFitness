package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_equipment_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_error
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_metric_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_muscle_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_submit
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_title
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SelectableCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet that creates a custom exercise: name (prefilled from the search), muscle group and equipment as chip
 * rows with nothing pre-selected, and "Crear", enabled only when all three are set.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
fun CreateExerciseSheet(
    form: CreateExerciseForm,
    onEvent: (ExercisePickerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(
        onDismissRequest = { onEvent(ExercisePickerEvent.DismissCreate) },
        modifier = modifier,
        testTagPrefix = "exercise_create_sheet",
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Text(
                text = stringResource(Res.string.exercise_create_title),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
            )
            AppTextField(
                value = form.name,
                onValueChange = { onEvent(ExercisePickerEvent.SetCreateName(it)) },
                label = stringResource(Res.string.exercise_create_name_label),
                testTagPrefix = "exercise_create_name",
            )
            CreateChoices(form, onEvent)
            if (form.hasError) {
                Text(
                    text = stringResource(Res.string.exercise_create_error),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.error,
                    modifier =
                    Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("exercise_create_error"),
                )
            }
            PrimaryButton(
                text = stringResource(Res.string.exercise_create_submit),
                onClick = { onEvent(ExercisePickerEvent.SubmitCreate) },
                enabled = form.canSubmit,
                loading = form.isSubmitting,
                modifier = Modifier.fillMaxWidth().testTag("exercise_create_submit"),
            )
        }
    }
}

/** The muscle group and equipment chip rows; nothing is selected until the user picks. */
@Composable
private fun CreateChoices(form: CreateExerciseForm, onEvent: (ExercisePickerEvent) -> Unit) {
    val muscleOptions =
        MuscleGroup.entries.map { AppOption(stringResource(it.label), "exercise_create_muscle_${it.tagName()}") }
    val equipmentOptions =
        Equipment.entries.map { AppOption(stringResource(it.label), "exercise_create_equipment_${it.tagName()}") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        ChipGroup(stringResource(Res.string.exercise_create_muscle_label)) {
            FilterChipRow(
                options = muscleOptions,
                selectedIndex = form.muscleGroup?.ordinal,
                onSelect = { onEvent(ExercisePickerEvent.SelectCreateMuscleGroup(MuscleGroup.entries[it])) },
            )
        }
        ChipGroup(stringResource(Res.string.exercise_create_equipment_label)) {
            FilterChipRow(
                options = equipmentOptions,
                selectedIndex = form.equipment?.ordinal,
                onSelect = { onEvent(ExercisePickerEvent.SelectCreateEquipment(Equipment.entries[it])) },
            )
        }
        ChipGroup(stringResource(Res.string.exercise_create_metric_label)) {
            MetricTypeChoices(form.metricType, onSelect = { onEvent(ExercisePickerEvent.SelectCreateMetricType(it)) })
        }
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
