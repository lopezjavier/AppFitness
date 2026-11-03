package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.lopezjavier.appfitness.data.remote.BlockSessionDto
import io.github.lopezjavier.appfitness.data.remote.CoachBlockPlanDto
import io.github.lopezjavier.appfitness.data.remote.CoachErrorParser
import io.github.lopezjavier.appfitness.data.remote.CoachStreamParser
import io.github.lopezjavier.appfitness.data.remote.blockOf
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration

/** The stored block of [planId] read through the owner-select RLS, or null when the plan has none. */
internal suspend fun loadStoredBlock(postgrest: Postgrest, planId: String): CoachBlock? {
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

/** Posts [body] to the `coach` function and hands the concatenated stream text to [onText]. */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> callCoachFunction(
    auth: Auth,
    functions: Functions,
    requestTimeout: Duration,
    body: String,
    onText: suspend (String) -> Result<T>,
): Result<T> {
    if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
    return try {
        val stream = functions.invoke("coach") {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(body)
            coachTimeout(requestTimeout)
        }.bodyAsText()
        CoachStreamParser.parse(stream).fold(
            onSuccess = { onText(it) },
            onFailure = { Result.failure(it) },
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: HttpRequestTimeoutException) {
        // The model took too long: the service is reachable, so offer a retry instead of "offline".
        Result.failure(CoachException(CoachFailure.Timeout))
    } catch (failure: RestException) {
        Result.failure(CoachException(CoachErrorParser.parse(failure.description ?: failure.error)))
    } catch (failure: Exception) {
        Result.failure(AppException(failure.toAppError(), failure))
    }
}
