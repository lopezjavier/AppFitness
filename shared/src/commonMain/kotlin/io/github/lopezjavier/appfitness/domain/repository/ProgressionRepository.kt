package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion

/** Deterministic progression suggestions of a live session (T-159). Exercises without a suggestion are absent. */
interface ProgressionRepository {
    suspend fun suggestionsFor(sessionId: String): Result<List<ProgressionSuggestion>>
}
