package io.github.lopezjavier.appfitness.feature.onboarding

import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_back
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_continue
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_goals_title
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_indicator
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_indicator_cd
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_offline_hint
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_save_error_offline
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_step_activity
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_step_birth_date
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_step_height
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_step_sex
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_step_weight
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stateful onboarding: connects [OnboardingViewModel] to the stateless [OnboardingScreen]. */
@Composable
fun OnboardingRoute(modifier: Modifier = Modifier, viewModel: OnboardingViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/**
 * The biometric onboarding: header (progress and step title), the controls of the step, and a footer with
 * "Atrás" and "Continuar" pinned to the bottom. The goals step is a stub until its task. A failed save shows a
 * persistent snackbar with "Reintentar".
 */
@Composable
fun OnboardingScreen(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, modifier: Modifier = Modifier) {
    val snackbarHost = remember { SnackbarHostState() }
    SaveErrorSnackbar(state.saveError, snackbarHost) { onEvent(OnboardingEvent.RetrySave) }
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = state.canGoBack,
        onBackCompleted = { onEvent(OnboardingEvent.Back) },
    )
    Box(
        modifier.fillMaxSize().safeDrawingPadding().testTag("onboarding_screen"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.fillMaxSize().widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .padding(horizontal = Spacing.screenMargin),
        ) {
            when {
                state.step == OnboardingStep.Goals -> GoalsStub(onBack = { onEvent(OnboardingEvent.Back) })

                state.phase == OnboardingPhase.Loading -> OnboardingLoading(Modifier.weight(1f))

                state.phase == OnboardingPhase.LoadError -> LoadFailure(
                    onRetry = { onEvent(OnboardingEvent.RetryLoad) },
                    modifier = Modifier.weight(1f),
                )

                else -> StepForm(state, onEvent, snackbarHost)
            }
        }
    }
}

@Composable
private fun SaveErrorSnackbar(error: OnboardingSaveError?, host: SnackbarHostState, onRetry: () -> Unit) {
    val message =
        when (error) {
            OnboardingSaveError.Network -> stringResource(Res.string.onboarding_save_error_offline)
            OnboardingSaveError.Other -> stringResource(Res.string.onboarding_save_error)
            null -> null
        }
    val retry = stringResource(Res.string.state_retry)
    val currentOnRetry by rememberUpdatedState(onRetry)
    LaunchedEffect(error) {
        if (message == null) return@LaunchedEffect
        val result = host.showSnackbar(message, retry, duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) currentOnRetry()
    }
}

@Composable
private fun ColumnScope.StepForm(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    snackbarHost: SnackbarHostState,
) {
    val focusTitle = remember { FocusRequester() }
    LaunchedEffect(state.step) { focusTitle.requestFocus() }
    Header(state.step, focusTitle)
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(vertical = Spacing.blockGap)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state.step) {
            OnboardingStep.Sex -> SexStep(state.sex) { onEvent(OnboardingEvent.SelectSex(it)) }
            OnboardingStep.BirthDate -> BirthDateStep(state) { onEvent(OnboardingEvent.SetBirthDate(it)) }
            OnboardingStep.Height -> HeightStep(state.heightCm) { onEvent(OnboardingEvent.SetHeight(it)) }
            OnboardingStep.Weight -> WeightStep(state.weightTenths) { onEvent(OnboardingEvent.SetWeight(it)) }
            OnboardingStep.Activity -> ActivityStep(state.activity) { onEvent(OnboardingEvent.SelectActivity(it)) }
            OnboardingStep.Goals -> Unit
        }
    }
    SnackbarHost(snackbarHost) { data ->
        Snackbar(
            action = {
                GhostButton(
                    text = data.visuals.actionLabel.orEmpty(),
                    onClick = { data.performAction() },
                    modifier = Modifier.testTag("onboarding_save_retry"),
                )
            },
        ) { Text(data.visuals.message) }
    }
    if (state.showOfflineHint) {
        Text(
            text = stringResource(Res.string.onboarding_offline_hint),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = Spacing.cardGap).semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("onboarding_offline_hint"),
        )
    }
    Footer(state, onEvent)
}

@Composable
private fun Header(step: OnboardingStep, focusTitle: FocusRequester) {
    val indicator = stringResource(Res.string.onboarding_indicator, step.number, OnboardingStep.BIOMETRIC_STEPS)
    val indicatorDescription =
        stringResource(Res.string.onboarding_indicator_cd, step.number, OnboardingStep.BIOMETRIC_STEPS)
    Column(Modifier.padding(top = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = indicator,
            style = AppTheme.typography.labelTechnical,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.semantics { contentDescription = indicatorDescription }
                .testTag("onboarding_step_indicator"),
        )
        Text(
            text = stringResource(stepTitle(step)),
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.focusRequester(focusTitle).focusable().semantics { heading() }
                .testTag("onboarding_step_title"),
        )
    }
}

private fun stepTitle(step: OnboardingStep): StringResource = when (step) {
    OnboardingStep.Sex -> Res.string.onboarding_step_sex
    OnboardingStep.BirthDate -> Res.string.onboarding_step_birth_date
    OnboardingStep.Height -> Res.string.onboarding_step_height
    OnboardingStep.Weight -> Res.string.onboarding_step_weight
    OnboardingStep.Activity -> Res.string.onboarding_step_activity
    OnboardingStep.Goals -> Res.string.onboarding_goals_title
}

@Composable
private fun Footer(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.step != OnboardingStep.Sex) {
            GhostButton(
                text = stringResource(Res.string.onboarding_back),
                onClick = { onEvent(OnboardingEvent.Back) },
                modifier = Modifier.testTag("onboarding_back"),
                enabled = state.canGoBack,
            )
        }
        PrimaryButton(
            text = stringResource(Res.string.onboarding_continue),
            onClick = { onEvent(OnboardingEvent.Continue) },
            modifier = Modifier.weight(1f).testTag("onboarding_continue"),
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
                step = OnboardingStep.Height,
                birthDate = BiometricBounds.defaultBirthDate(today),
                phase = OnboardingPhase.Content,
                today = today,
            ),
            onEvent = {},
        )
    }
}
