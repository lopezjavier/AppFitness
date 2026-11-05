package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderInputs
import io.github.lopezjavier.appfitness.domain.coach.builder.BuiltBlock
import io.github.lopezjavier.appfitness.domain.model.BlockEdit
import io.github.lopezjavier.appfitness.domain.model.BlockSaved
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import kotlinx.datetime.LocalDate

/**
 * Training blocks of the coach (US-72). Failures carry an [io.github.lopezjavier.appfitness.domain.AppException] or a
 * [io.github.lopezjavier.appfitness.domain.model.CoachException].
 */
interface CoachBlockRepository {
    /** The 8-week history per exercise, the cardio options and the default cardio exercise for the block builder. */
    suspend fun loadBuilderInputs(today: LocalDate): Result<BuilderInputs>

    /**
     * Stores a block built on the device and saves it as a new folder (named [folderName], else "Bloque dd/mm/aaaa") in
     * one transaction. A plan that is no longer open fails with
     * [io.github.lopezjavier.appfitness.domain.model.CoachFailure.Conflict]; a block the server refuses fails with
     * [io.github.lopezjavier.appfitness.domain.model.CoachFailure.InvalidBlock] and saves nothing.
     */
    suspend fun saveBuiltBlock(planId: String, block: BuiltBlock, folderName: String? = null): Result<BlockSaved>

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
