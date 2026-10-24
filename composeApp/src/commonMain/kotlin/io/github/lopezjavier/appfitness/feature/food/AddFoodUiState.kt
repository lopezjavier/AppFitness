package io.github.lopezjavier.appfitness.feature.food

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.LabelNutrient
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import io.github.lopezjavier.appfitness.domain.usecase.BuildLabelNutrients
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
    val micronutrients: MicronutrientsState = MicronutrientsState.Loading,
    val pick: Boolean = false,
) {
    /** The "Editar" action is shown: only for the user's own manual foods, never for Open Food Facts ones. */
    val showEdit: Boolean get() = food.isManual

    /** The "Editar" action is enabled: saving an edit needs the server. */
    val canEdit: Boolean get() = showEdit && !isOffline

    /** The nutrient rows in label order: the four columns of the food plus the extras once they are loaded. */
    val labelNutrients: List<LabelNutrient>
        get() = BuildLabelNutrients(
            food.nutrition,
            (micronutrients as? MicronutrientsState.Shown)?.micronutrients ?: Micronutrients.Empty,
        )

    /**
     * The add button is enabled. In [pick] mode (choosing a food for a comida draft) no meal and no server are needed:
     * nothing is logged.
     */
    val canSubmit: Boolean
        get() = quantityError == null && resolvedAmount != null && !submitting &&
            (pick || (selectedSlotId != null && !isOffline))
}

/**
 * The optional extra-nutrients section of the sheet. It never affects [AddFoodContent.canSubmit]: [Loading] renders
 * nothing, [Hidden] is an empty result or a silent failure (also offline) and [Shown] carries the rows.
 */
sealed interface MicronutrientsState {
    data object Loading : MicronutrientsState

    data object Hidden : MicronutrientsState

    data class Shown(val micronutrients: Micronutrients) : MicronutrientsState
}

sealed interface AddFoodEvent {
    data class AmountChanged(val text: String) : AddFoodEvent

    /** Pick a named portion, or null to type an amount in the base unit again. */
    data class PortionSelected(val portionId: String?) : AddFoodEvent

    data class SlotSelected(val slotId: String) : AddFoodEvent

    data object Submit : AddFoodEvent

    data object Retry : AddFoodEvent

    /** Reload the food without the loading state, e.g. after it was edited. */
    data object Refresh : AddFoodEvent
}

sealed interface AddFoodEffect {
    /** The entry was logged: give haptic feedback and close back to the Diary. */
    data object Added : AddFoodEffect

    /** Pick mode: the user confirmed [foodId] with [amount] (base unit) and a [portionLabel]; nothing is logged. */
    data class Picked(val foodId: String, val amount: Double, val portionLabel: String?) : AddFoodEffect

    /** Logging failed; the sheet stays open with the user's input. */
    data class SubmitFailed(val error: AppError?) : AddFoodEffect
}
