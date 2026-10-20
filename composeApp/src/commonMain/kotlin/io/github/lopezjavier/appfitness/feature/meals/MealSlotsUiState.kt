package io.github.lopezjavier.appfitness.feature.meals

import io.github.lopezjavier.appfitness.domain.usecase.MealSlotError

/**
 * One row of the meals editor. [key] is stable while editing (the slot id, or `new_<n>` for an unsaved meal).
 * [canToggle] is false when the meal cannot be disabled now; [hasEntriesInViewedDay] tells the UI to show the hint
 * (it is false when the entries could not be read, in which case the switch is still locked).
 */
data class MealSlotRowUi(
    val key: String,
    val label: String,
    val enabled: Boolean,
    val error: MealSlotError?,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val canToggle: Boolean,
    val hasEntriesInViewedDay: Boolean,
    val isNew: Boolean,
)

/** Why the last save failed. The draft is always kept. */
enum class MealSlotsSaveError {
    /** No connection. */
    Offline,

    /** The server refused the structure (a meal gained entries on the viewed day, or the draft is invalid). */
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
     * The editor. [canSave] needs valid labels, at least one enabled meal ([noneEnabled] explains that case), unsaved
     * changes, a connection and no save in flight. [showDiscardConfirm] shows the "discard changes?" dialog.
     */
    data class Editing(
        val rows: List<MealSlotRowUi>,
        val noneEnabled: Boolean = false,
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

    data class SetEnabled(val key: String, val enabled: Boolean) : MealSlotsEvent

    /** Adds a blank custom meal right after the row at [afterIndex], or at the end when null. */
    data class Add(val afterIndex: Int? = null) : MealSlotsEvent

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
