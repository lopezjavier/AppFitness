package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.workout_continue
import io.github.lopezjavier.appfitness.composeapp.resources.workout_history
import io.github.lopezjavier.appfitness.composeapp.resources.workout_in_progress
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.routines.RoutinesRoot
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Content of the Entreno tab: a "Historial" entry, "Mis rutinas" (with starting a session) and, while a session is
 * in progress, a "Continuar entreno" card. A session found at launch (cold start included) opens the live screen once.
 */
@Composable
fun WorkoutEntryRoot(
    onOpenBuilder: (routineId: String?) -> Unit,
    onOpenLiveSession: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutEntryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Coming back from the live screen, a finished or discarded session must stop showing as in progress.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.recheck() }
    WorkoutEntryHost(state, onOpenLiveSession, onOpenHistory, modifier) { routinesModifier, openLive ->
        RoutinesRoot(onOpenBuilder, openLive, routinesModifier)
    }
}

/**
 * Stateless Entreno tab. Opens the live screen once when [state] becomes [WorkoutEntryState.Live]; leaving that
 * screen with back does not reopen it (the "Continuar entreno" card does), and the guard re-arms when no session
 * is in progress.
 */
@Composable
fun WorkoutEntryHost(
    state: WorkoutEntryState,
    onOpenLiveSession: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    routines: @Composable (modifier: Modifier, onOpenLiveSession: () -> Unit) -> Unit,
) {
    var opened by rememberSaveable { mutableStateOf(false) }
    val open = {
        opened = true
        onOpenLiveSession()
    }
    LaunchedEffect(state) {
        when (state) {
            WorkoutEntryState.Routines -> opened = false
            WorkoutEntryState.Live -> if (!opened) open()
            WorkoutEntryState.Checking -> Unit
        }
    }
    Column(modifier) {
        if (state == WorkoutEntryState.Live) ContinueCard(open)
        GhostButton(
            text = stringResource(Res.string.workout_history),
            onClick = onOpenHistory,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm)
                .testTag("workout_history"),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) { routines(Modifier, open) }
    }
}

@Composable
private fun ContinueCard(onContinue: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.workout_in_progress),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).padding(end = Spacing.md),
            )
            PrimaryButton(
                text = stringResource(Res.string.workout_continue),
                onClick = onContinue,
                modifier = Modifier.testTag("workout_continue"),
            )
        }
    }
}
