package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_play_arrow
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_message
import io.github.lopezjavier.appfitness.composeapp.resources.live_discard_title
import io.github.lopezjavier.appfitness.composeapp.resources.routines_action_failed
import io.github.lopezjavier.appfitness.composeapp.resources.workout_continue_cd
import io.github.lopezjavier.appfitness.composeapp.resources.workout_discard_cd
import io.github.lopezjavier.appfitness.composeapp.resources.workout_entry_history_cd
import io.github.lopezjavier.appfitness.composeapp.resources.workout_entry_title
import io.github.lopezjavier.appfitness.composeapp.resources.workout_in_progress
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.routines.RoutinesRoot
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Content of the Entreno tab: the "Rutinas" header with the history action, the routines (with starting a session)
 * and, while a session is in progress, a "Continuar entreno" card. A session found at launch (cold start included)
 * opens the live screen once.
 */
@Composable
fun WorkoutEntryRoot(
    onOpenBuilder: (routineId: String?) -> Unit,
    onOpenDetails: (routineId: String) -> Unit,
    onOpenLiveSession: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutEntryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val discardAsked by viewModel.discardAsked.collectAsStateWithLifecycle()
    val discarding by viewModel.discarding.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
    val failed = stringResource(Res.string.routines_action_failed)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                WorkoutEntryEffect.DiscardFailed -> {
                    haptics.reject()
                    messages.show(failed)
                }
            }
        }
    }
    // Coming back from the live screen, a finished or discarded session must stop showing as in progress.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.recheck() }
    WorkoutEntryHost(
        state = state,
        onOpenLiveSession = onOpenLiveSession,
        onOpenHistory = onOpenHistory,
        modifier = modifier,
        discardAsked = discardAsked,
        discarding = discarding,
        isOnline = isOnline,
        onEvent = viewModel::onEvent,
    ) { routinesModifier, openLive ->
        RoutinesRoot(onOpenBuilder, onOpenDetails, openLive, routinesModifier)
    }
}

/**
 * Stateless Entreno tab on an `AppScreen` with the compact header (D1): "Rutinas" and the history (calendar)
 * action. Opens the live screen once when [state] becomes [WorkoutEntryState.Live]; leaving that
 * screen with back does not reopen it (the "Continuar entreno" card does), and the guard re-arms when no session
 * is in progress. The card's trash button asks for confirmation ([discardAsked]) and is inert offline or while
 * [discarding].
 */
@Composable
@Suppress("LongParameterList") // State plus the tab's navigation and discard hooks.
fun WorkoutEntryHost(
    state: WorkoutEntryState,
    onOpenLiveSession: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    discardAsked: Boolean = false,
    discarding: Boolean = false,
    isOnline: Boolean = true,
    onEvent: (WorkoutEntryEvent) -> Unit = {},
    routines: @Composable (modifier: Modifier, onOpenLiveSession: () -> Unit) -> Unit,
) {
    var opened by rememberSaveable { mutableStateOf(false) }
    val open = {
        opened = true
        onOpenLiveSession()
    }
    // Re-arm only on a Live -> Routines transition seen by this composition. A stale Routines state right after
    // coming back from the live screen (before recheck) must not clear the flag, or Live would reopen the screen.
    var previous by remember { mutableStateOf<WorkoutEntryState?>(null) }
    LaunchedEffect(state) {
        when (state) {
            WorkoutEntryState.Routines -> if (previous == WorkoutEntryState.Live) opened = false
            WorkoutEntryState.Live -> if (!opened) open()
            WorkoutEntryState.Checking -> Unit
        }
        previous = state
    }
    val title = stringResource(Res.string.workout_entry_title)
    AppScreen(
        title = title,
        modifier = modifier,
        header = { EntryHeader(title, onOpenHistory) },
        testTagPrefix = "workout_entry",
    ) {
        Column(Modifier.fillMaxSize()) {
            if (state == WorkoutEntryState.Live) {
                ContinueCard(
                    onContinue = open,
                    onDiscard = { onEvent(WorkoutEntryEvent.RequestDiscard) },
                    discardEnabled = isOnline && !discarding,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { routines(Modifier, open) }
        }
    }
    if (discardAsked) {
        AppConfirmDialog(
            title = stringResource(Res.string.live_discard_title),
            message = stringResource(Res.string.live_discard_message),
            confirmLabel = stringResource(Res.string.live_discard_confirm),
            cancelLabel = stringResource(Res.string.live_discard_cancel),
            onConfirm = { onEvent(WorkoutEntryEvent.ConfirmDiscard) },
            onDismiss = { onEvent(WorkoutEntryEvent.DismissDiscard) },
            testTagPrefix = "workout_discard_confirm",
        )
    }
}

/** The tab heading (equal to the bottom-bar label, R-04) and the one history action (R-03). */
@Composable
private fun EntryHeader(title: String, onOpenHistory: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.screenMargin, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.workout_entry_history_cd),
            icon = Res.drawable.ic_calendar,
            testTag = "workout_entry_history",
            onClick = onOpenHistory,
        )
    }
}

@Composable
private fun ContinueCard(onContinue: () -> Unit, onDiscard: () -> Unit, discardEnabled: Boolean) {
    AppCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.workout_in_progress),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).padding(end = Spacing.md),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                AppIconButton(
                    contentDescription = stringResource(Res.string.workout_continue_cd),
                    icon = Res.drawable.ic_play_arrow,
                    testTag = "workout_continue",
                    onClick = onContinue,
                    tint = AppTheme.colors.athleticCyan,
                )
                AppIconButton(
                    contentDescription = stringResource(Res.string.workout_discard_cd),
                    icon = Res.drawable.ic_delete,
                    testTag = "workout_discard",
                    onClick = onDiscard,
                    enabled = discardEnabled,
                    tint = AppTheme.colors.textSecondary,
                )
            }
        }
    }
}
