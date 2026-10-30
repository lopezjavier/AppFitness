package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.DeloadExerciseSuggestion
import io.github.lopezjavier.appfitness.domain.repository.DeloadRepository

/** In-memory deload suggestions; [failing] makes the read fail (offline or error). */
internal class FakeDeloadRepository(
    var suggestions: List<DeloadExerciseSuggestion> = emptyList(),
    var failing: Boolean = false,
) : DeloadRepository {
    val requestedSessions = mutableListOf<String>()

    override suspend fun suggestionsFor(sessionId: String): Result<List<DeloadExerciseSuggestion>> {
        requestedSessions += sessionId
        return if (failing) Result.failure(AppException(AppError.Network)) else Result.success(suggestions)
    }
}
