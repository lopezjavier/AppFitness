package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.MuscleShare
import io.github.lopezjavier.appfitness.domain.usecase.SaveWorkoutAsRoutine
import io.github.lopezjavier.appfitness.domain.usecase.SessionStats

/** One completed set of a past session; [text] is the shared set text ("85 kg × 8", "8 reps", "01:30"). */
data class DetailSet(val setIndex: Int, val setType: SetType, val text: String, val rpe: Double? = null)

/** An exercise of the session; [name] is null when the library has none (the UI shows a generic label). */
data class DetailExercise(
    val position: Int,
    val exerciseId: String,
    val name: String?,
    val sets: List<DetailSet>,
    val isPersonalRecord: Boolean,
    /** Record families the session beat for this exercise, in enum order; empty while history is unavailable. */
    val records: Set<PersonalRecordType> = emptySet(),
)

/**
 * The "Guardar como rutina" sheet: [name] is the typed routine name (prefilled with the workout's). [tooManySets] means
 * an exercise has more sets than a routine can hold, so saving is refused up front. [failed] shows the inline
 * failure (offline or server error); [busy] is true while the call runs.
 */
data class SaveAsRoutineState(
    val name: String,
    val tooManySets: Boolean,
    val busy: Boolean = false,
    val failed: Boolean = false,
) {
    val canConfirm: Boolean get() = !busy && !tooManySets && SaveWorkoutAsRoutine.isValidName(name)
}

sealed interface SessionDetailUiState {
    data object Loading : SessionDetailUiState

    /** The session could not be loaded (or no longer exists); [SessionDetailEvent.Retry] tries again. */
    data object Error : SessionDetailUiState

    /**
     * A completed session, read-only. Saving as a routine and deleting need the server, so both are refused while
     * [isOnline] is false (the UI shows the reason). [busy] is true while a deletion runs; [saveAsRoutine] is
     * non-null while its sheet is open.
     */
    data class Content(
        val name: String,
        val label: DateLabel,
        val stats: SessionStats,
        val exercises: List<DetailExercise>,
        val isOnline: Boolean = true,
        val saveAsRoutine: SaveAsRoutineState? = null,
        val busy: Boolean = false,
        /** Completed sets per primary muscle group, most first; empty (section hidden) when groups are unknown. */
        val split: List<MuscleShare> = emptyList(),
    ) : SessionDetailUiState
}

sealed interface SessionDetailEvent {
    data object Retry : SessionDetailEvent

    /** The screen came back (after an edit): reloads the workout without showing the loading state. */
    data object Refresh : SessionDetailEvent

    /** "Guardar como rutina": opens the sheet with the workout's name prefilled. */
    data object OpenSaveAsRoutine : SessionDetailEvent

    data class SaveAsRoutineNameChanged(val text: String) : SessionDetailEvent

    /** Creates the routine; refused while the name is blank or an exercise has more than 10 sets. */
    data object ConfirmSaveAsRoutine : SessionDetailEvent

    /** Closes the sheet (X, scrim, swipe, back) and discards the name. */
    data object DismissSaveAsRoutine : SessionDetailEvent

    /** "Eliminar sesión": deletes the session at once (no confirmation, ADR-45) and closes the details. */
    data object Delete : SessionDetailEvent
}

sealed interface SessionDetailEffect {
    /** The session was deleted: leave the detail. */
    data object Deleted : SessionDetailEffect

    /** The routine was created: the sheet is closed, show "Rutina guardada" and stay on the detail. */
    data class SavedAsRoutine(val routineId: String) : SessionDetailEffect

    /** A deletion failed (or was attempted offline): show a message. */
    data object ActionFailed : SessionDetailEffect
}
