package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.routines_action_failed
import io.github.lopezjavier.appfitness.composeapp.resources.routines_empty
import io.github.lopezjavier.appfitness.composeapp.resources.routines_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.routines_error
import io.github.lopezjavier.appfitness.composeapp.resources.routines_new
import io.github.lopezjavier.appfitness.composeapp.resources.routines_new_folder
import io.github.lopezjavier.appfitness.composeapp.resources.routines_start_offline_reason
import io.github.lopezjavier.appfitness.composeapp.resources.routines_title
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.workout_already_in_progress
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_ROWS = 4
private val SkeletonRowHeight = Spacing.xl * 3

/**
 * Stateful "Mis rutinas": connects [RoutinesViewModel], reloads when the screen comes back (for example after the
 * builder saved) and routes its one-off results: [onOpenBuilder] for new/edit, [onOpenLiveSession] once a session was
 * started and a snackbar for a failed action.
 */
@Composable
fun RoutinesRoot(
    onOpenBuilder: (routineId: String?) -> Unit,
    onOpenLiveSession: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutinesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val currentOpenBuilder by rememberUpdatedState(onOpenBuilder)
    val currentOpenLive by rememberUpdatedState(onOpenLiveSession)
    val failed = stringResource(Res.string.routines_action_failed)
    val inProgress = stringResource(Res.string.workout_already_in_progress)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RoutinesEffect.OpenBuilder -> currentOpenBuilder(effect.routineId)
                RoutinesEffect.ActionFailed, RoutinesEffect.StartFailed -> snackbarHost.showSnackbar(failed)
                RoutinesEffect.SessionInProgress -> snackbarHost.showSnackbar(inProgress)
                RoutinesEffect.OpenLiveSession -> currentOpenLive()
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(RoutinesEvent.Refresh) }
    RoutinesScreen(state, viewModel::onEvent, modifier, snackbarHost)
}

/**
 * "Mis rutinas": the routines as cards (name, exercise count, a menu with Editar / Duplicar / Archivar) and
 * "Nueva rutina" pinned at the bottom, and "Empezar" on every card. Loading shows
 * skeleton rows, an empty list offers "Nueva rutina" in place and a failed load offers "Reintentar".
 */
@Composable
fun RoutinesScreen(
    state: RoutinesUiState,
    onEvent: (RoutinesEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier.fillMaxSize().testTag("routines_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    RoutinesUiState.Loading -> RoutinesLoading()
                    RoutinesUiState.Error -> RoutinesError { onEvent(RoutinesEvent.Retry) }
                    is RoutinesUiState.Content -> RoutinesContent(state, onEvent)
                }
            }
            SnackbarHost(snackbarHost) { data -> Snackbar { Text(data.visuals.message) } }
            if (state is RoutinesUiState.Content) RoutinesBottomActions(state, onEvent)
        }
    }
    (state as? RoutinesUiState.Content)?.let { RoutinesDialogs(it, onEvent) }
}

/** "Nueva rutina" (once there are routines) and the offline reason for starting one. */
@Composable
private fun RoutinesBottomActions(state: RoutinesUiState.Content, onEvent: (RoutinesEvent) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (!state.isEmpty) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PrimaryButton(
                    text = stringResource(Res.string.routines_new),
                    onClick = { onEvent(RoutinesEvent.NewRoutine) },
                    modifier = Modifier.weight(1f).testTag("routines_new_button"),
                    enabled = state.isOnline,
                )
                SecondaryButton(
                    text = stringResource(Res.string.routines_new_folder),
                    onClick = { onEvent(RoutinesEvent.NewFolder) },
                    modifier = Modifier.testTag("routines_new_folder_button"),
                    enabled = state.isOnline,
                )
            }
        }
        if (!state.isOnline) {
            Text(
                text = stringResource(Res.string.routines_start_offline_reason),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("routines_start_offline_reason"),
            )
        }
    }
}

@Composable
private fun RoutinesContent(state: RoutinesUiState.Content, onEvent: (RoutinesEvent) -> Unit) {
    if (state.isEmpty) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                message = stringResource(Res.string.routines_empty),
                title = stringResource(Res.string.routines_empty_title),
                action = AppStateAction(stringResource(Res.string.routines_new)) { onEvent(RoutinesEvent.NewRoutine) },
                testTagPrefix = "routines_empty",
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("routines_list"),
        contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(Res.string.routines_title),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.padding(bottom = Spacing.sm).semantics { heading() }.testTag("routines_title"),
            )
        }
        routineGroups(state, onEvent)
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
