package io.github.lopezjavier.appfitness.feature.meals

import io.github.lopezjavier.appfitness.domain.usecase.MealSlotError

/**
 * One row of the meals editor. [key] is stable while editing (the slot id, or `new_<n>` for an unsaved meal).
 * [isEditing] shows the inline name field; [canDelete] is false while busy, offline or when it is the only meal;
 * [deleteBlocked] is set when the server refused the delete because the meal has logged entries.
 */
data class MealSlotRowUi(
    val key: String,
    val label: String,
    val error: MealSlotError?,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val isNew: Boolean,
    val isEditing: Boolean,
    val deleteBlocked: Boolean,
    val canDelete: Boolean,
)

/** Why the last save failed. The draft is always kept. */
enum class MealSlotsSaveError {
    /** No connection. */
    Offline,

    /** The server refused the structure (the draft is invalid). */
    Rejected,

    /** Any other failure. */
    Failed,
}

/** State of the meals editor. */
sealed interface MealSlotsUiState {
    data object Loading : MealSlotsUiState

    /** Reading the meals failed; [MealSlotsEvent.Retry] tries again. */
    data object LoadError : MealSlotsUiState

    /**
     * The editor. [canSave] needs valid labels, unsaved changes, a connection and no save or delete in flight.
     * [onlyOneMeal] tells the UI to show the "at least one meal must remain" hint. [showDiscardConfirm] shows the
     * "discard changes?" dialog.
     */
    data class Editing(
        val rows: List<MealSlotRowUi>,
        val onlyOneMeal: Boolean = false,
        val isOnline: Boolean = true,
        val isSaving: Boolean = false,
        val isDirty: Boolean = false,
        val showDiscardConfirm: Boolean = false,
        val saveError: MealSlotsSaveError? = null,
        val canSave: Boolean = false,
    ) : MealSlotsUiState
}

sealed interface MealSlotsEvent {
    data class SetLabel(val key: String, val label: String) : MealSlotsEvent

    /** Opens the inline name field of the row [key]. */
    data class Edit(val key: String) : MealSlotsEvent

    data object StopEdit : MealSlotsEvent

    /** Deletes the row: an unsaved one locally, a saved one through the server (which refuses used or last meals). */
    data class Delete(val key: String) : MealSlotsEvent

    /** Appends a blank custom meal and starts editing its name. */
    data object Add : MealSlotsEvent

    /** A drag finished: the row at [from] now sits at [to]. */
    data class MoveTo(val from: Int, val to: Int) : MealSlotsEvent

    data class MoveUp(val index: Int) : MealSlotsEvent

    data class MoveDown(val index: Int) : MealSlotsEvent

    data object Save : MealSlotsEvent

    data object Retry : MealSlotsEvent

    /** Back was pressed: leaves at once, or asks to discard when there are unsaved edits. */
    data object Back : MealSlotsEvent

    data object ConfirmDiscard : MealSlotsEvent

    data object DismissDiscard : MealSlotsEvent
}

/** One-off results of the meals editor. */
sealed interface MealSlotsEffect {
    /** The structure was saved, or the user chose to leave: close the screen. */
    data object Close : MealSlotsEffect
}
