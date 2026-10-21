package io.github.lopezjavier.appfitness.feature.food

import io.github.lopezjavier.appfitness.domain.model.NewFoodErrors

/** A portion row of the form; both values are the text the user typed. */
data class PortionDraft(val label: String = "", val amount: String = "")

/**
 * State of the create-food form. All numbers are the typed text (a comma or a point is accepted). [errors] only holds
 * the problems of fields the user has already edited; [canSave] is false while anything is invalid, while saving and
 * offline. [baseUnit] is "g" or "ml"; [barcode] is read-only.
 */
data class CreateFoodUiState(
    val name: String = "",
    val brand: String = "",
    val baseUnit: String = "g",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val saturatedFat: String = "",
    val sugars: String = "",
    val fiber: String = "",
    val salt: String = "",
    val barcode: String? = null,
    val portions: List<PortionDraft> = emptyList(),
    val errors: NewFoodErrors = NewFoodErrors(),
    val canSave: Boolean = false,
    val canAddPortion: Boolean = true,
    val isSaving: Boolean = false,
    val isOffline: Boolean = false,
    val saveFailed: Boolean = false,
)

/** The label fields (saturated fat, sugars, fiber, salt) are optional: blank means not typed. */
enum class CreateFoodField { Name, Brand, Kcal, Protein, Carbs, Fat, SaturatedFat, Sugars, Fiber, Salt }

sealed interface CreateFoodEvent {
    data class FieldChanged(val field: CreateFoodField, val text: String) : CreateFoodEvent

    /** [unit] is "g" or "ml". */
    data class UnitSelected(val unit: String) : CreateFoodEvent

    data object AddPortion : CreateFoodEvent

    data class RemovePortion(val index: Int) : CreateFoodEvent

    data class PortionLabelChanged(val index: Int, val text: String) : CreateFoodEvent

    data class PortionAmountChanged(val index: Int, val text: String) : CreateFoodEvent

    data object Save : CreateFoodEvent
}

sealed interface CreateFoodEffect {
    /** The food was created: open its add-food sheet. */
    data class Saved(val foodId: String) : CreateFoodEffect

    data object SaveFailed : CreateFoodEffect
}
