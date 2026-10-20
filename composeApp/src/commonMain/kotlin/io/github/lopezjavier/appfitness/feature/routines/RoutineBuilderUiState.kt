package io.github.lopezjavier.appfitness.feature.routines

import io.github.lopezjavier.appfitness.domain.usecase.SupersetLabel

/**
 * One exercise row of the builder. [rowId] is stable while editing (the list key; positions are not), [name] and
 * [imageKey] come from the exercise catalog (null while it loads or when unknown). [supersetLabel] is non-null for
 * members of a superset ("Superserie A1").
 */
data class BuilderRowUi(
    val rowId: Long,
    val exerciseId: String,
    val name: String?,
    val imageKey: String?,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val supersetLabel: SupersetLabel?,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val canGroupWithNext: Boolean,
)

/** State of the routine builder. */
sealed interface RoutineBuilderUiState {
    /** Edit mode while the routine is being read. */
    data object Loading : RoutineBuilderUiState

    /** Reading the routine (or its exercise names) failed; [RoutineBuilderEvent.Retry] tries again. */
    data object LoadError : RoutineBuilderUiState

    /**
     * The form. [isDirty] means there are unsaved edits; [showDiscardConfirm] shows the "discard changes?" dialog.
     * Saving needs a name, at least one exercise, a connection and no save in flight.
     */
    data class Editing(
        val isEditMode: Boolean,
        val name: String,
        val rows: List<BuilderRowUi>,
        val isOnline: Boolean = true,
        val isSaving: Boolean = false,
        val isDirty: Boolean = false,
        val showDiscardConfirm: Boolean = false,
    ) : RoutineBuilderUiState {
        val canSave: Boolean get() = name.isNotBlank() && rows.isNotEmpty() && isOnline && !isSaving
    }
}

sealed interface RoutineBuilderEvent {
    data class SetName(val name: String) : RoutineBuilderEvent

    /** The picker returned this exercise: append it with the default 3 x 8, 120 s. */
    data class AddExercise(val exerciseId: String) : RoutineBuilderEvent

    /** A drag finished: the row at [from] now sits at [to]. */
    data class MoveTo(val from: Int, val to: Int) : RoutineBuilderEvent

    data class MoveUp(val index: Int) : RoutineBuilderEvent

    data class MoveDown(val index: Int) : RoutineBuilderEvent

    data class GroupWithNext(val index: Int) : RoutineBuilderEvent

    data class Ungroup(val index: Int) : RoutineBuilderEvent

    data class Remove(val index: Int) : RoutineBuilderEvent

    /** [delta] is -1 or +1 step of that field (rest steps are 10 s); the value stays within its bounds. */
    data class StepSets(val index: Int, val delta: Int) : RoutineBuilderEvent

    data class StepReps(val index: Int, val delta: Int) : RoutineBuilderEvent

    data class StepRest(val index: Int, val delta: Int) : RoutineBuilderEvent

    data object Save : RoutineBuilderEvent

    data object Retry : RoutineBuilderEvent

    /** Back was pressed: leaves at once, or asks to discard when there are unsaved edits. */
    data object Back : RoutineBuilderEvent

    data object ConfirmDiscard : RoutineBuilderEvent

    data object DismissDiscard : RoutineBuilderEvent
}

/** One-off results of the builder. */
sealed interface RoutineBuilderEffect {
    /** The routine was saved, or the user chose to leave: close the screen. */
    data object Close : RoutineBuilderEffect

    /** Saving failed (the edits are kept); [offline] distinguishes a lost connection from any other failure. */
    data class SaveFailed(val offline: Boolean) : RoutineBuilderEffect
}
