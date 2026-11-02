package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseInfo
import io.github.lopezjavier.appfitness.domain.model.ExerciseNames
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart

/**
 * The one rule for an exercise's display name: a custom row keeps its own (non-blank) name, a catalogue row takes
 * the library name by `key`. Null when there is no name to show (R-54).
 */
internal fun displayNameOf(row: Exercise, library: Map<String, ExerciseInfo>): String? = if (row.isCustom) {
    row.name?.takeIf { it.isNotBlank() }
} else {
    row.key?.let { library[it]?.name }
}

/** The only source of exercise names for the UI. Screens receive resolved names and never choose a fallback. */
class ExerciseNameResolver(private val repository: ExerciseRepository, private val library: ExerciseLibrary) {
    /** Names of every readable exercise; a failure carries the repository's `AppException`. */
    suspend fun resolve(): Result<ExerciseNames> {
        val rows = repository.getAll().getOrElse { return Result.failure(it) }
        val info = library.load()
        return Result.success(
            ExerciseNames(
                rows.mapNotNull { row ->
                    displayNameOf(row, info)?.let { row.id to it }
                }.toMap(),
            ),
        )
    }

    /** Emits now and after every custom create/update/delete; a failed re-read emits nothing (keeps the last value). */
    fun observe(): Flow<ExerciseNames> = observeChanges().onStart { resolve().getOrNull()?.let { emit(it) } }

    /** Like [observe] without the first read: for callers that already called [resolve] themselves. */
    fun observeChanges(): Flow<ExerciseNames> = repository.changes.mapNotNull { resolve().getOrNull() }
}
