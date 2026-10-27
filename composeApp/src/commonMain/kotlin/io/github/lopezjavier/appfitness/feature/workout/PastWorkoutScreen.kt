package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.date_picker_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.date_picker_dismiss
import io.github.lopezjavier.appfitness.composeapp.resources.past_workout_title
import io.github.lopezjavier.appfitness.composeapp.resources.session_action_failed
import io.github.lopezjavier.appfitness.designsystem.AppDatePickerDialog
import io.github.lopezjavier.appfitness.designsystem.AppEditorScreen
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val TAG = "past_workout"

/**
 * Stateful past-workout form: connects [PastWorkoutViewModel] for the source ([routineId] / [sessionId], both null = an
 * empty workout) and the [date] it opens on, hands it the exercise the picker returned and routes its results: the
 * template calls [onClose] once the workout was created (or a discard was confirmed) and a failed save shows a message
 * with one Reject.
 */
@Composable
@Suppress("LongParameterList") // Navigation hooks of the screen.
fun PastWorkoutRoot(
    routineId: String?,
    sessionId: String?,
    selectedExerciseId: String?,
    onConsumeSelection: () -> Unit,
    onAddExercise: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    date: LocalDate? = null,
    viewModel: PastWorkoutViewModel = koinViewModel(
        key = "past_${routineId}_${sessionId}_$date",
        parameters = { parametersOf(routineId.orEmpty(), sessionId.orEmpty(), date?.toString().orEmpty()) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
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
                is PastWorkoutEffect.Saved -> Unit

                PastWorkoutEffect.ActionFailed -> {
                    haptics.reject()
                    messages.show(failed)
                }
            }
        }
    }
    PastWorkoutScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onFormEvent = viewModel::onEvent,
        onAddExercise = onAddExercise,
        onClose = onClose,
        modifier = modifier,
    )
}

/**
 * Form to log a workout of a past date (US-37) on [AppEditorScreen]: the shared form with the date field (opens the
 * date dialog, never a future day) and "Guardar" pinned at the bottom (only while the form changed and is valid).
 * Leaving with changes asks to discard them; leaving without saving writes nothing.
 */
@Composable
fun PastWorkoutScreen(
    state: PastWorkoutUiState,
    onEvent: (PastWorkoutEvent) -> Unit,
    onFormEvent: (WorkoutFormEvent) -> Unit,
    onAddExercise: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The open dialog survives recreation (ADR-40); the picked day is only applied on "Aceptar".
    var dateDialogOpen by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(Res.string.past_workout_title)
    if (state !is PastWorkoutUiState.Content) {
        AppScreen(title = title, modifier = modifier, onBack = onClose, testTagPrefix = TAG) {
            Box(Modifier.fillMaxSize().testTag("${TAG}_screen")) {
                if (state == PastWorkoutUiState.Error) {
                    WorkoutFormError(TAG) { onEvent(PastWorkoutEvent.Retry) }
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
        onSave = { onEvent(PastWorkoutEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        saved = state.saved,
        // Logging a workout from a routine or an earlier one is valid as it opens: no edit is needed to save it.
        saveEnabledWhenClean = true,
        canSave = state.view.issues.isEmpty(),
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
            FormDateField(state.view.form.date, "${TAG}_date") { dateDialogOpen = true }
        }
    }
    if (dateDialogOpen) {
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
