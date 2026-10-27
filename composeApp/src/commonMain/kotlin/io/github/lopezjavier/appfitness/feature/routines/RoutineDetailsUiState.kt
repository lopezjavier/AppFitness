package io.github.lopezjavier.appfitness.feature.routines

import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import kotlin.time.Instant

/**
 * One exercise of the routine details. [name] and [imageKey] come from the exercise catalog (null when unknown or
 * when the exercise has no media); [plannedSets] are in set order, so the UI can show "3 x 8" and per-set weights.
 */
data class RoutineDetailsExerciseUi(
    val exerciseId: String,
    val name: String?,
    val imageKey: String?,
    val plannedSets: List<PlannedSet>,
)

/** State of the routine details screen (read-only view of one routine with start, edit and delete). */
sealed interface RoutineDetailsUiState {
    data object Loading : RoutineDetailsUiState

    /** Reading the routine failed (or it no longer exists); [RoutineDetailsEvent.Retry] tries again. */
    data object Error : RoutineDetailsUiState

    /**
     * The routine. [lastDone] is the start of its last completed session (null = never done). Starting and
     * deleting need [isOnline]; [busy] is true while either runs (a second action is ignored).
     */
    data class Content(
        val name: String,
        val exercises: List<RoutineDetailsExerciseUi>,
        val lastDone: Instant? = null,
        val isOnline: Boolean = true,
        val busy: Boolean = false,
    ) : RoutineDetailsUiState
}

sealed interface RoutineDetailsEvent {
    data object Retry : RoutineDetailsEvent

    /** Reloads without the skeleton (the screen came back, for example from the builder). */
    data object Refresh : RoutineDetailsEvent

    /** "Empezar". */
    data object Start : RoutineDetailsEvent

    /** The trash action: deletes the routine at once (no dialog). */
    data object Delete : RoutineDetailsEvent
}

/** One-off results of the routine details. */
sealed interface RoutineDetailsEffect {
    /** A session was started; open the live screen. */
    data object OpenLiveSession : RoutineDetailsEffect

    /** Another session is already in progress; nothing was started. */
    data object SessionInProgress : RoutineDetailsEffect

    /** Starting failed; nothing was started. */
    data object StartFailed : RoutineDetailsEffect

    /** The routine was deleted; leave the screen. */
    data object Deleted : RoutineDetailsEffect

    /** Deleting failed; the routine is still there. */
    data object ActionFailed : RoutineDetailsEffect

    /** Start or delete was refused because there is no connection (nothing changed). */
    data object OfflineRefused : RoutineDetailsEffect
}
