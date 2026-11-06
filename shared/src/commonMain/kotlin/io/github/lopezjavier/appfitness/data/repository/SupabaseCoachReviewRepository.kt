package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.CoachErrorParser
import io.github.lopezjavier.appfitness.data.remote.CoachStreamParser
import io.github.lopezjavier.appfitness.data.remote.KeepPhaseParams
import io.github.lopezjavier.appfitness.data.remote.OpenBlockReviewParams
import io.github.lopezjavier.appfitness.data.remote.PhaseChangeRequestBody
import io.github.lopezjavier.appfitness.data.remote.PhaseChangeRequestInput
import io.github.lopezjavier.appfitness.data.remote.PlanEventDto
import io.github.lopezjavier.appfitness.data.remote.blockReviewOf
import io.github.lopezjavier.appfitness.data.remote.coachPlanJson
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration

/**
 * The block review through `open_coach_block_review` and `keep_coach_phase`; "Aceptar" goes through the `coach`
 * function kind `plan_phase_change` (deterministic: no model key, no usage row).
 */
class SupabaseCoachReviewRepository internal constructor(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val functions: Functions,
    private val requestTimeout: Duration,
) : CoachReviewRepository {
    constructor(auth: Auth, postgrest: Postgrest, functions: Functions) :
        this(auth, postgrest, functions, COACH_CLIENT_TIMEOUT)

    override suspend fun openReview(today: LocalDate): Result<BlockReview?> = auth.ownRow {
        blockReviewOf(postgrest.rpc("open_coach_block_review", OpenBlockReviewParams(today)).decodeAs<JsonElement>())
    }

    override suspend fun keepPhase(reviewId: String): Result<Unit> = auth.ownRow {
        postgrest.rpc("keep_coach_phase", KeepPhaseParams(reviewId))
        Unit
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun acceptPhase(reviewId: String, today: LocalDate): Result<String> {
        if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
        return try {
            val body = coachPlanJson.encodeToString(
                PhaseChangeRequestBody("plan_phase_change", PhaseChangeRequestInput(today, reviewId)),
            )
            val stream = functions.invoke("coach") {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(body)
                coachTimeout(requestTimeout)
            }.bodyAsText()
            CoachStreamParser.parse(stream).fold(
                onSuccess = { text -> planIdOf(text) },
                onFailure = { Result.failure(it) },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: HttpRequestTimeoutException) {
            Result.failure(CoachException(CoachFailure.Timeout))
        } catch (failure: RestException) {
            Result.failure(CoachException(CoachErrorParser.parse(failure.description ?: failure.error)))
        } catch (failure: Exception) {
            Result.failure(AppException(failure.toAppError(), failure))
        }
    }

    private fun planIdOf(eventText: String): Result<String> {
        val id = runCatching { coachPlanJson.decodeFromString<PlanEventDto>(eventText) }.getOrNull()?.planId
        return if (id != null) Result.success(id) else Result.failure(CoachException(CoachFailure.Other))
    }
}
