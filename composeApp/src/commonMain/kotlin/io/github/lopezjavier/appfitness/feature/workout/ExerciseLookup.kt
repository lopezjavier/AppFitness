package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository

/** Display names and primary muscle groups by exercise id, from a single read of the exercises. */
internal class ExerciseLookup(
    val names: Map<String, String>,
    val groups: Map<String, MuscleGroup>,
    val metricTypes: Map<String, ExerciseMetricType> = emptyMap(),
)

/** Names, groups and metric types of every exercise; null when the exercises cannot be read. */
internal suspend fun loadExerciseLookup(repository: ExerciseRepository, library: ExerciseLibrary): ExerciseLookup? {
    val rows = repository.getAll().getOrNull() ?: return null
    val catalog = library.load()
    val names = rows.mapNotNull { row ->
        val name = if (row.isCustom) row.name else row.key?.let { catalog[it]?.name }
        name?.let { row.id to it }
    }.toMap()
    return ExerciseLookup(names, rows.associate { it.id to it.muscleGroup }, rows.associate { it.id to it.metricType })
}

/**
 * Display names by exercise id: catalog exercises come from the [library] by key, custom ones from their row.
 * Returns null when the exercises cannot be read, so callers keep what they had (rows then show no name).
 */
internal suspend fun loadExerciseNames(repository: ExerciseRepository, library: ExerciseLibrary): Map<String, String>? =
    loadExerciseLookup(repository, library)?.names
