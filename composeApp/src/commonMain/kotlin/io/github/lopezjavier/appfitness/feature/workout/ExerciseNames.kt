package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository

/**
 * Display names by exercise id: catalog exercises come from the [library] by key, custom ones from their row.
 * Returns null when the exercises cannot be read, so callers keep what they had (rows then show no name).
 */
internal suspend fun loadExerciseNames(repository: ExerciseRepository, library: ExerciseLibrary): Map<String, String>? {
    val rows = repository.getAll().getOrNull() ?: return null
    val catalog = library.load()
    return rows.mapNotNull { row ->
        val name = if (row.isCustom) row.name else row.key?.let { catalog[it]?.name }
        name?.let { row.id to it }
    }.toMap()
}
