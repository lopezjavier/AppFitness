package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.domain.port.Clock
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful meals editor for the Diary day [date] (ISO yyyy-MM-dd): connects [MealSlotsEditorViewModel] and calls
 * [onClose] when the structure was saved or the user chose to leave without saving.
 */
@Composable
fun MealSlotsRoot(
    date: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MealSlotsEditorViewModel = koinViewModel(parameters = { parametersOf(date) }),
    clock: Clock = koinInject(),
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
    MealSlotsScreen(state, viewModel::onEvent, modifier, viewedDayIsToday = date == clock.today().toString())
}
