package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_save_error_offline
import io.github.lopezjavier.appfitness.composeapp.resources.profile_save
import io.github.lopezjavier.appfitness.composeapp.resources.profile_saved
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategySection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_CARDS = 3
private const val SKELETON_CARD_UNITS = 5

/** Stateful Preferencias: connects [PreferencesViewModel] to the stateless [PreferencesScreen]. */
@Composable
fun PreferencesRoute(modifier: Modifier = Modifier, viewModel: PreferencesViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PreferencesScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        effects = viewModel.effects,
    )
}

/**
 * Preferencias (US-06, US-07, US-08; ADR-37): the name, personal data and current weight, the energy strategy and the
 * macro split. Cards scroll above a pinned "Guardar" footer; from 600 dp the personal data and energy cards sit in
 * the left column and the macro split in the right one. [effects] feed the "Cambios guardados" / error snackbars and
 * the confirm haptic.
 */
@Composable
fun PreferencesScreen(
    state: PreferencesUiState,
    onEvent: (PreferencesEvent) -> Unit,
    modifier: Modifier = Modifier,
    effects: Flow<PreferencesEffect> = emptyFlow(),
) {
    val snackbarHost = remember { SnackbarHostState() }
    SaveEffects(effects, snackbarHost)
    Box(modifier.fillMaxSize().testTag("preferences_screen"), contentAlignment = Alignment.TopCenter) {
        when (state) {
            PreferencesUiState.Loading -> PreferencesLoading()

            PreferencesUiState.LoadFailed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(
                    message = stringResource(Res.string.root_error_message),
                    action = AppStateAction(stringResource(Res.string.state_retry)) {
                        onEvent(PreferencesEvent.Retry)
                    },
                    testTagPrefix = "preferences_error",
                )
            }

            is PreferencesUiState.Loaded -> PreferencesLoaded(state, onEvent, snackbarHost)
        }
    }
}

/** Haptic and snackbar for the result of a save: confirm on success only, a long message-only snackbar on failure. */
@Composable
private fun SaveEffects(effects: Flow<PreferencesEffect>, host: SnackbarHostState) {
    val haptic = LocalHapticFeedback.current
    val saved = stringResource(Res.string.profile_saved)
    val failed = stringResource(Res.string.onboarding_save_error)
    val offline = stringResource(Res.string.onboarding_save_error_offline)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                PreferencesEffect.Saved -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    host.showSnackbar(saved)
                }

                is PreferencesEffect.SaveFailed ->
                    host.showSnackbar(if (effect.offline) offline else failed, duration = SnackbarDuration.Long)
            }
        }
    }
}

@Composable
private fun PreferencesLoading() {
    Column(
        Modifier
            .widthIn(max = WindowWidthClassDefaults.TwoColumnContentMaxWidth)
            .fillMaxSize()
            .padding(Spacing.screenMargin)
            .testTag("preferences_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(SKELETON_CARDS) {
            SkeletonBox(Modifier.fillMaxWidth().height(Spacing.xl * SKELETON_CARD_UNITS), AppShapes.lg)
        }
    }
}

@Composable
private fun PreferencesLoaded(
    state: PreferencesUiState.Loaded,
    onEvent: (PreferencesEvent) -> Unit,
    snackbarHost: SnackbarHostState,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= WindowWidthClassDefaults.WideMinWidth
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.TwoColumnContentMaxWidth).fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                    .testTag("preferences_content"),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                if (wide) {
                    TwoColumns(state, onEvent)
                } else {
                    PersonalAndEnergy(state, onEvent)
                    MacroSplitCard(state, onEvent)
                }
            }
            SnackbarHost(snackbarHost) { data -> Snackbar { Text(data.visuals.message) } }
            SaveFooter(state, onEvent)
        }
    }
}

@Composable
private fun ColumnScope.PersonalAndEnergy(state: PreferencesUiState.Loaded, onEvent: (PreferencesEvent) -> Unit) {
    PersonalDataCard(state, onEvent)
    AppCard(Modifier.fillMaxWidth()) {
        EnergyStrategySection(
            state = state.energy,
            onEvent = { onEvent(PreferencesEvent.Energy(it)) },
            testTagPrefix = "preferences_energy",
        )
    }
}

@Composable
private fun TwoColumns(state: PreferencesUiState.Loaded, onEvent: (PreferencesEvent) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            PersonalAndEnergy(state, onEvent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            MacroSplitCard(state, onEvent)
        }
    }
}

/** The pinned "Guardar" bar; always shown, disabled unless there is something to save. */
@Composable
private fun SaveFooter(state: PreferencesUiState.Loaded, onEvent: (PreferencesEvent) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surfaceBase)
            .padding(horizontal = Spacing.screenMargin, vertical = Spacing.md),
    ) {
        PrimaryButton(
            text = stringResource(Res.string.profile_save),
            onClick = { onEvent(PreferencesEvent.Save) },
            modifier = Modifier.fillMaxWidth().testTag("preferences_save"),
            enabled = state.canSave,
            loading = state.isSaving,
        )
    }
}
