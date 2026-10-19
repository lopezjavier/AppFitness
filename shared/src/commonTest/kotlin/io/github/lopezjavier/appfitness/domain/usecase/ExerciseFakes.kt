package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseInfo
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository

/** Synthetic catalog row (never RepDB content). */
fun catalogRow(
    id: String,
    key: String?,
    group: MuscleGroup = MuscleGroup.Chest,
    equipment: Equipment = Equipment.Barbell,
    name: String? = null,
    custom: Boolean = false,
) = Exercise(id, key, name, group, emptyList(), equipment, ExerciseMetricType.WeightReps, custom)

fun listed(
    id: String,
    name: String,
    group: MuscleGroup = MuscleGroup.Chest,
    equipment: Equipment = Equipment.Barbell,
) = CatalogExercise(catalogRow(id, "key-$id", group, equipment), name, hasMedia = true)

fun info(key: String, name: String) =
    ExerciseInfo(key, name, "Descripcion de $name", listOf("Paso 1", "Paso 2"), listOf("Consejo"))

class FakeExerciseLibrary(private val entries: Map<String, ExerciseInfo> = emptyMap()) : ExerciseLibrary {
    override suspend fun load(): Map<String, ExerciseInfo> = entries
}

class FakeExerciseRepository(var rows: List<Exercise> = emptyList(), var failure: AppError? = null) :
    ExerciseRepository {
    var createCalls = 0
    var createFailure: AppError? = null

    override suspend fun getAll(): Result<List<Exercise>> =
        failure?.let { Result.failure(AppException(it)) } ?: Result.success(rows)

    override suspend fun getById(id: String): Result<Exercise> =
        rows.firstOrNull { it.id == id }?.let { Result.success(it) }
            ?: Result.failure(AppException(AppError.NotFound))

    override suspend fun createCustom(name: String, muscleGroup: MuscleGroup, equipment: Equipment): Result<Exercise> {
        createCalls++
        createFailure?.let { return Result.failure(AppException(it)) }
        val created = catalogRow("custom-$createCalls", null, muscleGroup, equipment, name, custom = true)
        rows = rows + created
        return Result.success(created)
    }
}
