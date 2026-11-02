package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow

/**
 * The listable exercises: DB rows merged with the client library. A catalog row takes its name from the library and
 * is dropped when the library lacks its key (it has no name to show, ADR-38); a custom row keeps its own name.
 */
class LoadExerciseCatalog(private val repository: ExerciseRepository, private val library: ExerciseLibrary) {
    /** Emits after every custom create, update or delete: a screen showing exercise names reloads on it. */
    val changes: Flow<Unit> get() = repository.changes

    suspend operator fun invoke(): Result<List<CatalogExercise>> {
        val rows = repository.getAll().getOrElse { return Result.failure(it) }
        val info = library.load()
        return Result.success(
            rows.mapNotNull { row -> displayNameOf(row, info)?.let { CatalogExercise(row, it, !row.isCustom) } },
        )
    }

    /** The displayable form of one exercise by id, with its library entry (null for custom or unknown keys). */
    suspend fun detail(id: String): Result<ExerciseDetail> {
        val row = repository.getById(id).getOrElse { return Result.failure(it) }
        val entries = library.load()
        // A catalogue row without a library entry has no name to show: it is not found, never an unnamed detail.
        return displayNameOf(row, entries)
            ?.let { Result.success(ExerciseDetail(row, it, row.key?.let { key -> entries[key] })) }
            ?: Result.failure(AppException(AppError.NotFound))
    }
}
