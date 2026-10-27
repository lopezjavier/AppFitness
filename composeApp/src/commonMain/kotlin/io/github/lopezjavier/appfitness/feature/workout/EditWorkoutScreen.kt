package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.edit_workout_title
import io.github.lopezjavier.appfitness.composeapp.resources.session_action_failed
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_delete_cd
import io.github.lopezjavier.appfitness.designsystem.AppEditorScreen
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.EditorDestructiveAction
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val TAG = "edit_workout"

/**
 * Stateful edit screen: connects [EditWorkoutViewModel], hands it the exercise the picker returned
 * ([selectedExerciseId], consumed once through [onConsumeSelection]) and routes its one-off results: the template
 * calls [onClose] after a save (or a confirmed discard), [onDeleted] after a deletion (the details below the screen go
 * too) and a failed call shows a message with one Reject.
 */
@Composable
@Suppress("LongParameterList", "ParameterNaming") // Navigation hooks of the screen (onDeleted reports a result).
fun EditWorkoutRoot(
    sessionId: String,
    selectedExerciseId: String?,
    onConsumeSelection: () -> Unit,
    onAddExercise: () -> Unit,
    onClose: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditWorkoutViewModel = koinViewModel(key = "edit_$sessionId", parameters = { parametersOf(sessionId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val currentConsumed by rememberUpdatedState(onConsumeSelection)
    val failed = stringResource(Res.string.session_action_failed)
    LaunchedEffect(selectedExerciseId) {
        if (selectedExerciseId != null) {
            viewModel.onEvent(WorkoutFormEvent.AddExercise(selectedExerciseId))
            currentConsumed()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // The template closes the screen once `saved` is true.
                EditWorkoutEffect.Saved -> Unit

                EditWorkoutEffect.Deleted -> currentOnDeleted()

                EditWorkoutEffect.ActionFailed -> {
                    haptics.reject()
                    messages.show(failed)
                }
            }
        }
    }
    EditWorkoutScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onFormEvent = viewModel::onEvent,
        onAddExercise = onAddExercise,
        onClose = onClose,
        modifier = modifier,
    )
}

/**
 * Edit screen of a finished workout (US-37) on [AppEditorScreen]: the shared form with the date as read-only text,
 * "Guardar" pinned at the bottom (only while the form changed and is valid) and "Eliminar" (immediate, ADR-45) under
 * it. Leaving with changes asks to discard them; leaving without saving writes nothing.
 */
@Composable
@Suppress("LongParameterList") // Screen state, its two event channels and the navigation hooks.
fun EditWorkoutScreen(
    state: EditWorkoutUiState,
    onEvent: (EditWorkoutEvent) -> Unit,
    onFormEvent: (WorkoutFormEvent) -> Unit,
    onAddExercise: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(Res.string.edit_workout_title)
    if (state !is EditWorkoutUiState.Content) {
        AppScreen(title = title, modifier = modifier, onBack = onClose, testTagPrefix = TAG) {
            Box(Modifier.fillMaxSize().testTag("${TAG}_screen")) {
                if (state == EditWorkoutUiState.Error) {
                    WorkoutFormError(TAG) { onEvent(EditWorkoutEvent.Retry) }
                } else {
                    WorkoutFormLoading(TAG)
                }
            }
        }
        return
    }
    AppEditorScreen(
        title = title,
        isDirty = state.isDirty,
        isSaving = state.busy,
        isOnline = state.isOnline,
        onSave = { onEvent(EditWorkoutEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        saved = state.saved,
        canSave = state.view.issues.isEmpty(),
        destructiveAction = EditorDestructiveAction(
            label = stringResource(Res.string.session_detail_delete_cd),
            enabled = state.isOnline && !state.busy,
            onClick = { onEvent(EditWorkoutEvent.Delete) },
        ),
        testTagPrefix = TAG,
    ) {
        WorkoutFormBody(
            state.view,
            TAG,
            onFormEvent,
            onAddExercise,
            showIssues = state.isDirty,
            modifier = Modifier.testTag("${TAG}_screen"),
        ) {
            FormDateField(state.view.form.date, "${TAG}_date")
        }
    }
}
