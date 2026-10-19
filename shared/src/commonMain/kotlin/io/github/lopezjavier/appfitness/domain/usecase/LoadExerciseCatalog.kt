package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository

/**
 * The listable exercises: DB rows merged with the client library. A catalog row takes its name from the library and
 * is dropped when the library lacks its key (it has no name to show, ADR-38); a custom row keeps its own name.
 */
class LoadExerciseCatalog(private val repository: ExerciseRepository, private val library: ExerciseLibrary) {
    suspend operator fun invoke(): Result<List<CatalogExercise>> {
        val rows = repository.getAll().getOrElse { return Result.failure(it) }
        val info = library.load()
        return Result.success(
            rows.mapNotNull { row ->
                when {
                    row.isCustom -> row.name?.takeIf { it.isNotBlank() }?.let { CatalogExercise(row, it, false) }
                    else -> row.key?.let { info[it] }?.let { CatalogExercise(row, it.name, true) }
                }
            },
        )
    }

    /** The displayable form of one exercise by id, with its library entry (null for custom or unknown keys). */
    suspend fun detail(id: String): Result<ExerciseDetail> {
        val row = repository.getById(id).getOrElse { return Result.failure(it) }
        val info = row.key?.let { library.load()[it] }
        val name = if (row.isCustom) row.name else info?.name
        return Result.success(ExerciseDetail(row, name.orEmpty(), info))
    }
}
