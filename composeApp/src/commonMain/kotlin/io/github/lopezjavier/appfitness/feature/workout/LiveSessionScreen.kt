package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.composeapp.resources.ic_pause
import io.github.lopezjavier.appfitness.composeapp.resources.ic_play_arrow
import io.github.lopezjavier.appfitness.composeapp.resources.live_action_failed
import io.github.lopezjavier.appfitness.composeapp.resources.live_error
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish
import io.github.lopezjavier.appfitness.composeapp.resources.live_finish_offline_reason
import io.github.lopezjavier.appfitness.composeapp.resources.live_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.live_pause
import io.github.lopezjavier.appfitness.composeapp.resources.live_resume
import io.github.lopezjavier.appfitness.composeapp.resources.live_title_fallback
import io.github.lopezjavier.appfitness.composeapp.resources.rest_sound_permission_note
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.RestTimerBar
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.platform.KeepScreenOn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_CARDS = 2
private val SkeletonCardHeight = Spacing.xl * 6

/**
 * Stateful live session: connects [LiveSessionViewModel], hands the exercise the picker returned
 * ([selectedExerciseId], consumed once through [onConsumeSelection]) and routes its one-off results: the picker
 * ([onAddExercise]), leaving the flow ([onClose]) and a message plus a Reject haptic for a failed finish or discard.
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
    val messages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
    val currentClose by rememberUpdatedState(onClose)
    val currentAddExercise by rememberUpdatedState(onAddExercise)
    val currentConsume by rememberUpdatedState(onConsumeSelection)
    val failed = stringResource(Res.string.live_action_failed)
    val permissionNote = stringResource(Res.string.rest_sound_permission_note)
    // The options sheets show their own "No se pudo guardar" line; the flag survives recreation with the open sheet.
    var optionSaveFailed by rememberSaveable { mutableStateOf(false) }
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

                LiveSessionEffect.ActionFailed -> {
                    haptics.reject()
                    messages.show(failed)
                }

                LiveSessionEffect.OptionSaveFailed -> {
                    haptics.reject()
                    optionSaveFailed = true
                }

                LiveSessionEffect.RestAlertPermissionNote -> messages.show(permissionNote)
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
    val onEvent = { event: LiveSessionEvent ->
        if (event.isOptionEdit()) optionSaveFailed = false
        viewModel.onEvent(event)
    }
    LiveSessionScreen(state, elapsed, onEvent, onClose, onOpenExercise, modifier, optionSaveFailed)
}

private fun LiveSessionEvent.isOptionEdit() = this is LiveSessionEvent.BodyWeightDraftChanged ||
    this is LiveSessionEvent.SaveBodyWeight ||
    this is LiveSessionEvent.DismissVolumeDrafts

/**
 * The live workout on [AppScreen]: the routine name with the stopwatch as subtitle, pause and menu actions, one card
 * per exercise and the rest bar pinned as the footer. Loading shows skeleton cards, a failed load offers "Reintentar"
 * and a finished session shows its summary. [onBack] leaves the screen without finishing the session.
 */
@Composable
fun LiveSessionScreen(
    state: LiveSessionUiState,
    elapsedSeconds: Long,
    onEvent: (LiveSessionEvent) -> Unit,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    optionSaveFailed: Boolean = false,
) {
    Box(modifier.fillMaxSize().background(AppTheme.colors.surfaceBase).testTag("live_screen")) {
        when (state) {
            LiveSessionUiState.Loading, LiveSessionUiState.NoSession -> LiveMessage(onBack) { LiveLoading() }

            LiveSessionUiState.Error -> LiveMessage(onBack) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.live_error),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
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
                onOpenExercise,
                optionSaveFailed,
            )

            is LiveSessionUiState.Finished -> {
                SessionSummaryScreen(state.summary, onDone = { onEvent(LiveSessionEvent.Done) })
                state.updateRoutineSheet?.let { sheet ->
                    UpdateRoutineBottomSheet(
                        sheet = sheet,
                        onUpdate = { onEvent(LiveSessionEvent.UpdateRoutine) },
                        onKeep = { onEvent(LiveSessionEvent.KeepRoutine) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveMessage(onBack: () -> Unit, content: @Composable () -> Unit) {
    AppScreen(
        title = stringResource(Res.string.live_title_fallback),
        onBack = onBack,
        maxContentWidth = WindowWidthClassDefaults.CardColumnMaxWidth,
        testTagPrefix = "live",
    ) { content() }
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

@Composable
@Suppress("LongParameterList") // State plus the screen's event and navigation hooks.
private fun LiveActive(
    state: LiveSessionUiState.Active,
    elapsedSeconds: Long,
    onEvent: (LiveSessionEvent) -> Unit,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    optionSaveFailed: Boolean,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    KeepScreenOn()
    val subtitle = liveSubtitle(elapsedSeconds, state.isPaused)
    val timer = state.restTimer
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
        AppScreen(
            title = state.name.ifBlank { stringResource(Res.string.live_title_fallback) },
            subtitle = subtitle.text,
            subtitleSemantics = subtitle.semantics,
            onBack = onBack,
            actions = liveActions(state, onEvent, onMenu = { menuOpen = true }),
            footer = timer?.let { { RestFooter(it, onEvent) } },
            maxContentWidth = if (wide) {
                WindowWidthClassDefaults.TwoColumnContentMaxWidth
            } else {
                WindowWidthClassDefaults.CardColumnMaxWidth
            },
            testTagPrefix = "live",
        ) {
            if (wide) {
                WideLive(state, onEvent, onOpenExercise, optionSaveFailed)
            } else {
                NarrowLive(state, onEvent, onOpenExercise, optionSaveFailed)
            }
        }
    }
    LiveDialogs(state, menuOpen, onCloseMenu = { menuOpen = false }, onEvent)
    LiveTypePicker(state, onEvent)
    LiveRpePicker(state, onEvent)
    LiveCalculator(state, onEvent)
}

/** Pause or resume (hidden while an action is blocked or running) and the overflow menu of the top bar. */
@Composable
private fun liveActions(
    state: LiveSessionUiState.Active,
    onEvent: (LiveSessionEvent) -> Unit,
    onMenu: () -> Unit,
): List<AppTopBarAction> {
    val pauseLabel = stringResource(if (state.isPaused) Res.string.live_resume else Res.string.live_pause)
    val menuLabel = stringResource(Res.string.live_menu_cd)
    return buildList {
        if (!state.actionsBlocked && !state.busy) {
            add(
                AppTopBarAction(
                    icon = if (state.isPaused) Res.drawable.ic_play_arrow else Res.drawable.ic_pause,
                    contentDescription = pauseLabel,
                    onClick = {
                        onEvent(if (state.isPaused) LiveSessionEvent.ResumeSession else LiveSessionEvent.PauseSession)
                    },
                    testTag = "live_pause_toggle",
                ),
            )
        }
        add(AppTopBarAction(Res.drawable.ic_more_vert, menuLabel, onMenu, testTag = "live_menu_button"))
    }
}

/** The running rest, pinned as the screen footer (above the bottom inset; messages float above it). */
@Composable
private fun RestFooter(timer: RestTimerUi, onEvent: (LiveSessionEvent) -> Unit) {
    RestTimerBar(
        remaining = timer.remaining,
        total = timer.total,
        onAddTime = { onEvent(LiveSessionEvent.RestAdd30) },
        onSkip = { onEvent(LiveSessionEvent.RestSkip) },
        testTagPrefix = "live_rest",
        paused = timer.paused,
        pausedTestTag = "live_rest_timer_paused",
    )
}

/** The open warm-up or plate calculator sheet; its draft lives in the view model (survives process death). */
@Composable
private fun LiveCalculator(state: LiveSessionUiState.Active, onEvent: (LiveSessionEvent) -> Unit) {
    val calculator = state.calculator ?: return
    val prefix = "live_exercise_${calculator.draft.position}"
    val position = calculator.draft.position
    val actions = CalculatorActions(
        onWeight = { onEvent(LiveSessionEvent.CalculatorWeightChanged(it)) },
        onDiscCount = { kg, count -> onEvent(LiveSessionEvent.DiscCountChanged(kg, count)) },
        onWarmUpSets = { onEvent(LiveSessionEvent.CalculatorWarmUpSetsChanged(it)) },
        onAddWarmUp = { onEvent(LiveSessionEvent.AddWarmUpSets(position, it)) },
        onDismiss = { onEvent(LiveSessionEvent.DismissCalculator) },
    )
    when (calculator.draft.kind) {
        CalculatorKind.WarmUp -> WarmUpCalculatorSheet(prefix, calculator, actions)
        CalculatorKind.Discs -> DiscLoaderSheet(prefix, calculator, actions)
    }
}

/** "Finalizar entreno" below "Añadir ejercicio" once every set is done; offline it stays visible but disabled. */
@Composable
internal fun FinishButton(state: LiveSessionUiState.Active, onEvent: (LiveSessionEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PrimaryButton(
            text = stringResource(Res.string.live_finish),
            onClick = { onEvent(LiveSessionEvent.RequestFinish) },
            modifier = Modifier.fillMaxWidth().testTag("live_finish_button"),
            enabled = !state.actionsBlocked && !state.busy,
        )
        if (state.actionsBlocked) {
            Text(
                text = stringResource(Res.string.live_finish_offline_reason),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("live_finish_offline_reason"),
            )
        }
    }
}
