package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful "Historial de peso": its own [WeightViewModel] (one per route entry) drives the calendar and the day sheet;
 * it reloads on resume and gives the haptic and message of its saves and deletes like the Peso screen.
 */
// The view model's one-shot effects are collected by a helper shared with the Peso route.
@Suppress("ViewModelForwarding")
@Composable
fun WeightHistoryRoot(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRemindersChange: () -> Unit = {},
    viewModel: WeightViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CollectWeightEffects(viewModel, onRemindersChange)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(WeightEvent.Refresh) }
    WeightHistoryScreen(state, viewModel::onEvent, onBack, modifier)
}
