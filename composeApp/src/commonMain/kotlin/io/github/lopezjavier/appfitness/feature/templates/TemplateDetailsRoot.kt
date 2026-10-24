package io.github.lopezjavier.appfitness.feature.templates

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful details (and editor) of the comida [templateId]: connects [TemplateDetailsViewModel]. [onClose] runs, with
 * haptic feedback, after the comida was saved or deleted so the details can be left. The comida is reloaded when the
 * screen resumes; the draft is kept. [onAddFood] opens the food picker. A food it returns arrives as [pickedFood]
 * (see [pickedFoodOf]); it is handed to the draft and [onConsumePickedFood] then clears it so it is applied once.
 */
@Composable
@Suppress("LongParameterList") // One callback per navigation event plus the picker result.
fun TemplateDetailsRoot(
    templateId: String,
    onClose: () -> Unit,
    onAddFood: () -> Unit,
    modifier: Modifier = Modifier,
    pickedFood: List<String>? = null,
    onConsumePickedFood: () -> Unit = {},
    viewModel: TemplateDetailsViewModel = koinViewModel(parameters = { parametersOf(templateId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var actionFailed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnAddFood by rememberUpdatedState(onAddFood)
    val currentOnConsumed by rememberUpdatedState(onConsumePickedFood)
    LaunchedEffect(pickedFood) {
        pickedFoodOf(pickedFood)?.let {
            viewModel.onEvent(it)
            currentOnConsumed()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(TemplateDetailsEvent.Refresh) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TemplateDetailsEffect.Saved, TemplateDetailsEffect.Deleted -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentOnClose()
                }

                is TemplateDetailsEffect.ActionFailed -> actionFailed = true

                TemplateDetailsEffect.OpenFoodPicker -> currentOnAddFood()
            }
        }
    }
    TemplateDetailsScreen(
        state = state,
        onEvent = {
            actionFailed = false
            viewModel.onEvent(it)
        },
        modifier = modifier,
        actionFailed = actionFailed,
    )
}

/** The key under which the picker leaves its result in the editor's back-stack entry. */
const val PICKED_FOOD_KEY = "picked_food"

/** The picker result as a savable list: food id, base-unit amount and portion label (blank: none). */
fun pickedFoodResult(foodId: String, amount: Double, portionLabel: String?): ArrayList<String> =
    arrayListOf(foodId, amount.toString(), portionLabel.orEmpty())

private const val PICKED_SIZE = 3

/** The event for a picker result written by [pickedFoodResult]; null when there is none (or it is malformed). */
internal fun pickedFoodOf(result: List<String>?): TemplateDetailsEvent.FoodPicked? {
    val amount = result?.takeIf { it.size == PICKED_SIZE }?.get(1)?.toDoubleOrNull() ?: return null
    return TemplateDetailsEvent.FoodPicked(result[0], amount, result[2].ifEmpty { null })
}
