package io.github.lopezjavier.appfitness.feature.checkin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Stateful weekly check-in: connects [CheckInViewModel] to the stateless [CheckInScreen]. */
@Composable
fun CheckInRoot(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: CheckInViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CheckInScreen(state, viewModel::onEvent, onClose, modifier, viewModel.effects)
}
