package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.past_source_empty
import io.github.lopezjavier.appfitness.composeapp.resources.past_source_no_routines_message
import io.github.lopezjavier.appfitness.composeapp.resources.past_source_no_routines_title
import io.github.lopezjavier.appfitness.composeapp.resources.past_source_no_sessions_message
import io.github.lopezjavier.appfitness.composeapp.resources.past_source_no_sessions_title
import io.github.lopezjavier.appfitness.composeapp.resources.past_source_routine
import io.github.lopezjavier.appfitness.composeapp.resources.past_source_routines_error
import io.github.lopezjavier.appfitness.composeapp.resources.past_source_session
import io.github.lopezjavier.appfitness.composeapp.resources.past_source_sessions_error
import io.github.lopezjavier.appfitness.composeapp.resources.past_source_title
import io.github.lopezjavier.appfitness.composeapp.resources.routines_count
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.NavigationRow
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val TAG = "past_workout_source"
private val SkeletonHeight = Spacing.xl * 3

/**
 * The source chooser opened by "Añadir entreno" (US-37), an [AppPickerSheet] ("¿Desde dónde empiezas?") with "Desde
 * cero", "Desde una rutina" and "Desde un entreno anterior"; the last two swap the body for a list (the title follows).
 * System back returns from a list to the options, then closes; the X closes the whole sheet. Picking only starts the
 * form, which has its own "Guardar". [sessions] supplies the workout list and is read only while that step shows.
 */
@Composable
fun PastWorkoutChooserSheet(
    state: PastWorkoutChooserUiState,
    onEvent: (PastWorkoutChooserEvent) -> Unit,
    modifier: Modifier = Modifier,
    sessions: @Composable () -> HistoryUiState = { koinHistoryState() },
) {
    if (state.step == PastWorkoutChooserStep.Closed) return
    val title = stringResource(
        when (state.step) {
            PastWorkoutChooserStep.Routines -> Res.string.past_source_routine
            PastWorkoutChooserStep.Sessions -> Res.string.past_source_session
            else -> Res.string.past_source_title
        },
    )
    AppPickerSheet(
        title = title,
        onDismiss = { onEvent(PastWorkoutChooserEvent.Dismiss) },
        modifier = modifier,
        testTagPrefix = "${TAG}_sheet",
    ) {
        NavigationBackHandler(
            state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
            isBackEnabled = state.step == PastWorkoutChooserStep.Routines ||
                state.step == PastWorkoutChooserStep.Sessions,
            onBackCompleted = { onEvent(PastWorkoutChooserEvent.Back) },
        )
        when (state.step) {
            PastWorkoutChooserStep.Routines -> RoutineList(state.routines, onEvent)
            PastWorkoutChooserStep.Sessions -> SessionList(sessions(), onEvent)
            else -> Options(onEvent)
        }
    }
}

/** The recent workouts, from a [HistoryViewModel] created only when the step that lists them composes. */
@Composable
private fun koinHistoryState(): HistoryUiState {
    val viewModel: HistoryViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    return state
}

@Composable
private fun Options(onEvent: (PastWorkoutChooserEvent) -> Unit) {
    Column {
        NavigationRow(
            label = stringResource(Res.string.past_source_empty),
            onClick = { onEvent(PastWorkoutChooserEvent.ChooseEmpty) },
            modifier = Modifier.testTag("${TAG}_empty"),
        )
        NavigationRow(
            label = stringResource(Res.string.past_source_routine),
            onClick = { onEvent(PastWorkoutChooserEvent.ChooseRoutine) },
            modifier = Modifier.testTag("${TAG}_routine"),
        )
        NavigationRow(
            label = stringResource(Res.string.past_source_session),
            onClick = { onEvent(PastWorkoutChooserEvent.ChooseWorkout) },
            modifier = Modifier.testTag("${TAG}_session"),
        )
    }
}

@Composable
private fun RoutineList(routines: ChooserRoutines, onEvent: (PastWorkoutChooserEvent) -> Unit) {
    when (routines) {
        ChooserRoutines.Loading -> Skeleton("${TAG}_routines_loading")

        ChooserRoutines.Empty -> EmptyState(
            message = stringResource(Res.string.past_source_no_routines_message),
            title = stringResource(Res.string.past_source_no_routines_title),
            testTagPrefix = "${TAG}_routines_empty",
        )

        ChooserRoutines.Error -> ErrorState(
            message = stringResource(Res.string.past_source_routines_error),
            action = AppStateAction(stringResource(Res.string.action_retry)) {
                onEvent(PastWorkoutChooserEvent.RetryRoutines)
            },
            testTagPrefix = "${TAG}_routines_error",
        )

        is ChooserRoutines.Content -> Column {
            routines.routines.forEach { routine ->
                RoutineSourceRow(routine) { onEvent(PastWorkoutChooserEvent.PickRoutine(routine.id)) }
            }
        }
    }
}

@Composable
private fun RoutineSourceRow(routine: RoutineSummary, onClick: () -> Unit) {
    AppListRow(
        headline = routine.name,
        supporting = pluralStringResource(Res.plurals.routines_count, routine.exerciseCount, routine.exerciseCount),
        onClick = onClick,
        modifier = Modifier.testTag("${TAG}_routine_${routine.id}"),
    )
}

@Composable
private fun SessionList(history: HistoryUiState, onEvent: (PastWorkoutChooserEvent) -> Unit) {
    when (history) {
        HistoryUiState.Loading -> Skeleton("${TAG}_sessions_loading")

        HistoryUiState.Empty -> EmptyState(
            message = stringResource(Res.string.past_source_no_sessions_message),
            title = stringResource(Res.string.past_source_no_sessions_title),
            testTagPrefix = "${TAG}_sessions_empty",
        )

        HistoryUiState.Error -> Text(
            text = stringResource(Res.string.past_source_sessions_error),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("${TAG}_sessions_error"),
        )

        is HistoryUiState.Content -> Column {
            history.rows.forEach { row ->
                HistoryWorkoutRow(
                    row = row,
                    onClick = { onEvent(PastWorkoutChooserEvent.PickWorkout(row.id)) },
                    tag = "${TAG}_session_${row.id}",
                )
            }
        }
    }
}

@Composable
private fun Skeleton(tag: String) {
    Column(Modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        repeat(2) { SkeletonBox(Modifier.fillMaxWidth().height(SkeletonHeight), AppShapes.lg) }
    }
}
