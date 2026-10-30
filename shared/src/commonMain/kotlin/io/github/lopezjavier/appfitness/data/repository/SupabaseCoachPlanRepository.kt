package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.AcceptPlanParams
import io.github.lopezjavier.appfitness.data.remote.CoachErrorParser
import io.github.lopezjavier.appfitness.data.remote.CoachPlanDto
import io.github.lopezjavier.appfitness.data.remote.CoachRecommendationDto
import io.github.lopezjavier.appfitness.data.remote.CoachStreamParser
import io.github.lopezjavier.appfitness.data.remote.DecideRecommendationParams
import io.github.lopezjavier.appfitness.data.remote.DiscardPlanParams
import io.github.lopezjavier.appfitness.data.remote.EvaluateCalorieParams
import io.github.lopezjavier.appfitness.data.remote.PlanEventDto
import io.github.lopezjavier.appfitness.data.remote.PlanRequestBody
import io.github.lopezjavier.appfitness.data.remote.PlanRequestInput
import io.github.lopezjavier.appfitness.data.remote.coachPlanJson
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CalorieChange
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate

private const val WRONG_OBJECT_STATE = "55000"

/**
 * Plans through the `coach` function (`plan_nutrition`, whole SSE body read as text) and the T-161 RPCs; reads go
 * through the owner-select RLS of `coach_plans` and `coach_recommendations`.
 */
class SupabaseCoachPlanRepository(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val functions: Functions,
) : CoachPlanRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun requestPlan(today: LocalDate): Result<CoachPlan> {
        if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
        return try {
            val body = coachPlanJson.encodeToString(PlanRequestBody("plan_nutrition", PlanRequestInput(today)))
            val stream = functions.invoke("coach") {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()
            CoachStreamParser.parse(stream).fold(
                onSuccess = { text -> storedPlanOf(text) },
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

    override suspend fun currentPlan(): Result<CoachPlan?> = auth.ownRow {
        val plans = postgrest.from("coach_plans")
            .select {
                filter { isIn("status", listOf("proposed", "accepted")) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<CoachPlanDto>()
            .map { it.toDomain() }
        plans.firstOrNull { it.status == PlanStatus.Proposed } ?: plans.firstOrNull()
    }

    override suspend fun acceptedPlan(): Result<CoachPlan?> = auth.ownRow {
        postgrest.from("coach_plans")
            .select {
                filter { eq("status", "accepted") }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<CoachPlanDto>()
            .firstOrNull()?.toDomain()
    }

    override suspend fun acceptPlan(planId: String, today: LocalDate): Result<CoachPlan> = auth.ownRow {
        postgrest.rpc("accept_coach_plan", AcceptPlanParams(planId, today)).decodeAs<CoachPlanDto>().toDomain()
    }.mapPlanErrors()

    override suspend fun discardPlan(planId: String): Result<Unit> = auth.ownRow {
        postgrest.rpc("discard_coach_plan", DiscardPlanParams(planId))
        Unit
    }.mapPlanErrors()

    override suspend fun pendingCalorieChange(today: LocalDate): Result<CalorieChange?> = auth.ownRow {
        postgrest.rpc("evaluate_coach_calorie_change", EvaluateCalorieParams(today))
        postgrest.from("coach_recommendations")
            .select {
                filter {
                    eq("kind", "calorie_change")
                    eq("status", "pending")
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<CoachRecommendationDto>()
            .firstOrNull()?.toCalorieChange()
    }

    override suspend fun decideCalorieChange(id: String, accept: Boolean, today: LocalDate): Result<Unit> =
        auth.ownRow {
            postgrest.rpc(
                "decide_recommendation",
                DecideRecommendationParams(id, if (accept) "accepted" else "discarded", today),
            )
            Unit
        }.mapPlanErrors()

    /** The stored plan the finished stream announced; an unreadable event or a missing row is [CoachFailure.Other]. */
    private suspend fun storedPlanOf(eventText: String): Result<CoachPlan> {
        val id = runCatching { coachPlanJson.decodeFromString<PlanEventDto>(eventText) }.getOrNull()?.planId
        val plan = id?.let { loadPlan(it) }
        return if (plan != null) Result.success(plan) else Result.failure(CoachException(CoachFailure.Other))
    }

    private suspend fun loadPlan(id: String): CoachPlan? = postgrest.from("coach_plans")
        .select { filter { eq("id", id) } }
        .decodeList<CoachPlanDto>()
        .firstOrNull()?.toDomain()

    /** SQLSTATE 55000 is "derived values unconfirmed" while accepting, or a plan no longer in the expected state. */
    private fun <T> Result<T>.mapPlanErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        if (cause.code != WRONG_OBJECT_STATE) throw failure
        val text = listOfNotNull(cause.error, cause.description, cause.message).joinToString(" ")
        throw CoachException(
            if (text.contains("unconfirmed")) CoachFailure.NeedsConfirmation else CoachFailure.Conflict,
        )
    }
}
