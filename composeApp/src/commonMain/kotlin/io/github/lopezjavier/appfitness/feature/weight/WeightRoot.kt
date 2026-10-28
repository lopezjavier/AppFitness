package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.app.WeightEntryTarget
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.delete_failed
import io.github.lopezjavier.appfitness.composeapp.resources.delete_offline
import io.github.lopezjavier.appfitness.composeapp.resources.goal_target_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.weight_delete_error
import io.github.lopezjavier.appfitness.composeapp.resources.weight_delete_error_offline
import io.github.lopezjavier.appfitness.composeapp.resources.weight_save_error
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.platform.HealthConnectLauncher
import io.github.lopezjavier.appfitness.platform.rememberHealthConnectLauncher
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stateful weight screen: connects [WeightViewModel]; it reloads on resume so a change in Preferencias shows. */
// The view models' one-shot effects are collected by a helper, so it receives them.
@Suppress("ViewModelForwarding")
@Composable
fun WeightRoot(
    onOpenHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    openEntry: WeightEntryTarget? = null,
    onRemindersChange: () -> Unit = {},
    viewModel: WeightViewModel = koinViewModel(),
    circumferencesViewModel: CircumferencesViewModel = koinViewModel(),
    healthSyncViewModel: HealthSyncViewModel = koinViewModel(),
    trendViewModel: WeightTrendViewModel = koinViewModel(),
    goalViewModel: GoalViewModel = koinViewModel(),
    launcherFor: @Composable ((weightReadGranted: Boolean) -> Unit) -> HealthConnectLauncher =
        { rememberHealthConnectLauncher(it) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val circumferences by circumferencesViewModel.uiState.collectAsStateWithLifecycle()
    val healthSync by healthSyncViewModel.uiState.collectAsStateWithLifecycle()
    val trend by trendViewModel.uiState.collectAsStateWithLifecycle()
    val goal by goalViewModel.uiState.collectAsStateWithLifecycle()
    val launcher = launcherFor { granted -> healthSyncViewModel.onEvent(HealthSyncEvent.PermissionResult(granted)) }
    CollectWeightEffects(viewModel, onRemindersChange)
    CollectCircumferenceEffects(circumferencesViewModel.effects, onRemindersChange)
    CollectGoalEffects(goalViewModel.effects)
    CollectHealthEffects(healthSyncViewModel, viewModel, trendViewModel, launcher, onRemindersChange)
    // A saved, edited or deleted weight changes the latest reading or the month's days: the trend reloads.
    val measurements = (state as? WeightUiState.Content)?.content?.let { it.current to it.days }
    LaunchedEffect(measurements) {
        if (measurements != null) {
            trendViewModel.onEvent(WeightTrendEvent.Refresh)
            goalViewModel.onEvent(GoalEvent.Refresh)
        }
    }
    // A saved goal changes the dashed target line of the chart.
    val goalTarget = (goal as? GoalUiState.Content)?.content?.targetKg
    var firstTarget by remember { mutableStateOf(true) }
    LaunchedEffect(goalTarget) {
        if (firstTarget) firstTarget = false else trendViewModel.onEvent(WeightTrendEvent.Refresh)
    }
    OpenRequestedEntry(
        target = openEntry,
        weightReady = state is WeightUiState.Content,
        circumferencesReady = circumferences is CircumferencesUiState.Content,
        onWeightEvent = viewModel::onEvent,
        onCircumferenceEvent = circumferencesViewModel::onEvent,
    )
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onEvent(WeightEvent.Refresh)
        trendViewModel.onEvent(WeightTrendEvent.Refresh)
        goalViewModel.onEvent(GoalEvent.Refresh)
        circumferencesViewModel.onEvent(CircumferencesEvent.Refresh)
        healthSyncViewModel.onEvent(HealthSyncEvent.Refresh)
    }
    WeightScreen(
        state = state,
        onEvent = viewModel::onEvent,
        circumferences = circumferences,
        onCircumferenceEvent = circumferencesViewModel::onEvent,
        modifier = modifier,
        healthSync = healthSync,
        onHealthEvent = healthSyncViewModel::onEvent,
        trend = trend,
        onTrendEvent = trendViewModel::onEvent,
        goal = goal,
        onGoalEvent = goalViewModel::onEvent,
        onOpenHistory = onOpenHistory,
        onBack = onBack,
    )
}

/**
 * Opens the entry a tapped reminder asked for ([target], US-57) once its data is loaded. The flag is saved, so a
 * rotation or process restoration does not open it again (ADR-40).
 */
@Composable
internal fun OpenRequestedEntry(
    target: WeightEntryTarget?,
    weightReady: Boolean,
    circumferencesReady: Boolean,
    onWeightEvent: (WeightEvent) -> Unit,
    onCircumferenceEvent: (CircumferencesEvent) -> Unit,
) {
    var consumed by rememberSaveable { mutableStateOf(false) }
    val latestWeightEvent by rememberUpdatedState(onWeightEvent)
    val latestCircumferenceEvent by rememberUpdatedState(onCircumferenceEvent)
    val ready = when (target) {
        WeightEntryTarget.Weight -> weightReady
        WeightEntryTarget.Circumference -> circumferencesReady
        null -> false
    }
    LaunchedEffect(target, ready, consumed) {
        if (ready && !consumed) {
            consumed = true
            when (target) {
                WeightEntryTarget.Weight -> latestWeightEvent(WeightEvent.LogToday)
                WeightEntryTarget.Circumference -> latestCircumferenceEvent(CircumferencesEvent.OpenAdd)
                null -> Unit
            }
        }
    }
}

/**
 * Weight saves and deletes confirm with a haptic only, the change is visible (R-13); failures reject and show a
 * message. Shared by the Peso and the history routes, each with its own [viewModel].
 */
@Composable
internal fun CollectWeightEffects(viewModel: WeightViewModel, onRemindersChange: () -> Unit = {}) {
    val latestOnChanged by rememberUpdatedState(onRemindersChange)
    val failed = stringResource(Res.string.weight_delete_error)
    val offline = stringResource(Res.string.weight_delete_error_offline)
    val saveFailed = stringResource(Res.string.weight_save_error)
    val saveOffline = offlineHint("guardar")
    val appMessages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WeightEffect.DeleteFailed -> {
                    haptics.reject()
                    appMessages.show(if (effect.offline) offline else failed)
                }

                WeightEffect.Saved, WeightEffect.Deleted -> {
                    haptics.confirm()
                    latestOnChanged()
                }

                is WeightEffect.SaveFailed -> {
                    haptics.reject()
                    appMessages.show(if (effect.offline) saveOffline else saveFailed)
                }
            }
        }
    }
}

@Composable
private fun CollectHealthEffects(
    healthSyncViewModel: HealthSyncViewModel,
    viewModel: WeightViewModel,
    trendViewModel: WeightTrendViewModel,
    launcher: HealthConnectLauncher,
    onRemindersChange: () -> Unit,
) {
    val latestOnChanged by rememberUpdatedState(onRemindersChange)
    LaunchedEffect(healthSyncViewModel) {
        healthSyncViewModel.effects.collect { effect ->
            when (effect) {
                HealthSyncEffect.RequestPermissions -> launcher.requestPermissions()

                HealthSyncEffect.OpenInstall -> launcher.openInstall()

                is HealthSyncEffect.Synced -> {
                    viewModel.onEvent(WeightEvent.Refresh)
                    trendViewModel.onEvent(WeightTrendEvent.Refresh)
                    latestOnChanged()
                }
            }
        }
    }
}

// Saves and deletes confirm with a haptic only, the change is visible (R-13); failures reject and show a message.
@Composable
private fun CollectCircumferenceEffects(effects: Flow<CircumferencesEffect>, onRemindersChange: () -> Unit) {
    val latestOnChanged by rememberUpdatedState(onRemindersChange)
    val deleteFailed = stringResource(Res.string.delete_failed)
    val deleteOffline = stringResource(Res.string.delete_offline)
    val saveFailed = stringResource(Res.string.weight_save_error)
    val saveOffline = offlineHint("guardar")
    val appMessages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is CircumferencesEffect.DeleteFailed -> {
                    haptics.reject()
                    appMessages.show(if (effect.offline) deleteOffline else deleteFailed)
                }

                CircumferencesEffect.Saved, CircumferencesEffect.Deleted -> {
                    haptics.confirm()
                    latestOnChanged()
                }

                is CircumferencesEffect.SaveFailed -> {
                    haptics.reject()
                    appMessages.show(if (effect.offline) saveOffline else saveFailed)
                }
            }
        }
    }
}

@Composable
private fun CollectGoalEffects(effects: Flow<GoalEffect>) {
    val saveFailed = stringResource(Res.string.goal_target_save_error)
    val saveOffline = offlineHint("guardar")
    val appMessages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                GoalEffect.Saved -> haptics.confirm()

                is GoalEffect.SaveFailed -> {
                    haptics.reject()
                    appMessages.show(if (effect.offline) saveOffline else saveFailed)
                }
            }
        }
    }
}
