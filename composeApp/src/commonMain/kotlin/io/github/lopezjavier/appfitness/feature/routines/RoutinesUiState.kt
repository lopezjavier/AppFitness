package io.github.lopezjavier.appfitness.feature.routines

import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.RoutineGroup
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.domain.usecase.groupRoutines

/** Whether the folder sheet creates a folder or renames [FolderSheetState.folderId]. */
enum class FolderSheetMode { Create, Rename }

/**
 * The open create/rename folder sheet (restored after process death, ADR-40). The typed name lives in the
 * `NameSheet` itself; a rename takes its initial name from the folder with [folderId].
 */
data class FolderSheetState(val mode: FolderSheetMode, val folderId: String? = null)

/** State of "Mis rutinas". */
sealed interface RoutinesUiState {
    data object Loading : RoutinesUiState

    /** Loading the list failed; [RoutinesEvent.Retry] tries again. */
    data object Error : RoutinesUiState

    /**
     * The user's routines (an empty list is the empty state). [menuFor] is the routine whose actions sheet is open.
     * Duplicate, delete and starting a session need
     * [isOnline]; [starting] is true while a session is being created (a second start is ignored).
     *
     * [routines] is the FULL list in manual order (position) and [folders] the folders in order. [groups] is the
     * grouped view (folders first, the no-folder group last). [collapsedFolderIds] survives leaving the app,
     * [folderSheet] is the create/rename sheet (null = closed), [deleteFolderFor] the folder waiting for the delete
     * confirmation (its routine count is [deleteFolderRoutineCount], taken from the full list) and [moveFor] the
     * routine whose folder picker is open.
     */
    data class Content(
        val routines: List<RoutineSummary>,
        val isOnline: Boolean = true,
        val menuFor: RoutineSummary? = null,
        val starting: Boolean = false,
        val folders: List<RoutineFolder> = emptyList(),
        val collapsedFolderIds: Set<String> = emptySet(),
        val folderSheet: FolderSheetState? = null,
        val deleteFolderFor: RoutineFolder? = null,
        val moveFor: RoutineSummary? = null,
        /** True while the folder sheet's create/rename call runs (the sheet shows its saving state). */
        val folderSaving: Boolean = false,
    ) : RoutinesUiState {
        /** No routines and no folders: the empty state. */
        val isEmpty: Boolean get() = routines.isEmpty() && folders.isEmpty()

        val groups: List<RoutineGroup> get() = groupRoutines(folders, routines)

        val deleteFolderRoutineCount: Int
            get() = deleteFolderFor?.let { folder -> routines.count { it.folderId == folder.id } } ?: 0
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

    /** Deletes the routine at once (no dialog): it leaves the list immediately and comes back if the server fails. */
    data class Delete(val id: String) : RoutinesEvent

    /** Tap on a routine card: opens its details. */
    data class OpenDetails(val id: String) : RoutinesEvent

    /** "Empezar" on a routine card. */
    data class StartRoutine(val id: String) : RoutinesEvent

    data object NewFolder : RoutinesEvent

    data class RenameFolder(val id: String) : RoutinesEvent

    /** "Guardar" in the folder sheet with the trimmed [name] the Name sheet produced. */
    data class SaveFolder(val name: String) : RoutinesEvent

    data object DismissFolderSheet : RoutinesEvent

    /** Delete icon of a folder: an empty folder is deleted at once, otherwise the confirmation opens. */
    data class RequestDeleteFolder(val id: String) : RoutinesEvent

    data object ConfirmDeleteFolder : RoutinesEvent

    data object DismissDeleteFolder : RoutinesEvent

    /** "Mover a carpeta" row of the routine sheet: opens the folder picker. */
    data class OpenMove(val routineId: String) : RoutinesEvent

    data object DismissMove : RoutinesEvent

    /** A target of the folder picker (null = "Mis rutinas"): moves the routine to the end of that group. */
    data class MoveToFolder(val routineId: String, val folderId: String?) : RoutinesEvent

    data class SetNext(val id: String) : RoutinesEvent

    data object ClearNext : RoutinesEvent

    /** The drop result of a drag: the new order of the routines of one group (null = no-folder group). */
    data class Reorder(val folderId: String?, val orderedIds: List<String>) : RoutinesEvent

    /** "Subir" / "Bajar" of the routine sheet. */
    data class MoveUp(val id: String) : RoutinesEvent

    data class MoveDown(val id: String) : RoutinesEvent

    data class ToggleFolder(val id: String) : RoutinesEvent
}

/** One-off results of "Mis rutinas". */
sealed interface RoutinesEffect {
    /** Open the builder: a new routine when [routineId] is null, otherwise edit that one. */
    data class OpenBuilder(val routineId: String?) : RoutinesEffect

    /** Open the details of this routine. */
    data class OpenDetails(val routineId: String) : RoutinesEffect

    /** A write (duplicate, delete, folders, order, next) failed; the list shows the server state again. */
    data object ActionFailed : RoutinesEffect

    /** A delete was refused because there is no connection (nothing changed). */
    data object OfflineRefused : RoutinesEffect

    /** A session was started; open the live screen. */
    data object OpenLiveSession : RoutinesEffect

    /** Starting a session failed; nothing was started. */
    data object StartFailed : RoutinesEffect

    /** Another session is already in progress; nothing was started. */
    data object SessionInProgress : RoutinesEffect
}
