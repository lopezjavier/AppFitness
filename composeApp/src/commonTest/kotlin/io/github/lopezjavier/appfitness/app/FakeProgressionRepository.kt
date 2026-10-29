package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import io.github.lopezjavier.appfitness.domain.repository.ProgressionRepository

/** In-memory suggestions; [failing] makes the read fail (offline or error). */
internal class FakeProgressionRepository(
    var suggestions: List<ProgressionSuggestion> = emptyList(),
    var failing: Boolean = false,
) : ProgressionRepository {
    val requestedSessions = mutableListOf<String>()

    override suspend fun suggestionsFor(sessionId: String): Result<List<ProgressionSuggestion>> {
        requestedSessions += sessionId
        return if (failing) Result.failure(AppException(AppError.Network)) else Result.success(suggestions)
    }
}
