package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_error
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_submit
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_edit_error
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_edit_save
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_edit_title
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet that creates a custom exercise: name (prefilled from the search), muscle group and equipment as chip
 * rows with nothing pre-selected, what it logs, the "pesas por mano" switch (off by default) and "Crear", enabled
 * only when name, muscle group and equipment are set.
 */
@Composable
fun CreateExerciseSheet(
    form: CreateExerciseForm,
    onEvent: (ExercisePickerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = ExerciseFormUi(
        title = stringResource(Res.string.exercise_create_title),
        submitLabel = stringResource(Res.string.exercise_create_submit),
        errorText = stringResource(Res.string.exercise_create_error),
        tagPrefix = "exercise_create",
        submitTag = "exercise_create_submit",
        name = form.name,
        muscleGroup = form.muscleGroup,
        equipment = form.equipment,
        perHand = form.perHand,
        metricType = form.metricType,
        canSubmit = form.canSubmit,
        isSubmitting = form.isSubmitting,
        hasError = form.hasError,
        isEdit = false,
    )
    val actions = ExerciseFormActions(
        onName = { onEvent(ExercisePickerEvent.SetCreateName(it)) },
        onMuscleGroup = { onEvent(ExercisePickerEvent.SelectCreateMuscleGroup(it)) },
        onEquipment = { onEvent(ExercisePickerEvent.SelectCreateEquipment(it)) },
        onPerHand = { onEvent(ExercisePickerEvent.SetCreatePerHand(it)) },
        onMetricType = { onEvent(ExercisePickerEvent.SelectCreateMetricType(it)) },
        onSubmit = { onEvent(ExercisePickerEvent.SubmitCreate) },
        onDismiss = { onEvent(ExercisePickerEvent.DismissCreate) },
    )
    ExerciseFormSheet(ui, actions, modifier)
}

/**
 * Bottom sheet that edits a manual exercise (US-09): the same fields prefilled, without the metric type, and an
 * explicit "Guardar". Closing the sheet discards the changes.
 */
@Composable
internal fun EditExerciseSheet(form: EditExerciseForm, actions: ExerciseFormActions, modifier: Modifier = Modifier) {
    val ui = ExerciseFormUi(
        title = stringResource(Res.string.exercise_edit_title),
        submitLabel = stringResource(Res.string.exercise_edit_save),
        errorText = stringResource(Res.string.exercise_edit_error),
        tagPrefix = "exercise_edit",
        submitTag = "exercise_edit_save",
        name = form.name,
        muscleGroup = form.muscleGroup,
        equipment = form.equipment,
        perHand = form.perHand,
        metricType = null,
        canSubmit = form.canSubmit,
        isSubmitting = form.isSubmitting,
        hasError = form.hasError,
        isEdit = true,
    )
    ExerciseFormSheet(ui, actions, modifier)
}
