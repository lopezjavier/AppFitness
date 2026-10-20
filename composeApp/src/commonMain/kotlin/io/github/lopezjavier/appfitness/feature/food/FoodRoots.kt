package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful food search: connects [FoodSearchViewModel]. [onOpenFood] receives the id of the food whose row was tapped
 * (the add-food sheet is opened by navigation, so it survives process death).
 */
@Composable
fun FoodSearchRoot(
    onOpenFood: (foodId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FoodSearchViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOpenFood by rememberUpdatedState(onOpenFood)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FoodSearchEffect.OpenFood -> currentOpenFood(effect.food.id)
                FoodSearchEffect.FavouriteFailed -> Unit // The star already rolled back.
            }
        }
    }
    FoodSearchScreen(state, viewModel::onEvent, modifier)
}

/**
 * Stateful add-food sheet for [foodId]: connects [AddFoodViewModel]. [slotId] is the meal tapped in the Diary (null =
 * chosen by the time of day) and [date] its ISO day. [onAdd] runs after the entry was logged (with haptic
 * feedback), [onDismiss] when the sheet is dismissed.
 */
@Composable
fun AddFoodRoot(
    foodId: String,
    slotId: String?,
    date: String,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddFoodViewModel = koinViewModel(parameters = { parametersOf(foodId, slotId.orEmpty(), date) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var submitFailed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val currentOnAdded by rememberUpdatedState(onAdd)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AddFoodEffect.Added -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentOnAdded()
                }

                is AddFoodEffect.SubmitFailed -> submitFailed = true
            }
        }
    }
    AddFoodSheet(
        state = state,
        onEvent = {
            if (it == AddFoodEvent.Submit) submitFailed = false
            viewModel.onEvent(it)
        },
        onDismiss = onDismiss,
        modifier = modifier,
        submitFailed = submitFailed,
    )
}
