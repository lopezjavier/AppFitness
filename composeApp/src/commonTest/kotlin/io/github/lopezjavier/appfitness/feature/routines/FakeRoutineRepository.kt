package io.github.lopezjavier.appfitness.feature.routines

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository

/**
 * In-memory routines: [routines] is kept in position order (new ones at the end), archived ones hidden, ids
 * assigned on save. [folderOf] and [nextId] mirror `routines.folder_id` / `is_next`.
 */
class FakeRoutineRepository(initial: List<Routine> = emptyList()) : RoutineRepository {
    val routines = initial.toMutableList()
    val saved = mutableListOf<Routine>()
    val duplicated = mutableListOf<String>()
    val archivedIds = mutableListOf<String>()
    val folders = mutableListOf<RoutineFolder>()
    val folderOf = mutableMapOf<String, String?>()
    var nextId: String? = null
    val createdFolders = mutableListOf<String>()
    val renamedFolders = mutableListOf<Pair<String, String>>()
    val deletedFolders = mutableListOf<String>()
    val movedToFolder = mutableListOf<Pair<String, String?>>()
    val savedOrders = mutableListOf<Pair<String?, List<String>>>()
    val nextCalls = mutableListOf<String?>()
    var listCalls = 0
    var failure: AppError? = null
    var saveFailure: AppError? = null
    var updateFailure: AppError? = null
    val updatedFromSession = mutableListOf<String>()

    /** (sessionId, name) of every [createFromSession] call. */
    val createdFromSession = mutableListOf<Pair<String, String>>()
    var createFromSessionFailure: AppError? = null
    private var counter = 0

    private fun failing(error: AppError?) = error?.let { Result.failure<Nothing>(AppException(it)) }

    override suspend fun listActive(): Result<List<RoutineSummary>> {
        listCalls++
        failing(failure)?.let { return it }
        return Result.success(
            routines.filter { !it.archived }.map {
                RoutineSummary(it.id.orEmpty(), it.name, it.exercises.size, folderOf[it.id], it.id == nextId)
            },
        )
    }

    override suspend fun listFolders(): Result<List<RoutineFolder>> {
        failing(failure)?.let { return it }
        return Result.success(folders.toList())
    }

    override suspend fun createFolder(name: String): Result<String> {
        failing(saveFailure)?.let { return it }
        createdFolders += name
        val id = "folder-${++counter}"
        folders += RoutineFolder(id, name.trim())
        return Result.success(id)
    }

    override suspend fun renameFolder(id: String, name: String): Result<Unit> {
        failing(saveFailure)?.let { return it }
        renamedFolders += id to name
        val index = folders.indexOfFirst { it.id == id }
        folders[index] = folders[index].copy(name = name.trim())
        return Result.success(Unit)
    }

    override suspend fun deleteFolder(id: String): Result<Int> {
        failing(saveFailure)?.let { return it }
        deletedFolders += id
        val doomed = routines.filter { folderOf[it.id] == id }
        routines.removeAll(doomed)
        doomed.forEach { folderOf.remove(it.id) }
        if (nextId in doomed.map { it.id }) nextId = null
        folders.removeAll { it.id == id }
        return Result.success(doomed.size)
    }

    override suspend fun moveToFolder(routineId: String, folderId: String?): Result<Unit> {
        failing(saveFailure)?.let { return it }
        movedToFolder += routineId to folderId
        folderOf[routineId] = folderId
        val index = routines.indexOfFirst { it.id == routineId }
        routines += routines.removeAt(index)
        return Result.success(Unit)
    }

    override suspend fun saveOrder(folderId: String?, orderedIds: List<String>): Result<Unit> {
        failing(saveFailure)?.let { return it }
        savedOrders += folderId to orderedIds
        val slots = routines.indices.filter { folderOf[routines[it].id] == folderId && routines[it].id in orderedIds }
        val reordered = orderedIds.map { id -> routines.first { it.id == id } }
        slots.zip(reordered).forEach { (slot, routine) -> routines[slot] = routine }
        return Result.success(Unit)
    }

    override suspend fun setNext(routineId: String?): Result<Unit> {
        failing(saveFailure)?.let { return it }
        nextCalls += routineId
        nextId = routineId
        return Result.success(Unit)
    }

    override suspend fun get(id: String): Result<Routine> {
        failing(failure)?.let { return it }
        return routines.firstOrNull { it.id == id }?.let { Result.success(it) }
            ?: Result.failure(AppException(AppError.NotFound))
    }

    override suspend fun save(routine: Routine): Result<String> {
        failing(saveFailure)?.let { return it }
        saved += routine
        val id = routine.id ?: "new-${++counter}"
        val index = routines.indexOfFirst { it.id == id }
        if (index >= 0) routines[index] = routine.copy(id = id) else routines += routine.copy(id = id)
        return Result.success(id)
    }

    override suspend fun duplicate(id: String): Result<String> {
        failing(saveFailure)?.let { return it }
        duplicated += id
        val original = routines.first { it.id == id }
        return save(original.copy(id = null, name = original.name + " (copia)"))
    }

    override suspend fun updateFromSession(sessionId: String): Result<String> {
        failing(updateFailure)?.let { return it }
        updatedFromSession += sessionId
        return Result.success("r1")
    }

    override suspend fun createFromSession(sessionId: String, name: String): Result<String> {
        failing(createFromSessionFailure)?.let { return it }
        createdFromSession += sessionId to name
        return Result.success("r-from-session")
    }

    override suspend fun archive(id: String): Result<Unit> {
        failing(saveFailure)?.let { return it }
        archivedIds += id
        val index = routines.indexOfFirst { it.id == id }
        routines[index] = routines[index].copy(archived = true)
        return Result.success(Unit)
    }
}

/** A stored routine with [count] exercises (sets 3, reps 8, rest 120). */
fun routineOf(id: String, name: String, vararg exerciseIds: String) = Routine(
    id = id,
    name = name,
    notes = null,
    archived = false,
    exercises =
    exerciseIds.mapIndexed { index, exerciseId ->
        RoutineExercise(
            exerciseId,
            position = index,
            supersetGroup = null,
            plannedSets = List(3) { PlannedSet(null, 8, SetType.Working) },
            restSeconds = 120,
        )
    },
)
