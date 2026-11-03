package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.BlockEdit
import io.github.lopezjavier.appfitness.domain.model.BlockSaved
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.SplitOption
import kotlinx.datetime.LocalDate

/**
 * Training blocks of the coach (US-72). Failures carry an [io.github.lopezjavier.appfitness.domain.AppException] or a
 * [io.github.lopezjavier.appfitness.domain.model.CoachException].
 */
interface CoachBlockRepository {
    /** Asks the coach for 2-3 ways to split the training days (exactly one is recommended). Costs money. */
    suspend fun requestSplitOptions(today: LocalDate): Result<List<SplitOption>>

    /**
     * Asks the coach to build the block of [planId] as of [today] on the chosen [split] and returns the stored
     * proposal. Costs money.
     */
    suspend fun requestBlock(planId: String, today: LocalDate, split: SplitOption): Result<CoachBlock>

    /** The stored block of [planId], or null when the coach has not proposed one. Used to reload and reopen. */
    suspend fun currentBlock(planId: String): Result<CoachBlock?>

    /**
     * Turns the stored block into one folder with its routines in Entreno (idempotent: a second call ignores
     * [edits]). An [edits] entry that is not an alternative of its exercise fails with
     * [io.github.lopezjavier.appfitness.domain.model.CoachFailure.InvalidReplacement] and saves nothing.
     */
    suspend fun saveBlock(planId: String, edits: List<BlockEdit> = emptyList()): Result<BlockSaved>

    /**
     * Removes the saved block of [planId] (its weeks and the plan's folder link) and keeps the plan, the folder and
     * the routines in Entreno (idempotent). A plan that is no longer open fails with
     * [io.github.lopezjavier.appfitness.domain.model.CoachFailure.Conflict].
     */
    suspend fun removeBlock(planId: String): Result<Unit>
}
