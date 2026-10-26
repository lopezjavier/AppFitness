package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful details (and editor) of the comida [templateId]: connects [TemplateDetailsViewModel]. [onClose] and
 * [onDelete] run, with haptic feedback, after the comida was saved or deleted so the editor can be left. The comida
 * is reloaded when the screen resumes; the draft is kept. [onAddFood] opens the food picker. A food it returns arrives
 * as [pickedFood] (see [pickedFoodOf]); it is handed to the draft and [onConsumePickedFood] then clears it so it is
 * applied once. [slotId] (null: the meal of the time of day) and [date] (ISO yyyy-MM-dd) are where
 * "Añadir" logs the foods, and [onAdd] runs, with haptic feedback, after it did.
 */
@Composable
@Suppress("LongParameterList") // One callback per navigation event plus the picker result.
fun TemplateDetailsRoot(
    templateId: String,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onAddFood: () -> Unit,
    slotId: String?,
    date: String,
    modifier: Modifier = Modifier,
    pickedFood: List<String>? = null,
    onConsumePickedFood: () -> Unit = {},
    onAdd: () -> Unit = {},
    viewModel: TemplateDetailsViewModel =
        koinViewModel(parameters = { parametersOf(templateId, slotId.orEmpty(), date) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var actionFailed by remember { mutableStateOf(false) }
    val haptic = LocalAppHaptics.current
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnAddFood by rememberUpdatedState(onAddFood)
    val currentOnAdded by rememberUpdatedState(onAdd)
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
                TemplateDetailsEffect.Saved -> {
                    haptic.confirm()
                    currentOnClose()
                }

                TemplateDetailsEffect.Added -> {
                    haptic.confirm()
                    currentOnAdded()
                }

                TemplateDetailsEffect.Deleted -> {
                    haptic.confirm()
                    currentOnDelete()
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
