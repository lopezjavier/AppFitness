package io.github.lopezjavier.appfitness.feature.meals.defaults

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Stateful default-comidas editor: connects [MealDefaultsViewModel] and calls [onClose] when saved or left. */
@Composable
fun MealDefaultsRoot(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MealDefaultsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MealDefaultsEffect.Close -> currentOnClose()
            }
        }
    }
    MealDefaultsScreen(state, viewModel::onEvent, modifier)
}
