package io.github.lopezjavier.appfitness.feature.exercises

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseInfo
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository

/** Synthetic exercise rows and library (never RepDB content). */
fun exerciseRow(
    id: String,
    key: String?,
    group: MuscleGroup = MuscleGroup.Chest,
    equipment: Equipment = Equipment.Barbell,
    name: String? = null,
    custom: Boolean = false,
) = Exercise(id, key, name, group, emptyList(), equipment, ExerciseMetricType.WeightReps, custom)

fun exerciseInfo(key: String, name: String, hasPeak: Boolean = true) =
    ExerciseInfo(key, name, "Descripcion", listOf("Primer paso", "Segundo paso"), listOf("Un consejo"), hasPeak)

class FakeExerciseLibrary(private val entries: Map<String, ExerciseInfo> = emptyMap()) : ExerciseLibrary {
    override suspend fun load(): Map<String, ExerciseInfo> = entries
}

class FakeExerciseRepository(var rows: List<Exercise> = emptyList(), var failure: AppError? = null) :
    ExerciseRepository {
    var createCalls = 0
    var createFailure: AppError? = null
    var lastCreated: Triple<String, MuscleGroup, Equipment>? = null

    override suspend fun getAll(): Result<List<Exercise>> =
        failure?.let { Result.failure(AppException(it)) } ?: Result.success(rows)

    override suspend fun getById(id: String): Result<Exercise> =
        rows.firstOrNull { it.id == id }?.let { Result.success(it) }
            ?: Result.failure(AppException(AppError.NotFound))

    override suspend fun createCustom(name: String, muscleGroup: MuscleGroup, equipment: Equipment): Result<Exercise> {
        createCalls++
        lastCreated = Triple(name, muscleGroup, equipment)
        createFailure?.let { return Result.failure(AppException(it)) }
        val created = exerciseRow("custom-$createCalls", null, muscleGroup, equipment, name, custom = true)
        rows = rows + created
        return Result.success(created)
    }
}

/** Image URIs that need no bundled files; the host tests answer them with a fake Coil engine. */
object FakeExerciseMediaUris : ExerciseMediaUris {
    override fun start(key: String): String = "https://img.example/$key-start.webp"

    override fun peak(key: String): String = "https://img.example/$key-peak.webp"
}
