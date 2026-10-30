package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.DeloadExerciseSuggestion

/** Deload line of a live session (T-165). Empty outside a deload week of the session's routine. */
interface DeloadRepository {
    suspend fun suggestionsFor(sessionId: String): Result<List<DeloadExerciseSuggestion>>
}
