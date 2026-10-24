package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup

/** The exercise catalog plus the signed-in user's custom exercises. A failure carries an `AppException`. */
interface ExerciseRepository {
    /** Catalog rows and own custom rows. Cached for the session; creating an exercise invalidates the cache. */
    suspend fun getAll(): Result<List<Exercise>>

    /** The exercise with this id, or `NotFound` when it is not visible to the user. */
    suspend fun getById(id: String): Result<Exercise>

    /** Creates a private custom exercise measured by [metricType] (the sets it logs follow it). */
    suspend fun createCustom(
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
        metricType: ExerciseMetricType = ExerciseMetricType.WeightReps,
        perHand: Boolean = false,
    ): Result<Exercise>

    /** Edits an own custom exercise (the metric type is fixed); `NotFound` when it is not an own custom one. */
    suspend fun updateCustom(
        id: String,
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
        perHand: Boolean,
    ): Result<Unit>

    /** Deletes an own custom exercise; `InUse(sets, routines)` (nothing deleted) while something references it. */
    suspend fun deleteCustom(id: String): Result<Unit>
}
