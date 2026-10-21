package io.github.lopezjavier.appfitness.feature.diary

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import io.github.lopezjavier.appfitness.domain.usecase.QuantityCheck

/** What the edit-entry sheet shows. */
sealed interface EditEntryUiState {
    /** Loading the entry, its food and the meals. */
    data object Loading : EditEntryUiState

    /** The entry, its food or the meals could not be loaded (or the entry no longer exists); Retry tries again. */
    data object Error : EditEntryUiState

    data class Content(val content: EditEntryContent) : EditEntryUiState
}

/**
 * The sheet for [entry] of [food]. [amountText] is what the user typed: an amount in the food's base unit, or a count
 * of the portion [selectedPortionId] (null = raw base unit). [resolvedAmount] is the amount in base units and
 * [preview] its nutrition (0.1 precision, the UI rounds kcal), both null while [quantityError] is set.
 */
data class EditEntryContent(
    val entry: FoodLogEntry,
    val food: Food,
    val amountText: String,
    val selectedPortionId: String?,
    val slots: List<MealSlot>,
    val selectedSlotId: String,
    val resolvedAmount: Double?,
    val preview: NutritionSnapshot?,
    val quantityError: QuantityCheck?,
    val isOffline: Boolean,
    val saving: Boolean,
) {
    /** The save button is enabled. */
    val canSave: Boolean
        get() = quantityError == null && resolvedAmount != null && !isOffline && !saving
}

sealed interface EditEntryEvent {
    data class AmountChanged(val text: String) : EditEntryEvent

    /** Pick a named portion, or null to type an amount in the base unit again. */
    data class PortionSelected(val portionId: String?) : EditEntryEvent

    data class SlotSelected(val slotId: String) : EditEntryEvent

    data object Save : EditEntryEvent

    /** Ask the Diary to delete this entry; only while online. */
    data object Delete : EditEntryEvent

    data object Retry : EditEntryEvent
}

sealed interface EditEntryEffect {
    /** The entry was updated: give haptic feedback and close back to the Diary. */
    data object Saved : EditEntryEffect

    /** Saving failed; the sheet stays open with the user's input. */
    data class SaveFailed(val error: AppError?) : EditEntryEffect

    /** The user chose to delete: close the sheet and let the Diary delete [entryId]. */
    data class DeleteRequested(val entryId: String) : EditEntryEffect
}
