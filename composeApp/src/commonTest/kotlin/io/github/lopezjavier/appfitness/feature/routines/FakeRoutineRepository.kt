package io.github.lopezjavier.appfitness.feature.routines

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository

/** In-memory routines: newest first, archived ones hidden, ids assigned on save. */
class FakeRoutineRepository(initial: List<Routine> = emptyList()) : RoutineRepository {
    val routines = initial.toMutableList()
    val saved = mutableListOf<Routine>()
    val duplicated = mutableListOf<String>()
    val archivedIds = mutableListOf<String>()
    var listCalls = 0
    var failure: AppError? = null
    var saveFailure: AppError? = null
    private var counter = 0

    private fun failing(error: AppError?) = error?.let { Result.failure<Nothing>(AppException(it)) }

    override suspend fun listActive(): Result<List<RoutineSummary>> {
        listCalls++
        failing(failure)?.let { return it }
        return Result.success(
            routines.filter {
                !it.archived
            }.reversed().map { RoutineSummary(it.id.orEmpty(), it.name, it.exercises.size) },
        )
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
        routines.removeAll { it.id == id }
        routines += routine.copy(id = id)
        return Result.success(id)
    }

    override suspend fun duplicate(id: String): Result<String> {
        failing(saveFailure)?.let { return it }
        duplicated += id
        val original = routines.first { it.id == id }
        return save(original.copy(id = null, name = original.name + " (copia)"))
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
            targetSets = 3,
            targetReps = 8,
            restSeconds = 120,
        )
    },
)
