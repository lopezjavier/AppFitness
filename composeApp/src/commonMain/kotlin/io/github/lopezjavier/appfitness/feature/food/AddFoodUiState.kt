package io.github.lopezjavier.appfitness.feature.food

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck

/** What the add-food sheet shows. */
sealed interface AddFoodUiState {
    /** Loading the food and the meals. */
    data object Loading : AddFoodUiState

    /** The food or the meals could not be loaded; [AddFoodEvent.Retry] tries again. */
    data object Error : AddFoodUiState

    data class Content(val content: AddFoodContent) : AddFoodUiState
}

/**
 * The sheet for [food]. [amountText] is what the user typed: an amount in the food's base unit, or a count of the
 * portion [selectedPortionId] (null = raw base unit). [resolvedAmount] is the amount in base units and [preview] its
 * nutrition (0.1 precision, the UI rounds kcal), both null while [quantityError] is set. The prefill is the food's
 * serving ([food].servingAmount) or 100.
 */
data class AddFoodContent(
    val food: Food,
    val amountText: String,
    val selectedPortionId: String?,
    val slots: List<MealSlot>,
    val selectedSlotId: String?,
    val resolvedAmount: Double?,
    val preview: NutritionSnapshot?,
    val quantityError: QuantityCheck?,
    val isOffline: Boolean,
    val submitting: Boolean,
) {
    /** The add button is enabled. */
    val canSubmit: Boolean
        get() = quantityError == null && resolvedAmount != null && selectedSlotId != null && !isOffline && !submitting
}

sealed interface AddFoodEvent {
    data class AmountChanged(val text: String) : AddFoodEvent

    /** Pick a named portion, or null to type an amount in the base unit again. */
    data class PortionSelected(val portionId: String?) : AddFoodEvent

    data class SlotSelected(val slotId: String) : AddFoodEvent

    data object Submit : AddFoodEvent

    data object Retry : AddFoodEvent
}

sealed interface AddFoodEffect {
    /** The entry was logged: give haptic feedback and close back to the Diary. */
    data object Added : AddFoodEffect

    /** Logging failed; the sheet stays open with the user's input. */
    data class SubmitFailed(val error: AppError?) : AddFoodEffect
}
