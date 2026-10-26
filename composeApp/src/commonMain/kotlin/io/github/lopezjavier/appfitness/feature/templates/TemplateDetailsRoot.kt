package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_add_failed
import io.github.lopezjavier.appfitness.composeapp.resources.template_manage_failed
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful details (and editor) of the comida [templateId]: connects [TemplateDetailsViewModel]. It shows the read-only
 * [TemplateDetailsScreen] and, after "Editar" (or "Añadir alimento"), the [TemplateEditorScreen] over the same draft;
 * leaving the editor (saved or discarded) comes back to the details. [onClose] is the details' back arrow and
 * [onDelete] runs, with haptic feedback, after the comida was deleted. The comida is reloaded when the screen
 * resumes; the draft is kept. [onAddFood] opens the food picker. A food it returns arrives as [pickedFood] (see
 * [pickedFoodOf]); it is handed to the draft and [onConsumePickedFood] then clears it so it is applied once.
 * [slotId] (null: the meal of the time of day) and [date] (ISO yyyy-MM-dd) are where "Añadir" logs the foods, and
 * [onAdd] runs, with haptic feedback, after it did. Failures go to the app messages (R-28).
 */
@Composable
@Suppress("LongParameterList", "LongMethod") // One callback per navigation event; the effects of both screens.
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
    var editing by rememberSaveable { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val haptic = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val actionFailedText = stringResource(Res.string.template_manage_failed)
    val addFailedText = stringResource(Res.string.template_details_add_failed)
    val currentActionFailed by rememberUpdatedState(actionFailedText)
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
    val addFailed = (state as? TemplateDetailsUiState.Content)?.addFailed == true
    LaunchedEffect(addFailed) {
        if (addFailed) {
            haptic.reject()
            messages.show(addFailedText)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // The editor closes itself (with the one Confirm haptic) once it sees the save went through.
                TemplateDetailsEffect.Saved -> {
                    saved = true
                    // The saved comida replaces the draft: the details show the new name and foods.
                    viewModel.onEvent(TemplateDetailsEvent.Refresh)
                }

                TemplateDetailsEffect.Added -> {
                    haptic.confirm()
                    currentOnAdded()
                }

                TemplateDetailsEffect.Deleted -> {
                    haptic.confirm()
                    currentOnDelete()
                }

                is TemplateDetailsEffect.ActionFailed -> {
                    haptic.reject()
                    messages.show(currentActionFailed)
                }

                TemplateDetailsEffect.OpenFoodPicker -> currentOnAddFood()
            }
        }
    }
    if (editing) {
        TemplateEditorScreen(
            state = state,
            onEvent = viewModel::onEvent,
            onClose = {
                viewModel.onEvent(TemplateDetailsEvent.Discard)
                saved = false
                editing = false
            },
            modifier = modifier,
            saved = saved,
        )
    } else {
        TemplateDetailsScreen(
            state = state,
            onEvent = viewModel::onEvent,
            onBack = onClose,
            onEdit = { editing = true },
            modifier = modifier,
        )
    }
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
