package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_back
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.past_source_back_cd
import io.github.lopezjavier.appfitness.composeapp.resources.past_source_close_cd
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
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val TAG = "past_workout_source"
private val SkeletonHeight = Spacing.xl * 3

/**
 * The source chooser opened by the history's "+" (US-37): one sheet with "Desde cero", "Desde una rutina" and "Desde un
 * entreno anterior"; the last two open a list in the same sheet (back arrow returns to the options). Picking only
 * starts the form; the form has its own "Guardar". [history] feeds the workout list with the history's own rows.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
fun PastWorkoutChooserSheet(
    state: PastWorkoutChooserUiState,
    history: HistoryUiState,
    onEvent: (PastWorkoutChooserEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.step == PastWorkoutChooserStep.Closed) return
    AppBottomSheet(
        onDismissRequest = { onEvent(PastWorkoutChooserEvent.Dismiss) },
        modifier = modifier,
        testTagPrefix = "${TAG}_sheet",
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ChooserHeader(state.step, onEvent)
            when (state.step) {
                PastWorkoutChooserStep.Routines -> RoutineList(state.routines, onEvent)
                PastWorkoutChooserStep.Sessions -> SessionList(history, onEvent)
                else -> Options(onEvent)
            }
        }
    }
}

@Composable
private fun ChooserHeader(step: PastWorkoutChooserStep, onEvent: (PastWorkoutChooserEvent) -> Unit) {
    val picker = step == PastWorkoutChooserStep.Routines || step == PastWorkoutChooserStep.Sessions
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (picker) {
            AppIconButton(
                contentDescription = stringResource(Res.string.past_source_back_cd),
                icon = Res.drawable.ic_arrow_back,
                testTag = "${TAG}_back",
                onClick = { onEvent(PastWorkoutChooserEvent.Back) },
            )
        }
        Text(
            text = stringResource(
                when (step) {
                    PastWorkoutChooserStep.Routines -> Res.string.past_source_routine
                    PastWorkoutChooserStep.Sessions -> Res.string.past_source_session
                    else -> Res.string.past_source_title
                },
            ),
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.past_source_close_cd),
            icon = Res.drawable.ic_close,
            testTag = "${TAG}_close",
            onClick = { onEvent(PastWorkoutChooserEvent.Dismiss) },
        )
    }
}

@Composable
private fun Options(onEvent: (PastWorkoutChooserEvent) -> Unit) {
    Column {
        OptionRow(stringResource(Res.string.past_source_empty), "${TAG}_empty") {
            onEvent(PastWorkoutChooserEvent.ChooseEmpty)
        }
        OptionRow(stringResource(Res.string.past_source_routine), "${TAG}_routine") {
            onEvent(PastWorkoutChooserEvent.ChooseRoutine)
        }
        OptionRow(stringResource(Res.string.past_source_session), "${TAG}_session") {
            onEvent(PastWorkoutChooserEvent.ChooseWorkout)
        }
    }
}

@Composable
private fun OptionRow(label: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = AppTheme.typography.bodyLarge, color = AppTheme.colors.textPrimary)
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
            action = AppStateAction(stringResource(Res.string.state_retry)) {
                onEvent(PastWorkoutChooserEvent.RetryRoutines)
            },
            testTagPrefix = "${TAG}_routines_error",
        )

        is ChooserRoutines.Content -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            routines.routines.forEach { routine ->
                RoutineSourceRow(routine) { onEvent(PastWorkoutChooserEvent.PickRoutine(routine.id)) }
            }
        }
    }
}

@Composable
private fun RoutineSourceRow(routine: RoutineSummary, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .testTag("${TAG}_routine_${routine.id}"),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = routine.name,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(Res.plurals.routines_count, routine.exerciseCount, routine.exerciseCount),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
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

        is HistoryUiState.Content -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            history.rows.forEach { row ->
                HistoryRowCard(
                    row,
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
