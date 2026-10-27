package io.github.lopezjavier.appfitness.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_save
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_back
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_continue
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_goals_title
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_helper_about_you
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_helper_activity
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_helper_goals
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_helper_measures
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_indicator
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_indicator_cd
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_offline_hint
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_save_error_offline
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_step_about_you
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_step_activity
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_step_measures
import io.github.lopezjavier.appfitness.designsystem.AppButtonDefaults
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StepScaffold
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.feature.biometrics.ActivityCards
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategySection
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.seconds

/** Stateful onboarding: connects [OnboardingViewModel] to the stateless [OnboardingScreen]. */
@Composable
fun OnboardingRoute(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingScreen(state = state, onEvent = viewModel::onEvent, onComplete = onComplete, modifier = modifier)
}

/** Timing of the onboarding screen. */
object OnboardingDefaults {
    /** How long "Objetivos guardados" shows before the root leaves onboarding. */
    val ConfirmationDuration = 1.seconds
}

/**
 * The biometric onboarding on the [StepScaffold]: indicator, title, helper, the controls of the step and a footer
 * with "Atrás" and "Continuar" pinned to the bottom. The goals step shows the energy strategy section with
 * "Guardar"; once saved it confirms in place and calls [onComplete] after [OnboardingDefaults.ConfirmationDuration].
 * A failed save goes to the shell's message queue ([LocalAppMessages]); the user taps the footer button again and the
 * values are kept.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {},
) {
    SaveErrorMessage(state.saveError)
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = state.canGoBack,
        onBackCompleted = { onEvent(OnboardingEvent.Back) },
    )
    Box(modifier.fillMaxSize().testTag("onboarding_screen"), contentAlignment = Alignment.TopCenter) {
        when (state.phase) {
            OnboardingPhase.Completed -> OnboardingFrame {
                GoalsSaved(Modifier.weight(1f))
                ConfirmationEffect(onComplete)
            }

            OnboardingPhase.Loading -> OnboardingFrame { OnboardingLoading(Modifier.weight(1f)) }

            OnboardingPhase.LoadError -> OnboardingFrame {
                LoadFailure(onRetry = { onEvent(OnboardingEvent.RetryLoad) }, modifier = Modifier.weight(1f))
            }

            else -> StepForm(state, onEvent)
        }
    }
}

@Composable
private fun OnboardingFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
            .padding(horizontal = Spacing.screenMargin),
        content = content,
    )
}

/** Confirms with a haptic once, waits [OnboardingDefaults.ConfirmationDuration], then reports completion. */
@Composable
private fun ConfirmationEffect(onComplete: () -> Unit) {
    val haptic = LocalAppHaptics.current
    val currentOnCompleted by rememberUpdatedState(onComplete)
    // Survives rotation: the haptic fires once even if the composition is recreated during the delay.
    var buzzed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!buzzed) {
            buzzed = true
            haptic.confirm()
        }
        delay(OnboardingDefaults.ConfirmationDuration)
        currentOnCompleted()
    }
}

@Composable
private fun SaveErrorMessage(error: OnboardingSaveError?) {
    val messages = LocalAppMessages.current
    val message =
        when (error) {
            OnboardingSaveError.Network -> stringResource(Res.string.onboarding_save_error_offline)
            OnboardingSaveError.Other -> stringResource(Res.string.onboarding_save_error)
            null -> null
        }
    LaunchedEffect(error) {
        if (message != null) messages.show(message)
    }
}

@Composable
private fun StepForm(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    StepScaffold(
        step = state.step.number,
        indicator = stringResource(Res.string.onboarding_indicator, state.step.number, OnboardingStep.TOTAL_STEPS),
        indicatorDescription =
        stringResource(Res.string.onboarding_indicator_cd, state.step.number, OnboardingStep.TOTAL_STEPS),
        title = { stringResource(stepTitle(OnboardingStep.entries[it - 1])) },
        helper = { stringResource(stepHelper(OnboardingStep.entries[it - 1])) },
        footer = {
            Column {
                if (state.showOfflineHint) {
                    Text(
                        text = stringResource(Res.string.onboarding_offline_hint),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.padding(bottom = Spacing.cardGap)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("onboarding_offline_hint"),
                    )
                }
                Footer(state, onEvent)
            }
        },
        testTagPrefix = "onboarding",
    ) { shown ->
        when (OnboardingStep.entries[shown - 1]) {
            OnboardingStep.AboutYou -> AboutYouStep(
                state = state,
                onName = { onEvent(OnboardingEvent.SetName(it)) },
                onSelectSex = { onEvent(OnboardingEvent.SelectSex(it)) },
                onBirthDate = { onEvent(OnboardingEvent.SetBirthDate(it)) },
            )

            OnboardingStep.Measures -> MeasuresStep(
                heightCm = state.heightCm,
                weightTenths = state.weightTenths,
                onHeight = { onEvent(OnboardingEvent.SetHeight(it)) },
                onWeight = { onEvent(OnboardingEvent.SetWeight(it)) },
            )

            OnboardingStep.Activity -> ActivityCards(
                state.activity,
                onSelect = { onEvent(OnboardingEvent.SelectActivity(it)) },
                tagPrefix = "onboarding",
            )

            OnboardingStep.Goals -> state.energy?.let { energy ->
                EnergyStrategySection(
                    state = energy,
                    onEvent = { onEvent(OnboardingEvent.Energy(it)) },
                    testTagPrefix = "onboarding_goals",
                )
            }
        }
    }
}

private fun stepTitle(step: OnboardingStep): StringResource = when (step) {
    OnboardingStep.AboutYou -> Res.string.onboarding_step_about_you
    OnboardingStep.Measures -> Res.string.onboarding_step_measures
    OnboardingStep.Activity -> Res.string.onboarding_step_activity
    OnboardingStep.Goals -> Res.string.onboarding_goals_title
}

private fun stepHelper(step: OnboardingStep): StringResource = when (step) {
    OnboardingStep.AboutYou -> Res.string.onboarding_helper_about_you
    OnboardingStep.Measures -> Res.string.onboarding_helper_measures
    OnboardingStep.Activity -> Res.string.onboarding_helper_activity
    OnboardingStep.Goals -> Res.string.onboarding_helper_goals
}

@Composable
private fun Footer(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.step != OnboardingStep.AboutYou) {
            SecondaryButton(
                text = stringResource(Res.string.onboarding_back),
                onClick = { onEvent(OnboardingEvent.Back) },
                modifier = Modifier.widthIn(min = AppButtonDefaults.CompactMinWidth).testTag("onboarding_back"),
                enabled = state.canGoBack,
            )
        }
        val isGoals = state.step == OnboardingStep.Goals
        PrimaryButton(
            text = stringResource(if (isGoals) Res.string.action_save else Res.string.onboarding_continue),
            onClick = { onEvent(if (isGoals) OnboardingEvent.SaveGoals else OnboardingEvent.Continue) },
            modifier = Modifier.weight(1f).testTag(if (isGoals) "onboarding_goals_save" else "onboarding_continue"),
            enabled = state.canContinue,
            loading = state.phase == OnboardingPhase.Saving,
        )
    }
}

@Preview
@Composable
private fun OnboardingScreenPreview() {
    val today = LocalDate(2026, 10, 14)
    AppTheme {
        OnboardingScreen(
            state = OnboardingUiState(
                step = OnboardingStep.Measures,
                birthDate = BiometricBounds.defaultBirthDate(today),
                phase = OnboardingPhase.Content,
                today = today,
            ),
            onEvent = {},
        )
    }
}
