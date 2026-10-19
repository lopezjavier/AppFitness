package io.github.lopezjavier.appfitness.feature.routines

import io.github.lopezjavier.appfitness.domain.model.RoutineSummary

/** State of "Mis rutinas". */
sealed interface RoutinesUiState {
    data object Loading : RoutinesUiState

    /** Loading the list failed; [RoutinesEvent.Retry] tries again. */
    data object Error : RoutinesUiState

    /**
     * The user's routines (an empty list is the empty state). [menuFor] is the routine whose actions sheet is open and
     * [archiveFor] the one waiting for the archive confirmation. Duplicate and archive need [isOnline].
     */
    data class Content(
        val routines: List<RoutineSummary>,
        val isOnline: Boolean = true,
        val menuFor: RoutineSummary? = null,
        val archiveFor: RoutineSummary? = null,
    ) : RoutinesUiState {
        val isEmpty: Boolean get() = routines.isEmpty()
    }
}

sealed interface RoutinesEvent {
    data object Retry : RoutinesEvent

    /** Reloads without the loading skeleton (the screen came back, for example from the builder). */
    data object Refresh : RoutinesEvent

    data object NewRoutine : RoutinesEvent

    data class Edit(val id: String) : RoutinesEvent

    data class OpenMenu(val id: String) : RoutinesEvent

    data object DismissMenu : RoutinesEvent

    data class Duplicate(val id: String) : RoutinesEvent

    data class RequestArchive(val id: String) : RoutinesEvent

    data object ConfirmArchive : RoutinesEvent

    data object DismissArchive : RoutinesEvent
}

/** One-off results of "Mis rutinas". */
sealed interface RoutinesEffect {
    /** Open the builder: a new routine when [routineId] is null, otherwise edit that one. */
    data class OpenBuilder(val routineId: String?) : RoutinesEffect

    /** Duplicating or archiving failed; the list is unchanged. */
    data object ActionFailed : RoutinesEffect
}
