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
@Composable
fun WeightRoot(
    modifier: Modifier = Modifier,
    viewModel: WeightViewModel = koinViewModel(),
    circumferencesViewModel: CircumferencesViewModel = koinViewModel(),
    healthSyncViewModel: HealthSyncViewModel = koinViewModel(),
    launcherFor: @Composable ((weightReadGranted: Boolean) -> Unit) -> HealthConnectLauncher =
        { rememberHealthConnectLauncher(it) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val circumferences by circumferencesViewModel.uiState.collectAsStateWithLifecycle()
    val healthSync by healthSyncViewModel.uiState.collectAsStateWithLifecycle()
    val launcher = launcherFor { granted -> healthSyncViewModel.onEvent(HealthSyncEvent.PermissionResult(granted)) }
    val snackbarHost = remember { SnackbarHostState() }
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
                is HealthSyncEffect.Synced -> viewModel.onEvent(WeightEvent.Refresh)
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onEvent(WeightEvent.Refresh)
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
        snackbarHost = snackbarHost,
    )
}
