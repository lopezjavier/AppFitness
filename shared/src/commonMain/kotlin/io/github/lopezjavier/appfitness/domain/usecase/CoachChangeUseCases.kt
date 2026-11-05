package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.FlagDecisionResult
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import io.github.lopezjavier.appfitness.domain.model.UndoResult
import io.github.lopezjavier.appfitness.domain.repository.CoachChangesRepository

/** Asks the server to apply the progression of a finished session; idempotent, so a retry is harmless. */
class ApplyCoachProgressionUseCase(private val repository: CoachChangesRepository) {
    suspend operator fun invoke(sessionId: String): Result<List<CoachChange>> = repository.apply(sessionId)
}

/** Coach changes newest first, optionally of one plan and in some statuses. */
class ListCoachChangesUseCase(private val repository: CoachChangesRepository) {
    suspend operator fun invoke(
        planId: String? = null,
        statuses: Set<CoachChangeStatus> = emptySet(),
    ): Result<List<CoachChange>> = repository.list(planId, statuses)
}

/** Restores the previous planned values of an applied change, except in routines edited since. */
class UndoCoachChangeUseCase(private val repository: CoachChangesRepository) {
    suspend operator fun invoke(id: String): Result<UndoResult> = repository.undo(id)
}

/** Resolves a flagged raise: raise anyway or keep the weight and add repetitions. */
class DecideProgressionFlagUseCase(private val repository: CoachChangesRepository) {
    suspend operator fun invoke(id: String, decision: ProgressionFlagDecision): Result<FlagDecisionResult> =
        repository.decide(id, decision)
}
