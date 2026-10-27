package io.github.lopezjavier.appfitness.feature.meals

/**
 * One row of the meals editor. [key] is stable while editing (the slot id, or `new_<n>` for an unsaved meal).
 * [inUse] (the meal has logged entries) and [isLastMeal] explain a disabled removal; [canDelete] is false for either of
 * them and while saving.
 */
data class MealSlotRowUi(
    val key: String,
    val label: String,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val isNew: Boolean,
    val inUse: Boolean,
    val isLastMeal: Boolean,
    val canDelete: Boolean,
)

/** Why the last save failed. The draft is always kept. */
enum class MealSlotsSaveError {
    /** No connection. */
    Offline,

    /** The server refused the structure (duplicate name, meal in use, last meal...). */
    Rejected,

    /** Any other failure. */
    Failed,
}

/** The Name sheet of the meals editor. It only stages a change in the draft. */
sealed interface MealNameSheet {
    /** Adds a new meal. */
    data object Create : MealNameSheet

    /** Renames the row [key], whose current name is [currentName]. */
    data class Rename(val key: String, val currentName: String) : MealNameSheet
}

/** State of the meals editor. */
sealed interface MealSlotsUiState {
    data object Loading : MealSlotsUiState

    /** Reading the meals failed; [MealSlotsEvent.Retry] tries again. */
    data object LoadError : MealSlotsUiState

    /**
     * The editor. Nothing is applied until [MealSlotsEvent.Save] (removals included). [canSave] needs valid labels,
     * unsaved changes, a connection and no save in flight; [saved] turns true once the single save succeeded (the
     * template closes the screen). [sheet] is the open Name sheet. [canNavigateToDefaults] is false while [isDirty].
     */
    data class Editing(
        val rows: List<MealSlotRowUi>,
        val sheet: MealNameSheet? = null,
        val isOnline: Boolean = true,
        val isSaving: Boolean = false,
        val isDirty: Boolean = false,
        val saveError: MealSlotsSaveError? = null,
        val canSave: Boolean = false,
        val saved: Boolean = false,
        val canNavigateToDefaults: Boolean = true,
    ) : MealSlotsUiState
}

sealed interface MealSlotsEvent {
    /** Opens the Name sheet to create a meal. */
    data object Add : MealSlotsEvent

    /** Opens the Name sheet to rename the row [key]. */
    data class Rename(val key: String) : MealSlotsEvent

    /** Commits the Name sheet: adds the meal or renames the row. Blank, too long or duplicate names are ignored. */
    data class SubmitName(val name: String) : MealSlotsEvent

    data object DismissSheet : MealSlotsEvent

    /** Stages the removal of the row (nothing reaches the server until save); in-use and last meals are refused. */
    data class Delete(val key: String) : MealSlotsEvent

    /** A drag finished: the row at [from] now sits at [to]. */
    data class MoveTo(val from: Int, val to: Int) : MealSlotsEvent

    /** Accessibility action "Subir". */
    data class MoveUp(val index: Int) : MealSlotsEvent

    /** Accessibility action "Bajar". */
    data class MoveDown(val index: Int) : MealSlotsEvent

    data object Save : MealSlotsEvent

    data object Retry : MealSlotsEvent
}
