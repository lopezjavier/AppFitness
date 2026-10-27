package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_save
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_create_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_edit_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_offline_add_action
import io.github.lopezjavier.appfitness.designsystem.SheetCommit
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import org.jetbrains.compose.resources.stringResource

/**
 * Sheet that creates a custom exercise: name (prefilled from the search), muscle group and equipment as chip rows
 * with nothing pre-selected, what it logs, the "pesas por mano" switch (off by default) and "Añadir", enabled only
 * when name, muscle group and equipment are set and the device is online ([isOnline]).
 */
@Composable
fun CreateExerciseSheet(
    form: CreateExerciseForm,
    isOnline: Boolean,
    onEvent: (ExercisePickerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = ExerciseFormUi(
        title = stringResource(Res.string.exercise_create_title),
        commit = SheetCommit.Add,
        tagPrefix = "exercise_create",
        name = form.name,
        muscleGroup = form.muscleGroup,
        equipment = form.equipment,
        perHand = form.perHand,
        metricType = form.metricType,
        canSubmit = form.canSubmit && isOnline,
        isSubmitting = form.isSubmitting,
        commitHint = if (isOnline) null else offlineHint(stringResource(Res.string.exercise_offline_add_action)),
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
 * Sheet that edits a manual exercise (US-09): the same fields prefilled, without the metric type, and an explicit
 * "Guardar" (disabled offline with the hint). Closing the sheet discards the changes.
 */
@Composable
internal fun EditExerciseSheet(
    form: EditExerciseForm,
    isOnline: Boolean,
    actions: ExerciseFormActions,
    modifier: Modifier = Modifier,
) {
    val ui = ExerciseFormUi(
        title = stringResource(Res.string.exercise_edit_title),
        commit = SheetCommit.Save,
        tagPrefix = "exercise_edit",
        name = form.name,
        muscleGroup = form.muscleGroup,
        equipment = form.equipment,
        perHand = form.perHand,
        metricType = null,
        canSubmit = form.canSubmit && isOnline,
        isSubmitting = form.isSubmitting,
        commitHint = if (isOnline) null else offlineHint(stringResource(Res.string.action_save).lowercase()),
        isEdit = true,
    )
    ExerciseFormSheet(ui, actions, modifier)
}
