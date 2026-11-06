package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.BlockEditDto
import io.github.lopezjavier.appfitness.data.remote.BuilderInputsDto
import io.github.lopezjavier.appfitness.data.remote.BuilderInputsParams
import io.github.lopezjavier.appfitness.data.remote.BuiltBlockParams
import io.github.lopezjavier.appfitness.data.remote.RemoveBlockParams
import io.github.lopezjavier.appfitness.data.remote.SaveBlockParams
import io.github.lopezjavier.appfitness.data.remote.SaveBlockResultDto
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderInputs
import io.github.lopezjavier.appfitness.domain.coach.builder.BuiltBlock
import io.github.lopezjavier.appfitness.domain.model.BlockEdit
import io.github.lopezjavier.appfitness.domain.model.BlockSaved
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import kotlinx.datetime.LocalDate

private const val WRONG_OBJECT_STATE = "55000"
private const val INVALID_PARAMETER = "22023"

/**
 * Training blocks through the `save_built_coach_block` and `save_coach_block` RPCs; reads go through the owner-select
 * RLS of `coach_plans` and `coach_plan_sessions`.
 */
@Suppress("TooManyFunctions") // One small function per block operation of the interface.
class SupabaseCoachBlockRepository(private val auth: Auth, private val postgrest: Postgrest) : CoachBlockRepository {
    override suspend fun currentBlock(planId: String): Result<CoachBlock?> = auth.ownRow { loadBlock(planId) }

    override suspend fun saveBlock(planId: String, edits: List<BlockEdit>): Result<BlockSaved> = auth.ownRow {
        val params = SaveBlockParams(planId, edits.map { BlockEditDto(it.sessionIndex, it.position, it.exerciseId) })
        postgrest.rpc("save_coach_block", params).decodeAs<SaveBlockResultDto>().toDomain()
    }.mapSaveErrors()

    override suspend fun loadBuilderInputs(today: LocalDate): Result<BuilderInputs> = auth.ownRow {
        postgrest.rpc("coach_builder_inputs", BuilderInputsParams(today)).decodeAs<BuilderInputsDto>().toDomain()
    }

    override suspend fun saveBuiltBlock(
        planId: String,
        block: BuiltBlock,
        folderName: String?,
        previousPlanId: String?,
    ): Result<BlockSaved> = auth.ownRow {
        postgrest.rpc(
            "save_built_coach_block",
            BuiltBlockParams(planId, block, folderName, previousPlanId).toJson(),
        )
            .decodeAs<SaveBlockResultDto>().toDomain()
    }.mapBuiltSaveErrors()

    override suspend fun removeBlock(planId: String): Result<Unit> = auth.ownRow {
        postgrest.rpc("remove_coach_block", RemoveBlockParams(planId))
        Unit
    }.mapRemoveErrors()

    private suspend fun loadBlock(planId: String): CoachBlock? = loadStoredBlock(postgrest, planId)

    /** SQLSTATE 55000: the plan is no longer open. */
    private fun <T> Result<T>.mapRemoveErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        if (cause.code == WRONG_OBJECT_STATE) throw CoachException(CoachFailure.Conflict) else throw failure
    }

    /**
     * SQLSTATE 55000: the plan or the previous block is no longer in the expected state, or accepting the next plan
     * found a derived value unconfirmed; 22023: the server refused a block invariant.
     */
    private fun <T> Result<T>.mapBuiltSaveErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        when (cause.code) {
            WRONG_OBJECT_STATE -> {
                val text = listOfNotNull(cause.error, cause.description, cause.message).joinToString(" ")
                throw CoachException(
                    if (text.contains("unconfirmed")) CoachFailure.NeedsConfirmation else CoachFailure.Conflict,
                )
            }

            INVALID_PARAMETER -> throw CoachException(CoachFailure.InvalidBlock)

            else -> throw failure
        }
    }

    /** SQLSTATE 55000: the plan is no longer open or has no block to save. */
    private fun <T> Result<T>.mapSaveErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        when (cause.code) {
            WRONG_OBJECT_STATE -> throw CoachException(CoachFailure.Conflict)
            INVALID_PARAMETER -> throw CoachException(CoachFailure.InvalidReplacement)
            else -> throw failure
        }
    }
}
