package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary

/** The signed-in user's routine templates. A failure carries an `AppException`. */
@Suppress("TooManyFunctions") // One function per repository operation.
interface RoutineRepository {
    /** Non-archived routines in the manual order of the user (position, then creation). */
    suspend fun listActive(): Result<List<RoutineSummary>>

    /** The routine with its exercises in position order, or `NotFound`. */
    suspend fun get(id: String): Result<Routine>

    /** Creates the routine (null id) or replaces its name and exercises atomically; returns its id. */
    suspend fun save(routine: Routine): Result<String>

    /** Saves a copy named "<name> (copia)"; returns the new id. */
    suspend fun duplicate(id: String): Result<String>

    /**
     * Replaces the routine's exercises and sets with the completed sets of the finished session; returns the routine
     * id. Call after the session is finished.
     */
    suspend fun updateFromSession(sessionId: String): Result<String>

    suspend fun archive(id: String): Result<Unit>

    /** The folders of the user in their order. */
    suspend fun listFolders(): Result<List<RoutineFolder>>

    /** Creates a folder at the end; returns its id. */
    suspend fun createFolder(name: String): Result<String>

    suspend fun renameFolder(id: String, name: String): Result<Unit>

    /** Deletes the folder and all its routines (archived included); returns how many routines were deleted. */
    suspend fun deleteFolder(id: String): Result<Int>

    /** Moves the routine to the end of the folder's group (null = "Mis rutinas", no folder). */
    suspend fun moveToFolder(routineId: String, folderId: String?): Result<Unit>

    /** Persists the manual order of the routines of one group (null folder = the no-folder group). */
    suspend fun saveOrder(folderId: String?, orderedIds: List<String>): Result<Unit>

    /** Marks the routine as the next workout (clearing any other); null only clears. */
    suspend fun setNext(routineId: String?): Result<Unit>
}
