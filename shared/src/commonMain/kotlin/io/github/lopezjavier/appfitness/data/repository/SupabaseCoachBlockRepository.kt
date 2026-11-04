package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.BlockEditDto
import io.github.lopezjavier.appfitness.data.remote.BlockRequestBody
import io.github.lopezjavier.appfitness.data.remote.BlockRequestInput
import io.github.lopezjavier.appfitness.data.remote.BlockSplitInput
import io.github.lopezjavier.appfitness.data.remote.BuilderInputsDto
import io.github.lopezjavier.appfitness.data.remote.BuilderInputsParams
import io.github.lopezjavier.appfitness.data.remote.BuiltBlockParams
import io.github.lopezjavier.appfitness.data.remote.RemoveBlockParams
import io.github.lopezjavier.appfitness.data.remote.SaveBlockParams
import io.github.lopezjavier.appfitness.data.remote.SaveBlockResultDto
import io.github.lopezjavier.appfitness.data.remote.SplitOptionDto
import io.github.lopezjavier.appfitness.data.remote.SplitOptionsDto
import io.github.lopezjavier.appfitness.data.remote.SplitRequestBody
import io.github.lopezjavier.appfitness.data.remote.SplitRequestInput
import io.github.lopezjavier.appfitness.data.remote.SplitSessionDto
import io.github.lopezjavier.appfitness.data.remote.coachPlanJson
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderInputs
import io.github.lopezjavier.appfitness.domain.coach.builder.BuiltBlock
import io.github.lopezjavier.appfitness.domain.model.BlockEdit
import io.github.lopezjavier.appfitness.domain.model.BlockSaved
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.SplitOption
import io.github.lopezjavier.appfitness.domain.model.requestKey
import io.github.lopezjavier.appfitness.domain.port.CoachModelPreference
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

private const val WRONG_OBJECT_STATE = "55000"
private const val INVALID_PARAMETER = "22023"

/**
 * Training blocks through the `coach` function (`plan_block`, whole SSE body read as text) and the
 * `save_coach_block` RPC; reads go through the owner-select RLS of `coach_plans` and `coach_plan_sessions`.
 */
@Suppress("TooManyFunctions") // One small function per block operation of the interface.
class SupabaseCoachBlockRepository internal constructor(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val functions: Functions,
    private val modelPreference: CoachModelPreference,
    private val requestTimeout: Duration,
) : CoachBlockRepository {
    constructor(auth: Auth, postgrest: Postgrest, functions: Functions, modelPreference: CoachModelPreference) :
        this(auth, postgrest, functions, modelPreference, COACH_CLIENT_TIMEOUT)

    override suspend fun requestSplitOptions(today: LocalDate): Result<List<SplitOption>> {
        val model = modelPreference.selected.value.requestKey()
        val body = coachPlanJson.encodeToString(SplitRequestBody("block_splits", SplitRequestInput(today), model))
        return callCoach(body) { text ->
            val options = runCatching { coachPlanJson.decodeFromString<SplitOptionsDto>(text) }.getOrNull()
                ?.options?.map(SplitOptionDto::toDomain)
            if (options.isNullOrEmpty()) {
                Result.failure(CoachException(CoachFailure.Other))
            } else {
                Result.success(options)
            }
        }
    }

    override suspend fun requestBlock(planId: String, today: LocalDate, split: SplitOption): Result<CoachBlock> {
        val model = modelPreference.selected.value.requestKey()
        val chosen = BlockSplitInput(split.name, split.sessions.map { SplitSessionDto(it.name, it.focus) })
        val body = coachPlanJson.encodeToString(
            BlockRequestBody("plan_block", BlockRequestInput(today, planId, chosen), model),
        )
        return callCoach(body) { storedBlockOrOther(planId) }
    }

    /** Posts [body] to the `coach` function and hands the concatenated stream text to [onText]. */
    private suspend fun <T> callCoach(body: String, onText: suspend (String) -> Result<T>): Result<T> =
        callCoachFunction(auth, functions, requestTimeout, body, onText)

    override suspend fun currentBlock(planId: String): Result<CoachBlock?> = auth.ownRow { loadBlock(planId) }

    override suspend fun saveBlock(planId: String, edits: List<BlockEdit>): Result<BlockSaved> = auth.ownRow {
        val params = SaveBlockParams(planId, edits.map { BlockEditDto(it.sessionIndex, it.position, it.exerciseId) })
        postgrest.rpc("save_coach_block", params).decodeAs<SaveBlockResultDto>().toDomain()
    }.mapSaveErrors()

    override suspend fun loadBuilderInputs(today: LocalDate): Result<BuilderInputs> = auth.ownRow {
        postgrest.rpc("coach_builder_inputs", BuilderInputsParams(today)).decodeAs<BuilderInputsDto>().toDomain()
    }

    override suspend fun saveBuiltBlock(planId: String, block: BuiltBlock, folderName: String?): Result<BlockSaved> =
        auth.ownRow {
            postgrest.rpc("save_built_coach_block", BuiltBlockParams(planId, block, folderName).toJson())
                .decodeAs<SaveBlockResultDto>().toDomain()
        }.mapBuiltSaveErrors()

    override suspend fun removeBlock(planId: String): Result<Unit> = auth.ownRow {
        postgrest.rpc("remove_coach_block", RemoveBlockParams(planId))
        Unit
    }.mapRemoveErrors()

    /** A finished stream whose block cannot be read back is [CoachFailure.Other]. */
    private suspend fun storedBlockOrOther(planId: String): Result<CoachBlock> {
        val block = loadBlock(planId)
        return if (block != null) Result.success(block) else Result.failure(CoachException(CoachFailure.Other))
    }

    private suspend fun loadBlock(planId: String): CoachBlock? = loadStoredBlock(postgrest, planId)

    /** SQLSTATE 55000: the plan is no longer open. */
    private fun <T> Result<T>.mapRemoveErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        if (cause.code == WRONG_OBJECT_STATE) throw CoachException(CoachFailure.Conflict) else throw failure
    }

    /** SQLSTATE 55000: the plan is no longer open; 22023: the server refused a block invariant. */
    private fun <T> Result<T>.mapBuiltSaveErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        when (cause.code) {
            WRONG_OBJECT_STATE -> throw CoachException(CoachFailure.Conflict)
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
