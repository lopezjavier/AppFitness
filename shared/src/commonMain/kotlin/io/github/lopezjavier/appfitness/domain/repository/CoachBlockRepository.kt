package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.BlockSaved
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import kotlinx.datetime.LocalDate

/**
 * Training blocks of the coach (US-72). Failures carry an [io.github.lopezjavier.appfitness.domain.AppException] or a
 * [io.github.lopezjavier.appfitness.domain.model.CoachException].
 */
interface CoachBlockRepository {
    /** Asks the coach for the block of [planId] as of [today] and returns the stored proposal. Costs money. */
    suspend fun requestBlock(planId: String, today: LocalDate): Result<CoachBlock>

    /** The stored block of [planId], or null when the coach has not proposed one. Used to reload and reopen. */
    suspend fun currentBlock(planId: String): Result<CoachBlock?>

    /** Turns the stored block into one folder with its routines in Entreno (idempotent). */
    suspend fun saveBlock(planId: String): Result<BlockSaved>
}
