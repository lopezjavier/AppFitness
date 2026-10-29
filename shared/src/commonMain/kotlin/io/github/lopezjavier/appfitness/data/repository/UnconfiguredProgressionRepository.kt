package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import io.github.lopezjavier.appfitness.domain.repository.ProgressionRepository

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredProgressionRepository : ProgressionRepository {
    override suspend fun suggestionsFor(sessionId: String): Result<List<ProgressionSuggestion>> =
        Result.failure(AppException(AppError.Unknown))
}
