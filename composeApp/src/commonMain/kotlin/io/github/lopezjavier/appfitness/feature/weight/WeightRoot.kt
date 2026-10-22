package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_delete_error
import io.github.lopezjavier.appfitness.composeapp.resources.circumference_delete_error_offline
import io.github.lopezjavier.appfitness.composeapp.resources.weight_delete_error
import io.github.lopezjavier.appfitness.composeapp.resources.weight_delete_error_offline
import io.github.lopezjavier.appfitness.platform.HealthConnectLauncher
import io.github.lopezjavier.appfitness.platform.rememberHealthConnectLauncher
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stateful weight screen: connects [WeightViewModel]; it reloads on resume so a change in Preferencias shows. */
// The view models' one-shot effects are collected by a helper, so it receives them.
@Suppress("ViewModelForwarding")
@Composable
fun WeightRoot(
    modifier: Modifier = Modifier,
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
    val snackbarHost = remember { SnackbarHostState() }
    CollectWeightEffects(
        viewModel,
        circumferencesViewModel,
        healthSyncViewModel,
        trendViewModel,
        launcher,
        snackbarHost,
    )
    // A saved, edited or deleted weight changes the latest reading or the month's days: the trend reloads.
    val measurements = (state as? WeightUiState.Content)?.content?.let { it.current to it.days }
    LaunchedEffect(measurements) {
        if (measurements != null) {
            trendViewModel.onEvent(WeightTrendEvent.Refresh)
            goalViewModel.onEvent(GoalEvent.Refresh)
        }
    }
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
        snackbarHost = snackbarHost,
    )
}

// Collects the one-shot effects of the screen's view models: delete errors as snackbars, Health Connect requests.
// The effects of all four view models are collected in one place, so they are passed whole.
@Suppress("LongParameterList")
@Composable
private fun CollectWeightEffects(
    viewModel: WeightViewModel,
    circumferencesViewModel: CircumferencesViewModel,
    healthSyncViewModel: HealthSyncViewModel,
    trendViewModel: WeightTrendViewModel,
    launcher: HealthConnectLauncher,
    snackbarHost: SnackbarHostState,
) {
    val failed = stringResource(Res.string.weight_delete_error)
    val offline = stringResource(Res.string.weight_delete_error_offline)
    val circumferenceFailed = stringResource(Res.string.circumference_delete_error)
    val circumferenceOffline = stringResource(Res.string.circumference_delete_error_offline)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WeightEffect.DeleteFailed ->
                    snackbarHost.showSnackbar(if (effect.offline) offline else failed, duration = SnackbarDuration.Long)
            }
        }
    }
    LaunchedEffect(circumferencesViewModel) {
        circumferencesViewModel.effects.collect { effect ->
            when (effect) {
                is CircumferencesEffect.DeleteFailed -> snackbarHost.showSnackbar(
                    if (effect.offline) circumferenceOffline else circumferenceFailed,
                    duration = SnackbarDuration.Long,
                )
            }
        }
    }
    LaunchedEffect(healthSyncViewModel) {
        healthSyncViewModel.effects.collect { effect ->
            when (effect) {
                HealthSyncEffect.RequestPermissions -> launcher.requestPermissions()

                HealthSyncEffect.OpenInstall -> launcher.openInstall()

                is HealthSyncEffect.Synced -> {
                    viewModel.onEvent(WeightEvent.Refresh)
                    trendViewModel.onEvent(WeightTrendEvent.Refresh)
                }
            }
        }
    }
}
