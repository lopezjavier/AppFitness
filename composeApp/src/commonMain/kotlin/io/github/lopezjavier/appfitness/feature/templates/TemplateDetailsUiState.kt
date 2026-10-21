package io.github.lopezjavier.appfitness.feature.templates

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck
import io.github.lopezjavier.appfitness.domain.usecase.TemplateNameCheck

/**
 * A name being typed (the rename sheet or the new-comida name): the typed [name], its [nameCheck] and whether it is
 * [nameTaken] by another comida (reported by the server when saving).
 */
data class RenameDraft(val name: String, val nameTaken: Boolean, val nameCheck: TemplateNameCheck) {
    /** The inline error under the field, if any. */
    val nameError: TemplateNameError?
        get() = when {
            nameTaken -> TemplateNameError.Taken
            nameCheck == TemplateNameCheck.TooLong -> TemplateNameError.TooLong
            else -> null
        }

    /** The name is valid and not taken: the submit button is enabled. */
    val canSubmit: Boolean
        get() = nameCheck == TemplateNameCheck.Valid && !nameTaken
}

/**
 * One food of the comida, identified by its [index] in the saved comida (the same food may appear twice). [amountText]
 * is the typed amount in the base [unit]; [quantityError] is set when it is not valid. [kcal] and the macros are the
 * nutrition of that amount (0 while it is invalid). [portionLabel] is kept only while the amount is untouched.
 * [removable] is false for the last food left.
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
)

/**
 * The food sheet: the tapped [row] as previewed with the amount typed in the sheet (not yet applied to the comida),
 * its catalogue [food] and its [micronutrients] once known (null: none to show). [canSave] says the typed amount is
 * valid, so the sheet's "Guardar" is enabled.
 */
data class OpenFood(val row: DetailsRow, val food: Food, val micronutrients: Micronutrients?, val canSave: Boolean)

/** Which sheet over the details is open: none, the rename sheet, the "Guardar" choice, or the name of a new comida. */
enum class DetailsSheet { None, Rename, Choose, NewName }

/** What the details of a comida show. */
sealed interface TemplateDetailsUiState {
    data object Loading : TemplateDetailsUiState

    /** The comida could not be read (or no longer exists); the screen offers a retry. */
    data object Error : TemplateDetailsUiState

    /**
     * The comida as drafted: the draft [name], the totals of the foods left and one of [rows] per food. Nothing is
     * stored until "Guardar": [isDirty] says the draft differs from the saved comida and [canSave] that it can be
     * saved now (valid amounts, a food left, online, not busy). [openFood] is the food sheet, [sheet] the open sheet
     * with [rename] or [newName] as typed. Everything survives process death (ADR-40).
     */
    data class Content(
        val name: String,
        val totalKcal: Double,
        val totalProteinG: Double,
        val totalCarbsG: Double,
        val totalFatG: Double,
        val rows: List<DetailsRow>,
        val canSave: Boolean,
        val isDirty: Boolean,
        val isOffline: Boolean,
        val busy: Boolean,
        val sheet: DetailsSheet,
        val openFood: OpenFood?,
        val rename: RenameDraft?,
        val newName: RenameDraft?,
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

    /** The food sheet's "Guardar": applies the pending amount to the comida draft and closes the sheet. */
    data object SaveFood : TemplateDetailsEvent

    /** Sets the committed amount of a food in the draft. */
    data class AmountChanged(val index: Int, val text: String) : TemplateDetailsEvent

    /** Removes a food from the draft (swipe or food sheet); ignored for the last food. */
    data class RemoveFood(val index: Int) : TemplateDetailsEvent

    /** The pencil: opens the rename sheet prefilled with the draft name. */
    data object OpenRename : TemplateDetailsEvent

    data class RenameNameChanged(val text: String) : TemplateDetailsEvent

    /** Stores the typed name in the draft only; nothing is sent to the server. */
    data object SubmitRename : TemplateDetailsEvent

    data object DismissRename : TemplateDetailsEvent

    /** "Guardar": opens the choice between overwriting and saving as a new comida. */
    data object SaveClicked : TemplateDetailsEvent

    /** "Sobrescribir": replaces the name and items of this comida with the draft. */
    data object Overwrite : TemplateDetailsEvent

    /** "Guardar como nueva": asks for the name of the new comida. */
    data object SaveAsNew : TemplateDetailsEvent

    data class NewNameChanged(val text: String) : TemplateDetailsEvent

    data object SubmitNewName : TemplateDetailsEvent

    /** Closes the "Guardar" choice or the new-name sheet. */
    data object DismissSheet : TemplateDetailsEvent

    /** "Eliminar comida": deletes at once (online only). */
    data object Delete : TemplateDetailsEvent

    /** Reload after an error (shows the loading state); the draft is kept. */
    data object Retry : TemplateDetailsEvent

    /** Silent reload, e.g. on resume; the draft is kept. */
    data object Refresh : TemplateDetailsEvent
}

sealed interface TemplateDetailsEffect {
    /** The draft was saved (overwritten or as a new comida): give haptic feedback and leave the details. */
    data object Saved : TemplateDetailsEffect

    /** The comida was deleted: leave the details. */
    data object Deleted : TemplateDetailsEffect

    /** A save or delete was refused for another reason than a taken name; the draft is kept. */
    data class ActionFailed(val error: AppError?) : TemplateDetailsEffect
}
