package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.DeloadExerciseSuggestion
import io.github.lopezjavier.appfitness.domain.repository.DeloadRepository

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredDeloadRepository : DeloadRepository {
    override suspend fun suggestionsFor(sessionId: String): Result<List<DeloadExerciseSuggestion>> =
        Result.failure(AppException(AppError.Unknown))
}
