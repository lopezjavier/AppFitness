package io.github.lopezjavier.appfitness.feature.workout

import kotlinx.datetime.LocalDate

/** Where a past workout starts from (US-37): nothing, a routine's plan or a finished workout used as a template. */
sealed interface PastWorkoutSource {
    data object Empty : PastWorkoutSource

    data class FromRoutine(val routineId: String) : PastWorkoutSource

    data class FromSession(val sessionId: String) : PastWorkoutSource
}

sealed interface PastWorkoutUiState {
    data object Loading : PastWorkoutUiState

    /** The routine or workout to start from could not be loaded; [PastWorkoutEvent.Retry] tries again. */
    data object Error : PastWorkoutUiState

    /**
     * The form of the workout to log. [today] is the latest date that can be picked (the date picker's `maxDate`).
     * [canSave] is false while the form has issues, the device is offline or a call runs ([busy]).
     */
    data class Content(
        val view: WorkoutFormView,
        val today: LocalDate,
        val isOnline: Boolean = true,
        val busy: Boolean = false,
    ) : PastWorkoutUiState {
        val canSave: Boolean get() = isOnline && !busy && view.issues.isEmpty()
    }
}

/** Screen-level events; the field edits are [WorkoutFormEvent]s. */
sealed interface PastWorkoutEvent {
    data object Retry : PastWorkoutEvent

    /** The date picked in the date dialog (its "Aceptar" only fills the form). */
    data class DateChanged(val date: LocalDate) : PastWorkoutEvent

    /** The only write of the screen: creates the completed workout (never a live session). */
    data object Save : PastWorkoutEvent
}

sealed interface PastWorkoutEffect {
    /** Saved: close the form; [sessionId] is the new workout. */
    data class Saved(val sessionId: String) : PastWorkoutEffect

    /** The save failed (or was attempted offline): show a message; the edits stay. */
    data object ActionFailed : PastWorkoutEffect
}
