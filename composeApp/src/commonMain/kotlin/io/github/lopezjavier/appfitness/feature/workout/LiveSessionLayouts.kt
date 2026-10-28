package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.live_object_exercise
import io.github.lopezjavier.appfitness.composeapp.resources.live_progress_summary
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
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
) {
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
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

/** Wide window: progress and the session actions on the left, the exercise list on the right. */
@Composable
internal fun WideLive(
    state: LiveSessionUiState.Active,
    onEvent: (LiveSessionEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    optionSaveFailed: Boolean,
) {
    Row(Modifier.fillMaxSize().testTag("live_session_two_column")) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            val sets = state.exercises.flatMap { it.sets }
            Text(
                text = stringResource(Res.string.live_progress_summary, sets.count { it.completed }, sets.size),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.testTag("live_progress_summary"),
            )
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
