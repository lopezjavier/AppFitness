package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.DiscomfortFollowUp
import io.github.lopezjavier.appfitness.domain.model.SwapCandidate
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortFollowUpRepository

/**
 * In-memory follow-ups. [failing] makes every call fail; a successful [swap] drops the offer of that exercise (the
 * server does the same: it leaves the block) and records the pair in [swaps]. Candidates are per flagged exercise.
 */
internal class FakeDiscomfortFollowUpRepository(
    var followUps: List<DiscomfortFollowUp> = emptyList(),
    var candidates: Map<String, List<SwapCandidate>> = emptyMap(),
    var failing: Boolean = false,
) : DiscomfortFollowUpRepository {
    var followUpCalls = 0
    var candidateCalls = 0
    val swaps = mutableListOf<Pair<String, String>>()
    val declined = mutableListOf<String>()
    val dismissed = mutableListOf<String>()

    override suspend fun followUps(): Result<List<DiscomfortFollowUp>> {
        followUpCalls++
        return if (failing) failure() else Result.success(followUps)
    }

    override suspend fun swapCandidates(exerciseId: String): Result<List<SwapCandidate>> {
        candidateCalls++
        return if (failing) failure() else Result.success(candidates[exerciseId].orEmpty())
    }

    override suspend fun swap(exerciseId: String, newExerciseId: String): Result<Int> {
        if (failing) return failure()
        swaps += exerciseId to newExerciseId
        followUps = followUps.map { if (it.exerciseId == exerciseId) it.copy(offerSwap = false) else it }
        return Result.success(1)
    }

    override suspend fun declineSwap(exerciseId: String): Result<Unit> {
        if (failing) return failure()
        declined += exerciseId
        followUps = followUps.map { if (it.exerciseId == exerciseId) it.copy(offerSwap = false) else it }
        return Result.success(Unit)
    }

    override suspend fun dismissNote(exerciseId: String): Result<Unit> {
        if (failing) return failure()
        dismissed += exerciseId
        followUps = followUps.map {
            if (it.exerciseId == exerciseId) it.copy(showNote = false, noteReason = null) else it
        }
        return Result.success(Unit)
    }

    private fun <T> failure(): Result<T> = Result.failure(AppException(AppError.Network))
}
