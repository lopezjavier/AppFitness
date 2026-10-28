package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_cooking_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_energy_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_macros_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_reminders_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_save_error_offline
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_training_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_personal_title
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.designsystem.AppEditorScreen
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategySection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val SKELETON_CARDS = 3
private const val SKELETON_CARD_UNITS = 5

/** The title of the editor of [section] (the same label as the hub row that opens it, R-04). */
internal val PreferencesSection.title: StringResource
    get() = when (this) {
        PreferencesSection.PersonalData -> Res.string.profile_personal_title
        PreferencesSection.EnergyGoal -> Res.string.preferences_energy_title
        PreferencesSection.Macros -> Res.string.preferences_macros_title
        PreferencesSection.Cooking -> Res.string.preferences_cooking_title
        PreferencesSection.Training -> Res.string.preferences_training_title
        PreferencesSection.Reminders -> Res.string.preferences_reminders_title
    }

/**
 * Stateful section editor: connects [PreferencesViewModel] (its section comes from the route argument) to the
 * stateless [PreferencesSectionScreen]. [onClose] runs when the editor leaves (saved, discarded or clean back).
 */
@Composable
fun PreferencesSectionRoot(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // A Health Connect sync can land while the screen is away: re-read the weight flag on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(PreferencesEvent.Refresh) }
    PreferencesSectionScreen(
        section = viewModel.section,
        state = state,
        onEvent = viewModel::onEvent,
        onClose = onClose,
        modifier = modifier,
        effects = viewModel.effects,
    )
}

/**
 * One Preferencias editor (US-56) on [AppEditorScreen]: the fields of [section] above a pinned "Guardar" that saves
 * only that section. The template owns the discard dialog, the offline hint and the single close once
 * [PreferencesUiState.Loaded.isSaved]; a failed save is a Reject haptic plus a message (R-28, R-37).
 */
@Composable
fun PreferencesSectionScreen(
    section: PreferencesSection,
    state: PreferencesUiState,
    onEvent: (PreferencesEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    effects: Flow<PreferencesEffect> = emptyFlow(),
) {
    SaveFailureEffects(effects)
    val prefix = "preferences_${section.key}"
    val title = stringResource(section.title)
    if (state !is PreferencesUiState.Loaded) {
        AppScreen(title = title, modifier = modifier, onBack = onClose, testTagPrefix = prefix) {
            Box(Modifier.fillMaxSize().testTag("preferences_screen")) {
                if (state == PreferencesUiState.LoadFailed) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorState(
                            message = stringResource(Res.string.root_error_message),
                            action = AppStateAction(stringResource(Res.string.action_retry)) {
                                onEvent(PreferencesEvent.Retry)
                            },
                            testTagPrefix = "preferences_error",
                        )
                    }
                } else {
                    SectionLoading()
                }
            }
        }
        return
    }
    AppEditorScreen(
        title = title,
        isDirty = state.isDirty,
        isSaving = state.isSaving,
        isOnline = state.isOnline,
        onSave = { onEvent(PreferencesEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        saved = state.isSaved,
        canSave = state.canSave,
        testTagPrefix = prefix,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .testTag("preferences_content"),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            SectionContent(section, state, onEvent)
        }
    }
}

@Composable
private fun SectionContent(
    section: PreferencesSection,
    state: PreferencesUiState.Loaded,
    onEvent: (PreferencesEvent) -> Unit,
) {
    when (section) {
        // The picker sheets of "Datos personales" move to the picker archetypes in the next slice.
        PreferencesSection.PersonalData -> PersonalDataCard(state, onEvent)

        PreferencesSection.EnergyGoal -> EnergyStrategySection(
            state = state.energy,
            onEvent = { onEvent(PreferencesEvent.Energy(it)) },
            testTagPrefix = "preferences_energy",
        )

        PreferencesSection.Macros -> MacroSplitSection(state, onEvent)

        PreferencesSection.Cooking -> CookingSection(state.form.foodPreferences, onEvent)

        PreferencesSection.Training -> TrainingSection(
            warmUpIncluded = state.form.warmUpIncluded,
            onChange = { onEvent(PreferencesEvent.SetWarmUpIncluded(it)) },
            autoUpdateRoutine = state.form.autoUpdateRoutine,
            onAutoUpdateRoutine = { onEvent(PreferencesEvent.SetAutoUpdateRoutine(it)) },
            previousValuesMode = state.form.previousValuesMode,
            onPreviousValuesMode = { onEvent(PreferencesEvent.SetPreviousValuesMode(it)) },
            calculators = state.form.calculators,
            platesError = state.platesError,
            onEvent = onEvent,
        )

        PreferencesSection.Reminders -> RemindersSection(state.form, onEvent)
    }
}

/** A failed save: one Reject haptic and one message (R-28, R-29, R-37); the edits stay on screen. */
@Composable
private fun SaveFailureEffects(effects: Flow<PreferencesEffect>) {
    val messages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
    val failed = stringResource(Res.string.preferences_save_error)
    val offline = stringResource(Res.string.preferences_save_error_offline)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is PreferencesEffect.SaveFailed -> {
                    haptics.reject()
                    messages.show(if (effect.offline) offline else failed)
                }
            }
        }
    }
}

@Composable
private fun SectionLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("preferences_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(SKELETON_CARDS) {
            SkeletonBox(Modifier.fillMaxWidth().height(Spacing.xl * SKELETON_CARD_UNITS), AppShapes.lg)
        }
    }
}
