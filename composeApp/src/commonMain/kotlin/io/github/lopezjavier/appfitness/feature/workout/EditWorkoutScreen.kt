package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.session_action_failed
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_delete_cd
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val TAG = "edit_workout"

/**
 * Stateful edit screen: connects [EditWorkoutViewModel], hands it the exercise the picker returned
 * ([selectedExerciseId], consumed once through [onConsumeSelection]) and routes its one-off results: [onClose] after a
 * save, [onDeleted] after a deletion (the details below the screen go too) and a snackbar when a call failed.
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
    val snackbarHost = remember { SnackbarHostState() }
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val currentConsumed by rememberUpdatedState(onConsumeSelection)
    LaunchedEffect(selectedExerciseId) {
        if (selectedExerciseId != null) {
            viewModel.onEvent(WorkoutFormEvent.AddExercise(selectedExerciseId))
            currentConsumed()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditWorkoutEffect.Saved -> currentOnClose()
                EditWorkoutEffect.Deleted -> currentOnDeleted()
                EditWorkoutEffect.ActionFailed -> snackbarHost.showSnackbar(getString(Res.string.session_action_failed))
            }
        }
    }
    EditWorkoutScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onFormEvent = viewModel::onEvent,
        onAddExercise = onAddExercise,
        modifier = modifier,
        snackbarHost = snackbarHost,
    )
}

/**
 * Edit screen of a finished workout (US-37): the shared form with the date as read-only text, "Eliminar" (immediate,
 * ADR-45) above it and "Guardar" pinned at the bottom. Leaving without saving writes nothing.
 */
@Composable
fun EditWorkoutScreen(
    state: EditWorkoutUiState,
    onEvent: (EditWorkoutEvent) -> Unit,
    onFormEvent: (WorkoutFormEvent) -> Unit,
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier.fillMaxSize().testTag("${TAG}_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            if (state is EditWorkoutUiState.Content) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AppIconButton(
                        contentDescription = stringResource(Res.string.session_detail_delete_cd),
                        icon = Res.drawable.ic_delete,
                        testTag = "${TAG}_delete",
                        onClick = { onEvent(EditWorkoutEvent.Delete) },
                        enabled = state.isOnline && !state.busy,
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    EditWorkoutUiState.Loading -> WorkoutFormLoading(TAG)

                    EditWorkoutUiState.Error -> WorkoutFormError(TAG) { onEvent(EditWorkoutEvent.Retry) }

                    is EditWorkoutUiState.Content -> WorkoutFormBody(state.view, TAG, onFormEvent, onAddExercise) {
                        FormDateField(state.view.form.date, "${TAG}_date")
                    }
                }
            }
            SnackbarHost(snackbarHost) { data ->
                Snackbar(Modifier.testTag("${TAG}_snackbar")) { Text(data.visuals.message) }
            }
            if (state is EditWorkoutUiState.Content) {
                WorkoutFormFooter(
                    // An invalid form stays tappable: Guardar then flags the fields that need a fix.
                    canSave = state.isOnline && !state.busy,
                    busy = state.busy,
                    isOnline = state.isOnline,
                    tagPrefix = TAG,
                    onSave = { onEvent(EditWorkoutEvent.Save) },
                )
            }
        }
    }
}
