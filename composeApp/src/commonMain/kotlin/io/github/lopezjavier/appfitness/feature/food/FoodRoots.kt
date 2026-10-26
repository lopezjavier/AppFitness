package io.github.lopezjavier.appfitness.feature.food

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
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_submit_error
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_save_error
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.domain.model.NewFoodDraftCodec
import io.github.lopezjavier.appfitness.domain.port.PhotoCapture
import io.github.lopezjavier.appfitness.feature.food.photo.AmountPhotoEffect
import io.github.lopezjavier.appfitness.feature.food.photo.AmountPhotoFlow
import io.github.lopezjavier.appfitness.feature.food.photo.AmountPhotoViewModel
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoEffect
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoEvent
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoFlow
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoViewModel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful food search: connects [FoodSearchViewModel] for the meal [slotId] (null = none preselected) and the ISO day
 * [date]. [onOpenFood] receives the id of the food to log (a tapped row or a scanned product; the add-food sheet is
 * opened by navigation, so it survives process death). [onCreateFood] opens the create-food form with the scanned
 * barcode and/or the typed name, or the JSON `NewFoodDraft` of a photo estimate (null prefill and name = empty form).
 * [onOpenTemplate] opens the read-only details of a comida, [onBack] is the top bar's arrow; the list is reloaded when
 * the search is shown again (the editor may have changed it). In [pick] mode the search chooses a food for a comida
 * draft (no Comidas tab; [slotId] and [date] are irrelevant).
 */
@Suppress("LongParameterList") // One callback per navigation event of the search.
@Composable
fun FoodSearchRoot(
    slotId: String?,
    date: String,
    onOpenFood: (foodId: String) -> Unit,
    onCreateFood: (barcode: String?, name: String?, prefill: String?) -> Unit,
    onOpenTemplate: (templateId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pick: Boolean = false,
    viewModel: FoodSearchViewModel = koinViewModel(parameters = { parametersOf(slotId.orEmpty(), date, pick) }),
    photoViewModel: FoodPhotoViewModel = koinViewModel(),
    photoCapture: PhotoCapture = koinInject(),
) {
    val photoState by photoViewModel.uiState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var scanFailed by remember { mutableStateOf(false) }
    var templateManageFailed by remember { mutableStateOf(false) }
    val currentOpenFood by rememberUpdatedState(onOpenFood)
    val currentCreateFood by rememberUpdatedState(onCreateFood)
    val currentOpenTemplate by rememberUpdatedState(onOpenTemplate)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(FoodSearchEvent.RefreshTemplates) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FoodSearchEffect.OpenFood -> currentOpenFood(effect.food.id)

                is FoodSearchEffect.OpenCreateFood -> currentCreateFood(effect.barcode, effect.name, null)

                FoodSearchEffect.ScanFailed -> scanFailed = true

                is FoodSearchEffect.OpenTemplateDetails -> currentOpenTemplate(effect.templateId)

                FoodSearchEffect.TemplateManageFailed -> templateManageFailed = true

                // The star already rolled back.
                FoodSearchEffect.FavouriteFailed -> Unit
            }
        }
    }
    LaunchedEffect(photoViewModel) {
        photoViewModel.effects.collect { effect ->
            when (effect) {
                is FoodPhotoEffect.OpenPrefilledForm ->
                    currentCreateFood(null, null, NewFoodDraftCodec.encode(effect.draft))

                FoodPhotoEffect.OpenEmptyForm -> currentCreateFood(null, null, null)
            }
        }
    }
    FoodSearchScreen(
        state = state,
        onEvent = {
            scanFailed = false
            templateManageFailed = false
            viewModel.onEvent(it)
        },
        onBack = onBack,
        modifier = modifier,
        scanFailed = scanFailed,
        templateManageFailed = templateManageFailed,
        onPhotoClick = { photoViewModel.onEvent(FoodPhotoEvent.Open) },
    )
    FoodPhotoFlow(photoState, photoViewModel::onEvent, photoCapture::readJpeg)
}

/**
 * Stateful create-food form: connects [CreateFoodViewModel], prefilled with [barcode] and/or [name], or with the JSON
 * `NewFoodDraft` [prefill], the first time.
 * [onSave] receives the id of the new food (the add-food sheet opens for it).
 */
@Composable
fun CreateFoodRoot(
    barcode: String?,
    name: String?,
    onSave: (foodId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    prefill: String? = null,
    viewModel: CreateFoodViewModel =
        koinViewModel(parameters = { parametersOf(barcode.orEmpty(), name.orEmpty(), "", prefill.orEmpty()) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FoodFormEffects(viewModel.effects, onSave)
    CreateFoodScreen(state, viewModel::onEvent, onBack, modifier)
}

/**
 * Stateful add-food sheet for [foodId]: connects [AddFoodViewModel]. [slotId] is the meal tapped in the Diary (null =
 * chosen by the time of day) and [date] its ISO day. [onAdd] runs after the entry was logged (with haptic
 * feedback), [onDismiss] when the sheet is dismissed. In [pick] mode nothing is logged: "Añadir" hands the chosen
 * amount to [onPick] (a comida draft) instead.
 */
@Composable
@Suppress("LongParameterList") // One callback per outcome of the sheet.
fun AddFoodRoot(
    foodId: String,
    slotId: String?,
    date: String,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
    pick: Boolean = false,
    onPick: (AddFoodEffect.Picked) -> Unit = {},
    viewModel: AddFoodViewModel =
        koinViewModel(parameters = { parametersOf(foodId, slotId.orEmpty(), date, pick) }),
    amountPhotoViewModel: AmountPhotoViewModel =
        koinViewModel(key = "amount_photo_$foodId", parameters = { parametersOf(foodId) }),
    photoCapture: PhotoCapture = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val amountPhotoState by amountPhotoViewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val submitError = stringResource(Res.string.add_food_submit_error)
    val currentSubmitError by rememberUpdatedState(submitError)
    val currentOnAdded by rememberUpdatedState(onAdd)
    val currentOnPicked by rememberUpdatedState(onPick)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AddFoodEffect.Added -> {
                    haptic.confirm()
                    currentOnAdded()
                }

                is AddFoodEffect.SubmitFailed -> {
                    haptic.reject()
                    messages.show(currentSubmitError)
                }

                is AddFoodEffect.Picked -> currentOnPicked(effect)
            }
        }
    }
    LaunchedEffect(amountPhotoViewModel) {
        amountPhotoViewModel.effects.collect { effect ->
            when (effect) {
                is AmountPhotoEffect.AmountEstimated ->
                    viewModel.onEvent(
                        AddFoodEvent.AmountEstimated(effect.amount, effect.baselineAmountText, effect.confidence),
                    )
            }
        }
    }
    AddFoodSheet(
        state = state,
        onEvent = viewModel::onEvent,
        onDismiss = onDismiss,
        modifier = modifier,
        onEdit = onEdit,
        amountPhoto = amountPhotoState,
        onAmountPhotoEvent = amountPhotoViewModel::onEvent,
    )
    AmountPhotoFlow(amountPhotoState, amountPhotoViewModel::onEvent, photoCapture::readJpeg)
}

/**
 * Stateful edit form of the manual food [foodId] (US-47): connects [CreateFoodViewModel] in edit mode. [onSaved] runs
 * after "Guardar"; leaving without it writes nothing.
 */
@Composable
fun EditFoodRoot(
    foodId: String,
    onSave: (foodId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateFoodViewModel =
        koinViewModel(key = "edit_$foodId", parameters = { parametersOf("", "", foodId, "") }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FoodFormEffects(viewModel.effects, onSave)
    CreateFoodScreen(state, viewModel::onEvent, onBack, modifier)
}

/** Saved opens the food's sheet; a failed save is a message and one Reject vibration (R-28, R-29). */
@Composable
private fun FoodFormEffects(effects: Flow<CreateFoodEffect>, onSave: (foodId: String) -> Unit) {
    val haptic = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    val failedText = stringResource(Res.string.create_food_save_error)
    val currentFailedText by rememberUpdatedState(failedText)
    val currentOnSaved by rememberUpdatedState(onSave)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is CreateFoodEffect.Saved -> currentOnSaved(effect.foodId)

                CreateFoodEffect.SaveFailed -> {
                    haptic.reject()
                    messages.show(currentFailedText)
                }
            }
        }
    }
}
