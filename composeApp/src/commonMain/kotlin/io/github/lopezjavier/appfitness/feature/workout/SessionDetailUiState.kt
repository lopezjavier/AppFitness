package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.MuscleShare
import io.github.lopezjavier.appfitness.domain.usecase.SessionStats
import kotlinx.datetime.LocalDate

/** One completed set of a past session; [text] is the shared set text ("85 kg × 8", "8 reps", "01:30"). */
data class DetailSet(
    val setIndex: Int,
    val setType: SetType,
    val text: String,
    val rpe: Double? = null,
    /** The set note (US-66), read-only in the detail; null when none. */
    val notes: String? = null,
)

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
 * The "Guardar como rutina" sheet. The typed name lives in the sheet itself (it is prefilled with
 * `SaveWorkoutAsRoutine.defaultName`); [existingNames] are the names of the active routines, for the unique-name check
 * (empty when they could not be read: the server stays the authority). [tooManySets] means an exercise has more sets
 * than a routine can hold, so saving is refused up front. [failed] means the last save failed (offline or server
 * error); [busy] is true while the call runs.
 */
data class SaveAsRoutineState(
    val existingNames: List<String> = emptyList(),
    val tooManySets: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
)

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
        /** Day the workout was done; with [today] it gives the title date ("Hoy", "Ayer", "lun, 24 oct"). */
        val date: LocalDate,
        val today: LocalDate,
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

    /** Creates the routine [name]; refused while it is blank or an exercise has more than 10 sets. */
    data class ConfirmSaveAsRoutine(val name: String) : SessionDetailEvent

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
