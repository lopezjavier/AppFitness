package io.github.lopezjavier.appfitness.feature.meals.defaults

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Stateful default-comidas editor: connects [MealDefaultsViewModel]; the template calls [onClose]. */
@Composable
fun MealDefaultsRoot(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MealDefaultsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MealDefaultsScreen(state, viewModel::onEvent, modifier, onClose)
}
