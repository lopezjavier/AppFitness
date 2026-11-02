package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository

/** Primary muscle groups and metric types by exercise id from one read; names come from ExerciseNameResolver (R-54). */
internal class ExerciseLookup(
    val groups: Map<String, MuscleGroup>,
    val metricTypes: Map<String, ExerciseMetricType> = emptyMap(),
)

/** Groups and metric types of every exercise; null when the exercises cannot be read. */
internal suspend fun loadExerciseLookup(repository: ExerciseRepository): ExerciseLookup? {
    val rows = repository.getAll().getOrNull() ?: return null
    return ExerciseLookup(rows.associate { it.id to it.muscleGroup }, rows.associate { it.id to it.metricType })
}
