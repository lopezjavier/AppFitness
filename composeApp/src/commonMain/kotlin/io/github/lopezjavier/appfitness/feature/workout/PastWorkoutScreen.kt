package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.date_picker_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.date_picker_dismiss
import io.github.lopezjavier.appfitness.composeapp.resources.session_action_failed
import io.github.lopezjavier.appfitness.designsystem.AppDatePickerDialog
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val TAG = "past_workout"

/**
 * Stateful past-workout form: connects [PastWorkoutViewModel] for the source ([routineId] / [sessionId], both null = an
 * empty workout), hands it the exercise the picker returned and routes its results: [onSaved] once the workout was
 * created (with its id) and a snackbar when saving failed.
 */
@Composable
@Suppress("LongParameterList", "ParameterNaming") // Navigation hooks of the screen (onSaved reports a result).
fun PastWorkoutRoot(
    routineId: String?,
    sessionId: String?,
    selectedExerciseId: String?,
    onConsumeSelection: () -> Unit,
    onAddExercise: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PastWorkoutViewModel = koinViewModel(
        key = "past_${routineId}_$sessionId",
        parameters = { parametersOf(routineId.orEmpty(), sessionId.orEmpty()) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val currentOnSaved by rememberUpdatedState(onSaved)
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
                is PastWorkoutEffect.Saved -> currentOnSaved(effect.sessionId)
                PastWorkoutEffect.ActionFailed -> snackbarHost.showSnackbar(getString(Res.string.session_action_failed))
            }
        }
    }
    PastWorkoutScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onFormEvent = viewModel::onEvent,
        onAddExercise = onAddExercise,
        modifier = modifier,
        snackbarHost = snackbarHost,
    )
}

/**
 * Form to log a workout of a past date (US-37): the shared form with the date field (opens the date dialog, never a
 * future day) and "Guardar" pinned at the bottom. Leaving without saving writes nothing.
 */
@Composable
fun PastWorkoutScreen(
    state: PastWorkoutUiState,
    onEvent: (PastWorkoutEvent) -> Unit,
    onFormEvent: (WorkoutFormEvent) -> Unit,
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
) {
    // The open dialog survives recreation (ADR-40); the picked day is only applied on "Aceptar".
    var dateDialogOpen by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize().testTag("${TAG}_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    PastWorkoutUiState.Loading -> WorkoutFormLoading(TAG)

                    PastWorkoutUiState.Error -> WorkoutFormError(TAG) { onEvent(PastWorkoutEvent.Retry) }

                    is PastWorkoutUiState.Content -> WorkoutFormBody(state.view, TAG, onFormEvent, onAddExercise) {
                        FormDateField(state.view.form.date, "${TAG}_date") { dateDialogOpen = true }
                    }
                }
            }
            SnackbarHost(snackbarHost) { data ->
                Snackbar(Modifier.testTag("${TAG}_snackbar")) { Text(data.visuals.message) }
            }
            if (state is PastWorkoutUiState.Content) {
                WorkoutFormFooter(
                    canSave = state.isOnline && !state.busy,
                    busy = state.busy,
                    isOnline = state.isOnline,
                    tagPrefix = TAG,
                    onSave = { onEvent(PastWorkoutEvent.Save) },
                )
            }
        }
    }
    if (dateDialogOpen && state is PastWorkoutUiState.Content) {
        AppDatePickerDialog(
            selected = state.view.form.date,
            onConfirm = {
                onEvent(PastWorkoutEvent.DateChanged(it))
                dateDialogOpen = false
            },
            onDismiss = { dateDialogOpen = false },
            confirmLabel = stringResource(Res.string.date_picker_confirm),
            dismissLabel = stringResource(Res.string.date_picker_dismiss),
            maxDate = state.today,
            testTagPrefix = "${TAG}_date_dialog",
        )
    }
}
