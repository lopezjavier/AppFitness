package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful meals editor: connects [MealSlotsEditorViewModel]; the editor template calls [onClose] once the structure
 * is saved or the user leaves. [onOpenDefaults] opens the default comidas.
 */
@Composable
fun MealSlotsRoot(
    onClose: () -> Unit,
    onOpenDefaults: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MealSlotsEditorViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MealSlotsScreen(state, viewModel::onEvent, modifier, onOpenDefaults, onClose)
}
