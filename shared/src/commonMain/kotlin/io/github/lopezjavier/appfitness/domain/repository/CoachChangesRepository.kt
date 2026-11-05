package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.FlagDecisionResult
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import io.github.lopezjavier.appfitness.domain.model.UndoResult

/**
 * Automatic load changes and flags of the coach (T-227, US-97, US-104). Refusals fail with a
 * `CoachChangeException`.
 */
interface CoachChangesRepository {
    /** Applies the progression decision of a finished session; empty outside a block or when nothing is due. */
    suspend fun apply(sessionId: String): Result<List<CoachChange>>

    /** Changes newest first, optionally of one plan and limited to [statuses] (empty = all). */
    suspend fun list(planId: String? = null, statuses: Set<CoachChangeStatus> = emptySet()): Result<List<CoachChange>>

    suspend fun undo(id: String): Result<UndoResult>

    suspend fun decide(id: String, decision: ProgressionFlagDecision): Result<FlagDecisionResult>
}
