package io.github.lopezjavier.appfitness.feature.templates

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck

/**
 * One food of the comida, identified by its [index] (the identity of a row, stable for the whole draft): the position
 * in the saved comida for a saved food, and the saved count plus the order of arrival for a food [added] in the editor
 * (the same food may appear twice). [amountText] is the typed amount in the base [unit]; [quantityError] is set when it
 * is not valid. [kcal] and the macros are the nutrition of that amount (0 while it is invalid). [portionLabel] is kept
 * only while the amount is untouched. [removable] is false for the last food left.
 */
data class DetailsRow(
    val index: Int,
    val foodId: String,
    val name: String,
    val unit: String,
    val amountText: String,
    val quantityError: QuantityCheck?,
    val portionLabel: String?,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val removable: Boolean,
    val added: Boolean = false,
)

/**
 * The food sheet: the tapped [row] as previewed with the amount typed in the sheet (not yet applied to the comida),
 * its catalogue [food] and its [micronutrients] once known (null: none to show). [canSave] says the typed amount is
 * valid, so the sheet's confirm button is enabled.
 */
data class OpenFood(val row: DetailsRow, val food: Food, val micronutrients: Micronutrients?, val canSave: Boolean)

/** What the editor of a comida shows. */
sealed interface TemplateDetailsUiState {
    data object Loading : TemplateDetailsUiState

    /** The comida could not be read (or no longer exists); the screen offers a retry. */
    data object Error : TemplateDetailsUiState

    /**
     * The comida as drafted: the draft [name] (with [nameError] when it is too long or taken by another comida; a blank
     * name has no hint but cannot be saved), the totals of the foods left and one of [rows] per food. Nothing is stored
     * until "Guardar": [isDirty] says the draft differs from the saved comida and [canSave] that it can be saved now
     * (valid name and amounts, a food left, online, not busy). [openFood] is the food sheet. Everything survives
     * process death (ADR-40).
     */
    data class Content(
        val name: String,
        val nameError: TemplateNameError?,
        val totalKcal: Double,
        val totalProteinG: Double,
        val totalCarbsG: Double,
        val totalFatG: Double,
        val rows: List<DetailsRow>,
        val canSave: Boolean,
        val isDirty: Boolean,
        val isOffline: Boolean,
        val busy: Boolean,
        val openFood: OpenFood?,
    ) : TemplateDetailsUiState {
        /** The index of the food whose sheet is open, if any. */
        val openFoodIndex: Int?
            get() = openFood?.row?.index
    }
}

sealed interface TemplateDetailsEvent {
    /** A tap on a food: opens its sheet. */
    data class OpenFood(val index: Int) : TemplateDetailsEvent

    /** Closes the food sheet; the amount typed in it is discarded. */
    data object DismissFood : TemplateDetailsEvent

    /** The amount typed in the food sheet: a pending value only, applied by [SaveFood]. */
    data class FoodAmountChanged(val text: String) : TemplateDetailsEvent

    /** The food sheet's confirm: applies the pending amount to the comida draft and closes the sheet. */
    data object SaveFood : TemplateDetailsEvent

    /** Sets the committed amount of a food in the draft. */
    data class AmountChanged(val index: Int, val text: String) : TemplateDetailsEvent

    /** Removes a food from the draft (swipe or food sheet); ignored for the last food. */
    data class RemoveFood(val index: Int) : TemplateDetailsEvent

    /** The name field changed: a draft change only. */
    data class NameChanged(val text: String) : TemplateDetailsEvent

    /** "Añadir alimento": asks the screen to open the food picker ([TemplateDetailsEffect.OpenFoodPicker]). */
    data object AddFoodClicked : TemplateDetailsEvent

    /** The picker returned a food: it is appended to the draft with [amount] (base unit) and [portionLabel]. */
    data class FoodPicked(val foodId: String, val amount: Double, val portionLabel: String?) : TemplateDetailsEvent

    /** "Guardar": stores name, removals, additions and amounts together (one request). */
    data object SaveClicked : TemplateDetailsEvent

    /** "Eliminar comida": deletes at once (online only). */
    data object Delete : TemplateDetailsEvent

    /** Reload after an error (shows the loading state); the draft is kept. */
    data object Retry : TemplateDetailsEvent

    /** Silent reload, e.g. on resume; the draft is kept. */
    data object Refresh : TemplateDetailsEvent
}

sealed interface TemplateDetailsEffect {
    /** The draft was saved: give haptic feedback and leave the editor. */
    data object Saved : TemplateDetailsEffect

    /** The comida was deleted: leave the editor. */
    data object Deleted : TemplateDetailsEffect

    /** Open the food picker (search in pick mode); its result comes back as [TemplateDetailsEvent.FoodPicked]. */
    data object OpenFoodPicker : TemplateDetailsEffect

    /** A save, delete or food lookup was refused for another reason than a taken name; the draft is kept. */
    data class ActionFailed(val error: AppError?) : TemplateDetailsEffect
}
