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
import io.github.lopezjavier.appfitness.composeapp.resources.weight_delete_error
import io.github.lopezjavier.appfitness.composeapp.resources.weight_delete_error_offline
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stateful weight screen: connects [WeightViewModel]; it reloads on resume so a change in Preferencias shows. */
@Composable
fun WeightRoot(modifier: Modifier = Modifier, viewModel: WeightViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val failed = stringResource(Res.string.weight_delete_error)
    val offline = stringResource(Res.string.weight_delete_error_offline)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WeightEffect.DeleteFailed ->
                    snackbarHost.showSnackbar(if (effect.offline) offline else failed, duration = SnackbarDuration.Long)
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(WeightEvent.Refresh) }
    WeightScreen(state, viewModel::onEvent, modifier, snackbarHost)
}
