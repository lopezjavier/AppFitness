package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful meals editor: connects [MealSlotsEditorViewModel] and calls [onClose] when saved or the user leaves;
 * [onOpenDefaults] opens the default comidas.
 */
@Composable
fun MealSlotsRoot(
    onClose: () -> Unit,
    onOpenDefaults: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MealSlotsEditorViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MealSlotsEffect.Close -> currentOnClose()
            }
        }
    }
    MealSlotsScreen(state, viewModel::onEvent, modifier, onOpenDefaults)
}
