package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.live_object_exercise
import io.github.lopezjavier.appfitness.composeapp.resources.live_paused_label
import io.github.lopezjavier.appfitness.composeapp.resources.live_progress_summary
import io.github.lopezjavier.appfitness.composeapp.resources.live_title
import io.github.lopezjavier.appfitness.composeapp.resources.px_live_sets_progress
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppLinearProgress
import io.github.lopezjavier.appfitness.designsystem.AppLinearProgressDefaults
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LiveStatusStrip
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import org.jetbrains.compose.resources.stringResource

/** Phone and narrow layout (and wide-window layout) of the active live session. */
@Composable
internal fun NarrowLive(
    state: LiveSessionUiState.Active,
    onEvent: (LiveSessionEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    optionSaveFailed: Boolean,
    elapsedSeconds: Long,
) {
    Column(Modifier.fillMaxSize()) {
        val sets = state.exercises.flatMap { it.sets }
        val done = sets.count { it.completed }
        LiveStrip(state, elapsedSeconds)
        // Full-width session progress under the top bar (C-69); the wide layout has no bar, only the status row.
        AppLinearProgress(
            fraction = if (sets.isEmpty()) 0f else done.toFloat() / sets.size,
            stateDescription = stringResource(Res.string.live_progress_summary, done, sets.size),
            modifier = Modifier.testTag("live_progress_bar"),
            fillColor = AppTheme.colors.neonLime,
            barHeight = AppLinearProgressDefaults.SlimBarHeight,
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().imePadding(),
            contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            exerciseItems(state, onEvent, onOpenExercise, optionSaveFailed)
            item(key = "add_exercise") { AddExerciseButton(onEvent) }
            if (state.allExercisesComplete) {
                item(key = "finish_session") { FinishButton(state, onEvent) }
            }
        }
    }
}

/** The one status row of the session: the EN VIVO / EN PAUSA pill, the stopwatch and the sets figure. */
@Composable
private fun LiveStrip(state: LiveSessionUiState.Active, elapsedSeconds: Long) {
    val sets = state.exercises.flatMap { it.sets }
    LiveStatusStrip(
        statusText = stringResource(if (state.isPaused) Res.string.live_paused_label else Res.string.live_title),
        progressText = stringResource(Res.string.px_live_sets_progress, sets.count { it.completed }, sets.size),
        paused = state.isPaused,
        modifier = Modifier.testTag("live_status_strip"),
        elapsedText = formatElapsed(elapsedSeconds),
        elapsedSemantics = liveTimerSemantics(elapsedSeconds, state.isPaused),
    )
}

/** Wide window: the status row on top; progress and session actions on the left, the exercise list on the right. */
@Composable
internal fun WideLive(
    state: LiveSessionUiState.Active,
    onEvent: (LiveSessionEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    optionSaveFailed: Boolean,
    elapsedSeconds: Long,
) {
    Column(Modifier.fillMaxSize()) {
        LiveStrip(state, elapsedSeconds)
        WideLiveColumns(state, onEvent, onOpenExercise, optionSaveFailed, Modifier.weight(1f))
    }
}

@Composable
private fun WideLiveColumns(
    state: LiveSessionUiState.Active,
    onEvent: (LiveSessionEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    optionSaveFailed: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().testTag("live_session_two_column")) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            AddExerciseButton(onEvent)
            if (state.allExercisesComplete) FinishButton(state, onEvent)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight().imePadding(),
            contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) { exerciseItems(state, onEvent, onOpenExercise, optionSaveFailed) }
    }
}

private fun LazyListScope.exerciseItems(
    state: LiveSessionUiState.Active,
    onEvent: (LiveSessionEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    optionSaveFailed: Boolean,
) {
    // The next set to do is the first pending one of the session; its check circle gets the ring (C-67).
    val current = state.exercises.firstNotNullOfOrNull { exercise ->
        exercise.sets.firstOrNull { !it.completed }?.let { exercise.position to it.setIndex }
    }
    items(state.exercises, key = { it.position }) {
        LiveExerciseCard(
            it,
            onEvent,
            onOpenExercise,
            options = state.volumeOptionsFor(optionSaveFailed),
            deleteEnabled = !state.busy,
            online = state.isOnline,
            discomfortSaving = state.discomfortSaving,
            discomfortLogs = state.discomfortLogs,
            currentSetIndex = current?.takeIf { (position, _) -> position == it.position }?.second,
            modifier = appAnimateItem(),
        )
    }
}

@Composable
private fun AddExerciseButton(onEvent: (LiveSessionEvent) -> Unit) {
    AddItemButton(
        objectLabel = stringResource(Res.string.live_object_exercise),
        onClick = { onEvent(LiveSessionEvent.AddExercise) },
        modifier = Modifier.padding(top = Spacing.blockGap - Spacing.cardGap).testTag("live_add_exercise"),
    )
}
