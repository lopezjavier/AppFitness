package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.live_action_failed
import io.github.lopezjavier.appfitness.composeapp.resources.live_add_exercise
import io.github.lopezjavier.appfitness.composeapp.resources.live_error
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.RestTimerBar
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.platform.KeepScreenOn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_CARDS = 2
private val SkeletonCardHeight = Spacing.xl * 6

/**
 * Stateful live session: connects [LiveSessionViewModel], hands the exercise the picker returned
 * ([selectedExerciseId], consumed once through [onConsumeSelection]) and routes its one-off results: the picker
 * ([onAddExercise]), leaving the flow ([onClose]) and a snackbar for a failed finish or discard.
 */
@Composable
fun LiveSessionRoot(
    selectedExerciseId: String?,
    onConsumeSelection: () -> Unit,
    onAddExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveSessionViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle(initialValue = 0L)
    val snackbarHost = remember { SnackbarHostState() }
    val currentClose by rememberUpdatedState(onClose)
    val currentAddExercise by rememberUpdatedState(onAddExercise)
    val currentConsume by rememberUpdatedState(onConsumeSelection)
    val failed = stringResource(Res.string.live_action_failed)
    LaunchedEffect(selectedExerciseId) {
        if (selectedExerciseId != null) {
            viewModel.onEvent(LiveSessionEvent.ExerciseSelected(selectedExerciseId))
            currentConsume()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LiveSessionEffect.OpenPicker -> currentAddExercise()
                LiveSessionEffect.Closed -> currentClose()
                LiveSessionEffect.ActionFailed -> snackbarHost.showSnackbar(failed)
            }
        }
    }
    // The session was finished or discarded elsewhere: go back to "Mis rutinas" instead of a blank screen.
    LaunchedEffect(state) { if (state == LiveSessionUiState.NoSession) currentClose() }
    // Back from the background: the rest countdown is recomputed from the clock at once.
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(LiveSessionEvent.Resume)
        onPauseOrDispose { viewModel.onEvent(LiveSessionEvent.Pause) }
    }
    LiveSessionScreen(state, elapsed, viewModel::onEvent, onClose, onOpenExercise, modifier, snackbarHost)
}

/**
 * The live workout: top bar, one card per exercise and a sticky "Finalizar entreno" bar. Loading shows skeleton
 * cards, a failed load offers "Reintentar" and a finished session shows its summary. [onBack] leaves the screen
 * without finishing the session.
 */
@Composable
fun LiveSessionScreen(
    state: LiveSessionUiState,
    elapsedSeconds: Long,
    onEvent: (LiveSessionEvent) -> Unit,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier.fillMaxSize().background(AppTheme.colors.surfaceBase).testTag("live_screen")) {
        when (state) {
            LiveSessionUiState.Loading, LiveSessionUiState.NoSession ->
                LiveMessage(elapsedSeconds, onBack) { LiveLoading() }

            LiveSessionUiState.Error -> LiveMessage(elapsedSeconds, onBack) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.live_error),
                        action = AppStateAction(stringResource(Res.string.state_retry)) {
                            onEvent(LiveSessionEvent.Retry)
                        },
                        testTagPrefix = "live_error",
                    )
                }
            }

            is LiveSessionUiState.Active -> LiveActive(
                state,
                elapsedSeconds,
                onEvent,
                onBack,
                snackbarHost,
                onOpenExercise,
            )

            is LiveSessionUiState.Finished ->
                SessionSummaryScreen(state.summary, onDone = { onEvent(LiveSessionEvent.Done) })
        }
    }
}

@Composable
private fun LiveMessage(elapsedSeconds: Long, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LiveSessionTopBar(elapsedSeconds, onBack, onMenu = null)
        content()
    }
}

@Composable
private fun LiveLoading() {
    Column(
        Modifier.fillMaxSize().padding(
            horizontal = Spacing.screenMargin,
            vertical = Spacing.lg,
        ).testTag("live_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(SKELETON_CARDS) { SkeletonBox(Modifier.fillMaxWidth().height(SkeletonCardHeight), AppShapes.lg) }
    }
}

/** The running rest, pinned above the list (never over its rows) and capped to the list's width. */
@Composable
private fun RestBar(timer: RestTimerUi, onEvent: (LiveSessionEvent) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin), contentAlignment = Alignment.TopCenter) {
        RestTimerBar(
            remaining = timer.remaining,
            total = timer.total,
            onAddTime = { onEvent(LiveSessionEvent.RestAdd30) },
            onSkip = { onEvent(LiveSessionEvent.RestSkip) },
            modifier = Modifier.widthIn(max = WindowWidthClassDefaults.CardColumnMaxWidth)
                .padding(top = Spacing.sm, bottom = Spacing.md),
            testTagPrefix = "live_rest",
        )
    }
}

@Composable
@Suppress("LongParameterList") // State plus the screen's event and navigation hooks.
private fun LiveActive(
    state: LiveSessionUiState.Active,
    elapsedSeconds: Long,
    onEvent: (LiveSessionEvent) -> Unit,
    onBack: () -> Unit,
    snackbarHost: SnackbarHostState,
    onOpenExercise: (String) -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    KeepScreenOn()
    Column(Modifier.fillMaxSize().imePadding()) {
        LiveSessionTopBar(elapsedSeconds, onBack, onMenu = { menuOpen = true })
        KeepAwakeIndicator(Modifier.padding(horizontal = Spacing.screenMargin, vertical = Spacing.xs))
        state.restTimer?.let { RestBar(it, onEvent) }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = WindowWidthClassDefaults.CardColumnMaxWidth).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                items(state.exercises, key = { it.position }) { LiveExerciseCard(it, onEvent, onOpenExercise) }
                item(key = "add_exercise") {
                    SecondaryButton(
                        text = stringResource(Res.string.live_add_exercise),
                        onClick = { onEvent(LiveSessionEvent.AddExercise) },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.blockGap - Spacing.cardGap)
                            .testTag("live_add_exercise"),
                    )
                }
            }
        }
        SnackbarHost(snackbarHost) { data -> Snackbar { Text(data.visuals.message) } }
        FinishBar(state, onFinish = { onEvent(LiveSessionEvent.RequestFinish) })
    }
    LiveDialogs(state, menuOpen, onCloseMenu = { menuOpen = false }, onEvent)
}
