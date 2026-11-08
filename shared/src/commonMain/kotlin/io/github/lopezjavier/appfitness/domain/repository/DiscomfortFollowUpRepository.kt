package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.DiscomfortFollowUp
import io.github.lopezjavier.appfitness.domain.model.SwapCandidate

/** Swap offer and health note of an unresolved level 4-5 discomfort. The database is the source of truth. */
interface DiscomfortFollowUpRepository {
    suspend fun followUps(): Result<List<DiscomfortFollowUp>>

    suspend fun swapCandidates(exerciseId: String): Result<List<SwapCandidate>>

    /** Replaces the flagged exercise in every routine of the accepted block; returns the routines changed. */
    suspend fun swap(exerciseId: String, newExerciseId: String): Result<Int>

    suspend fun declineSwap(exerciseId: String): Result<Unit>

    suspend fun dismissNote(exerciseId: String): Result<Unit>
}
