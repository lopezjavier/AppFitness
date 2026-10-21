package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.material3.SnackbarHostState
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
 * Stateful food search: connects [FoodSearchViewModel] for the meal [slotId] (null = none preselected) and the ISO day
 * [date]. [onOpenFood] receives the id of the food to log (a tapped row or a scanned product; the add-food sheet is
 * opened by navigation, so it survives process death). [onCreateFood] opens the create-food form with the scanned
 * barcode and/or the typed name. [onAdd] runs after a comida was logged (with haptic feedback). [onOpenTemplate] opens
 * the details of a comida; the list is reloaded when the search is shown again (the editor may have changed it).
 */
@Suppress("LongParameterList") // One callback per navigation event of the search.
@Composable
fun FoodSearchRoot(
    slotId: String?,
    date: String,
    onOpenFood: (foodId: String) -> Unit,
    onCreateFood: (barcode: String?, name: String?) -> Unit,
    onAdd: () -> Unit,
    onOpenTemplate: (templateId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FoodSearchViewModel = koinViewModel(parameters = { parametersOf(slotId.orEmpty(), date) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var scanFailed by remember { mutableStateOf(false) }
    var templateFailed by remember { mutableStateOf(false) }
    var templateManageFailed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val currentOpenFood by rememberUpdatedState(onOpenFood)
    val currentCreateFood by rememberUpdatedState(onCreateFood)
    val currentTemplateAdded by rememberUpdatedState(onAdd)
    val currentOpenTemplate by rememberUpdatedState(onOpenTemplate)
    val snackbarHost = remember { SnackbarHostState() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(FoodSearchEvent.RefreshTemplates) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FoodSearchEffect.OpenFood -> currentOpenFood(effect.food.id)

                is FoodSearchEffect.OpenCreateFood -> currentCreateFood(effect.barcode, effect.name)

                FoodSearchEffect.ScanFailed -> scanFailed = true

                FoodSearchEffect.TemplateAdded -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentTemplateAdded()
                }

                is FoodSearchEffect.TemplateFailed -> templateFailed = true

                is FoodSearchEffect.OpenTemplateDetails -> currentOpenTemplate(effect.templateId)

                FoodSearchEffect.TemplateManageFailed -> templateManageFailed = true

                // The star already rolled back.
                FoodSearchEffect.FavouriteFailed -> Unit
            }
        }
    }
    FoodSearchScreen(
        state = state,
        onEvent = {
            scanFailed = false
            templateFailed = false
            templateManageFailed = false
            viewModel.onEvent(it)
        },
        modifier = modifier,
        scanFailed = scanFailed,
        templateFailed = templateFailed,
        templateManageFailed = templateManageFailed,
        snackbarHost = snackbarHost,
    )
}

/**
 * Stateful create-food form: connects [CreateFoodViewModel], prefilled with [barcode] and/or [name] the first time.
 * [onSave] receives the id of the new food (the add-food sheet opens for it).
 */
@Composable
fun CreateFoodRoot(
    barcode: String?,
    name: String?,
    onSave: (foodId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateFoodViewModel = koinViewModel(parameters = { parametersOf(barcode.orEmpty(), name.orEmpty()) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSave)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CreateFoodEffect.Saved -> currentOnSaved(effect.foodId)

                // The state already shows the inline error.
                CreateFoodEffect.SaveFailed -> Unit
            }
        }
    }
    CreateFoodScreen(state, viewModel::onEvent, modifier)
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
