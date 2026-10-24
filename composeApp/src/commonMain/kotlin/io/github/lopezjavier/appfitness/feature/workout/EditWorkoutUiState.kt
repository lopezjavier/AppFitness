package io.github.lopezjavier.appfitness.feature.workout

sealed interface EditWorkoutUiState {
    data object Loading : EditWorkoutUiState

    /** The workout could not be loaded (or no longer exists); [EditWorkoutEvent.Retry] tries again. */
    data object Error : EditWorkoutUiState

    /**
     * The form of a finished workout. Saving and deleting need the server, so they are refused while [isOnline] is
     * false; [canSave] is false while the form has issues, the device is offline or a call runs ([busy]).
     * [WorkoutFormView.form] holds the date, read-only here (an edit never moves the workout).
     */
    data class Content(val view: WorkoutFormView, val isOnline: Boolean = true, val busy: Boolean = false) :
        EditWorkoutUiState {
        val canSave: Boolean get() = isOnline && !busy && view.issues.isEmpty()
    }
}

/** Screen-level events; the field edits are [WorkoutFormEvent]s. */
sealed interface EditWorkoutEvent {
    data object Retry : EditWorkoutEvent

    /** The only write of the screen (ADR-45): replaces the workout's content in one atomic call. */
    data object Save : EditWorkoutEvent

    /** Deletes the workout at once (no confirmation, ADR-45) and closes the screen. */
    data object Delete : EditWorkoutEvent
}

sealed interface EditWorkoutEffect {
    /** Saved: close the screen. */
    data object Saved : EditWorkoutEffect

    /** Deleted: close the screen and the details below it. */
    data object Deleted : EditWorkoutEffect

    /** A save or a deletion failed (or was attempted offline): show a message; the edits stay. */
    data object ActionFailed : EditWorkoutEffect
}
