package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.DiscomfortFollowUp
import io.github.lopezjavier.appfitness.domain.model.SwapCandidate
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortFollowUpRepository

/** Used when Supabase is not configured: nothing can be read or stored. */
class UnconfiguredDiscomfortFollowUpRepository : DiscomfortFollowUpRepository {
    override suspend fun followUps(): Result<List<DiscomfortFollowUp>> = unavailable()

    override suspend fun swapCandidates(exerciseId: String): Result<List<SwapCandidate>> = unavailable()

    override suspend fun swap(exerciseId: String, newExerciseId: String): Result<Int> = unavailable()

    override suspend fun declineSwap(exerciseId: String): Result<Unit> = unavailable()

    override suspend fun dismissNote(exerciseId: String): Result<Unit> = unavailable()

    private fun <T> unavailable(): Result<T> = Result.failure(AppException(AppError.Unknown))
}
