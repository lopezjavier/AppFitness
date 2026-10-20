package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup

/**
 * In-memory search of the exercise list: every whitespace-separated token of the query must appear in the name
 * (case and accent insensitive), ANDed with the optional muscle group and equipment filters. Sorted by name.
 */
class SearchExercises {
    operator fun invoke(
        items: List<CatalogExercise>,
        query: String,
        muscleGroup: MuscleGroup?,
        equipment: Equipment?,
    ): List<CatalogExercise> {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
        return items
            .asSequence()
            .filter { muscleGroup == null || it.exercise.muscleGroup == muscleGroup }
            .filter { equipment == null || it.exercise.equipment == equipment }
            .map { it to normalize(it.name) }
            .filter { (_, name) -> tokens.all { token -> name.contains(token) } }
            .sortedBy { (_, name) -> name }
            .map { it.first }
            .toList()
    }

    private fun normalize(text: String): String = normalizeText(text)
}
