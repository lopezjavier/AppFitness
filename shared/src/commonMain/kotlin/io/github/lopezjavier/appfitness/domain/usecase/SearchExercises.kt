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

    companion object {
        private val folds: Map<Char, Char> =
            buildMap {
                "áàâäãå".forEach { put(it, 'a') }
                "éèêë".forEach { put(it, 'e') }
                "íìîï".forEach { put(it, 'i') }
                "óòôöõ".forEach { put(it, 'o') }
                "úùûü".forEach { put(it, 'u') }
                put('ñ', 'n')
                put('ç', 'c')
            }

        /** Lowercase, accents folded (explicit map: no JVM-only APIs), whitespace collapsed and trimmed. */
        fun normalize(text: String): String = text
            .lowercase()
            .map { folds[it] ?: it }
            .joinToString("")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }
}
