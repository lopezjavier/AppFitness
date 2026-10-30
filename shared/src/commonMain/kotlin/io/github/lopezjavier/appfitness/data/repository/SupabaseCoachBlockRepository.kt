package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.BlockRequestBody
import io.github.lopezjavier.appfitness.data.remote.BlockRequestInput
import io.github.lopezjavier.appfitness.data.remote.BlockSessionDto
import io.github.lopezjavier.appfitness.data.remote.CoachBlockPlanDto
import io.github.lopezjavier.appfitness.data.remote.CoachErrorParser
import io.github.lopezjavier.appfitness.data.remote.CoachStreamParser
import io.github.lopezjavier.appfitness.data.remote.SaveBlockParams
import io.github.lopezjavier.appfitness.data.remote.SaveBlockResultDto
import io.github.lopezjavier.appfitness.data.remote.blockOf
import io.github.lopezjavier.appfitness.data.remote.coachPlanJson
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockSaved
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate

private const val WRONG_OBJECT_STATE = "55000"

/**
 * Training blocks through the `coach` function (`plan_block`, whole SSE body read as text) and the
 * `save_coach_block` RPC; reads go through the owner-select RLS of `coach_plans` and `coach_plan_sessions`.
 */
class SupabaseCoachBlockRepository(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val functions: Functions,
) : CoachBlockRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun requestBlock(planId: String, today: LocalDate): Result<CoachBlock> {
        if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
        return try {
            val body = coachPlanJson.encodeToString(BlockRequestBody("plan_block", BlockRequestInput(today, planId)))
            val stream = functions.invoke("coach") {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()
            CoachStreamParser.parse(stream).fold(
                onSuccess = { storedBlockOrOther(planId) },
                onFailure = { Result.failure(it) },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: RestException) {
            Result.failure(CoachException(CoachErrorParser.parse(failure.description ?: failure.error)))
        } catch (failure: Exception) {
            Result.failure(AppException(failure.toAppError(), failure))
        }
    }

    override suspend fun currentBlock(planId: String): Result<CoachBlock?> = auth.ownRow { loadBlock(planId) }

    override suspend fun saveBlock(planId: String): Result<BlockSaved> = auth.ownRow {
        postgrest.rpc("save_coach_block", SaveBlockParams(planId)).decodeAs<SaveBlockResultDto>().toDomain()
    }.mapSaveErrors()

    /** A finished stream whose block cannot be read back is [CoachFailure.Other]. */
    private suspend fun storedBlockOrOther(planId: String): Result<CoachBlock> {
        val block = loadBlock(planId)
        return if (block != null) Result.success(block) else Result.failure(CoachException(CoachFailure.Other))
    }

    private suspend fun loadBlock(planId: String): CoachBlock? {
        val plan = postgrest.from("coach_plans")
            .select { filter { eq("id", planId) } }
            .decodeList<CoachBlockPlanDto>()
            .firstOrNull() ?: return null
        val sessions = postgrest.from("coach_plan_sessions")
            .select {
                filter { eq("plan_id", planId) }
                order("week", Order.ASCENDING)
                order("session_index", Order.ASCENDING)
            }
            .decodeList<BlockSessionDto>()
        return blockOf(plan, sessions)
    }

    /** SQLSTATE 55000: the plan is no longer open or has no block to save. */
    private fun <T> Result<T>.mapSaveErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        if (cause.code != WRONG_OBJECT_STATE) throw failure
        throw CoachException(CoachFailure.Conflict)
    }
}
