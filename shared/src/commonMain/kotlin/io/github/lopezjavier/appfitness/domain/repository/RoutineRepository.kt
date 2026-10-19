package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary

/** The signed-in user's routine templates. A failure carries an `AppException`. */
interface RoutineRepository {
    /** Non-archived routines, newest first. */
    suspend fun listActive(): Result<List<RoutineSummary>>

    /** The routine with its exercises in position order, or `NotFound`. */
    suspend fun get(id: String): Result<Routine>

    /** Creates the routine (null id) or replaces its name and exercises atomically; returns its id. */
    suspend fun save(routine: Routine): Result<String>

    /** Saves a copy named "<name> (copia)"; returns the new id. */
    suspend fun duplicate(id: String): Result<String>

    suspend fun archive(id: String): Result<Unit>
}
