package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.save_as_routine_item_label
import io.github.lopezjavier.appfitness.composeapp.resources.save_as_routine_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.save_as_routine_title
import io.github.lopezjavier.appfitness.composeapp.resources.save_as_routine_too_many_sets
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.NameSheet
import io.github.lopezjavier.appfitness.domain.usecase.SaveWorkoutAsRoutine
import org.jetbrains.compose.resources.stringResource

/**
 * "Guardar como rutina" (US-37) as a [NameSheet] (R-15): the routine name prefilled with the workout's, unique among
 * the active routines. Closing it discards the name. An exercise with more than 10 sets is explained under the field
 * and blocks "Guardar". A failed save is reported by the screen (message and Reject), the sheet stays open.
 */
@Composable
internal fun SaveAsRoutineSheet(workoutName: String, state: SaveAsRoutineState, onEvent: (SessionDetailEvent) -> Unit) {
    NameSheet(
        title = stringResource(Res.string.save_as_routine_title),
        label = stringResource(Res.string.save_as_routine_name_label),
        itemLabel = stringResource(Res.string.save_as_routine_item_label),
        existingNames = state.existingNames,
        onSave = { onEvent(SessionDetailEvent.ConfirmSaveAsRoutine(it)) },
        onDismiss = { onEvent(SessionDetailEvent.DismissSaveAsRoutine) },
        initialName = SaveWorkoutAsRoutine.defaultName(workoutName),
        saving = state.busy,
        maxLength = SaveWorkoutAsRoutine.NAME_MAX_LENGTH,
        canSubmit = !state.tooManySets,
        initialNameIsOwn = false,
        testTagPrefix = "save_as_routine_sheet",
        extra = if (state.tooManySets) {
            {
                Text(
                    text = stringResource(Res.string.save_as_routine_too_many_sets),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.error,
                    modifier = Modifier.testTag("save_as_routine_too_many_sets"),
                )
            }
        } else {
            null
        },
    )
}
