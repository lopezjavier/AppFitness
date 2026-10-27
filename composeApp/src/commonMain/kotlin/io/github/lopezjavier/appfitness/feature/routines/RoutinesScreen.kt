package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.delete_offline
import io.github.lopezjavier.appfitness.composeapp.resources.routines_action_failed
import io.github.lopezjavier.appfitness.composeapp.resources.routines_empty
import io.github.lopezjavier.appfitness.composeapp.resources.routines_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.routines_error
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.workout_already_in_progress
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_ROWS = 4
private val SkeletonRowHeight = Spacing.xl * 3

/**
 * Stateful routines list: connects [RoutinesViewModel], reloads when the screen comes back (for example after the
 * builder saved) and routes its one-off results: [onOpenBuilder] for new/edit, [onOpenDetails] for a card tap,
 * [onOpenLiveSession] once a session was started, and a message plus a Reject haptic for refusals and failures.
 */
@Composable
fun RoutinesRoot(
    onOpenBuilder: (routineId: String?) -> Unit,
    onOpenDetails: (routineId: String) -> Unit,
    onOpenLiveSession: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutinesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
    val currentOpenBuilder by rememberUpdatedState(onOpenBuilder)
    val currentOpenDetails by rememberUpdatedState(onOpenDetails)
    val currentOpenLive by rememberUpdatedState(onOpenLiveSession)
    val failed = stringResource(Res.string.routines_action_failed)
    val deleteOffline = stringResource(Res.string.delete_offline)
    val inProgress = stringResource(Res.string.workout_already_in_progress)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RoutinesEffect.OpenBuilder -> currentOpenBuilder(effect.routineId)

                is RoutinesEffect.OpenDetails -> currentOpenDetails(effect.routineId)

                RoutinesEffect.OpenLiveSession -> currentOpenLive()

                RoutinesEffect.ActionFailed -> {
                    haptics.reject()
                    messages.show(failed)
                }

                RoutinesEffect.StartFailed -> {
                    haptics.reject()
                    messages.show(failed)
                }

                RoutinesEffect.OfflineRefused -> {
                    haptics.reject()
                    messages.show(deleteOffline)
                }

                RoutinesEffect.SessionInProgress -> {
                    haptics.reject()
                    messages.show(inProgress)
                }
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(RoutinesEvent.Refresh) }
    RoutinesScreen(state, viewModel::onEvent, modifier)
}

/**
 * The routines as swipeable cards (R-20): tap opens the details, ✎ the builder, ⋮ the actions sheet, "Empezar"
 * starts a session; "Añadir rutina" and "Añadir carpeta" close the list (R-19). Body only: the title, bar and
 * messages belong to the Entreno tab. Loading shows skeleton rows, an empty list offers "Añadir rutina" in place and
 * a failed load offers "Reintentar".
 */
@Composable
fun RoutinesScreen(state: RoutinesUiState, onEvent: (RoutinesEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().testTag("routines_screen")) {
        when (state) {
            RoutinesUiState.Loading -> RoutinesLoading()
            RoutinesUiState.Error -> RoutinesError { onEvent(RoutinesEvent.Retry) }
            is RoutinesUiState.Content -> RoutinesContent(state, onEvent)
        }
    }
    (state as? RoutinesUiState.Content)?.let { RoutinesDialogs(it, onEvent) }
}

@Composable
private fun RoutinesContent(state: RoutinesUiState.Content, onEvent: (RoutinesEvent) -> Unit) {
    if (state.isEmpty) {
        Column(
            Modifier.fillMaxSize().padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                message = stringResource(Res.string.routines_empty),
                title = stringResource(Res.string.routines_empty_title),
                testTagPrefix = "routines_empty",
            )
            AddItemButton(
                objectLabel = "rutina",
                onClick = { onEvent(RoutinesEvent.NewRoutine) },
                modifier = Modifier.testTag("routines_empty_add"),
                enabled = state.isOnline,
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("routines_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        routineGroups(state, onEvent)
        item(key = "add_routine") {
            AddItemButton(
                objectLabel = "rutina",
                onClick = { onEvent(RoutinesEvent.NewRoutine) },
                modifier = Modifier.testTag("routines_add_routine"),
                enabled = state.isOnline,
            )
        }
        item(key = "add_folder") {
            AddItemButton(
                objectLabel = "carpeta",
                onClick = { onEvent(RoutinesEvent.NewFolder) },
                modifier = Modifier.testTag("routines_add_folder"),
                enabled = state.isOnline,
            )
        }
        if (!state.isOnline) item(key = "offline_hint") { OfflineHint(action = "empezar") }
    }
}

@Composable
private fun RoutinesLoading() {
    Column(
        Modifier.fillMaxSize().padding(
            horizontal = Spacing.screenMargin,
            vertical = Spacing.lg,
        ).testTag("routines_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(SkeletonRowHeight), AppShapes.lg) }
    }
}

@Composable
private fun RoutinesError(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ErrorState(
            message = stringResource(Res.string.routines_error),
            action = AppStateAction(stringResource(Res.string.state_retry), onRetry),
            testTagPrefix = "routines_error",
        )
    }
}
