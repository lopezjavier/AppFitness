package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.CoachErrorParser
import io.github.lopezjavier.appfitness.data.remote.CoachStreamParser
import io.github.lopezjavier.appfitness.data.remote.GoalSwitchRequestBody
import io.github.lopezjavier.appfitness.data.remote.GoalSwitchRequestInput
import io.github.lopezjavier.appfitness.data.remote.KeepPhaseParams
import io.github.lopezjavier.appfitness.data.remote.MarkBlockReviewSeenParams
import io.github.lopezjavier.appfitness.data.remote.OpenBlockNewParams
import io.github.lopezjavier.appfitness.data.remote.OpenBlockReviewParams
import io.github.lopezjavier.appfitness.data.remote.PhaseChangeRequestBody
import io.github.lopezjavier.appfitness.data.remote.PhaseChangeRequestInput
import io.github.lopezjavier.appfitness.data.remote.PlanEventDto
import io.github.lopezjavier.appfitness.data.remote.WeeksInPhaseParams
import io.github.lopezjavier.appfitness.data.remote.blockReviewOf
import io.github.lopezjavier.appfitness.data.remote.coachPlanJson
import io.github.lopezjavier.appfitness.data.remote.goalSwitchPreviewOf
import io.github.lopezjavier.appfitness.data.remote.newBlockEntryOf
import io.github.lopezjavier.appfitness.data.remote.phaseWeeksOf
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.GoalSwitchPreview
import io.github.lopezjavier.appfitness.domain.model.NewBlockEntry
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
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

    override suspend fun markSeen(reviewId: String): Result<Unit> = auth.ownRow {
        postgrest.rpc("mark_coach_block_review_seen", MarkBlockReviewSeenParams(reviewId))
        Unit
    }

    override suspend fun acceptPhase(reviewId: String, today: LocalDate): Result<String> = callCoach(
        coachPlanJson.encodeToString(
            PhaseChangeRequestBody("plan_phase_change", PhaseChangeRequestInput(today, reviewId)),
        ),
    ) { text -> planIdOf(text) }

    override suspend fun openNewBlock(today: LocalDate): Result<NewBlockEntry?> = auth.ownRow {
        newBlockEntryOf(postgrest.rpc("open_coach_block_new", OpenBlockNewParams(today)).decodeAs<JsonElement>())
    }

    override suspend fun previewGoalSwitch(today: LocalDate): Result<GoalSwitchPreview> = callCoach(
        coachPlanJson.encodeToString(GoalSwitchRequestBody("plan_goal_switch", GoalSwitchRequestInput(today))),
    ) { text ->
        val preview = runCatching { coachPlanJson.parseToJsonElement(text) }.getOrNull()?.let(::goalSwitchPreviewOf)
        if (preview != null) Result.success(preview) else Result.failure(CoachException(CoachFailure.Other))
    }

    override suspend fun weeksInPhase(today: LocalDate): Result<PhaseWeeks?> = auth.ownRow {
        phaseWeeksOf(postgrest.rpc("coach_weeks_in_phase", WeeksInPhaseParams(today)).decodeAs<JsonElement>())
    }

    /** Posts [body] to the `coach` function and reads the single plan event with [parse]. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> callCoach(body: String, parse: (String) -> Result<T>): Result<T> {
        if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
        return try {
            val stream = functions.invoke("coach") {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(body)
                coachTimeout(requestTimeout)
            }.bodyAsText()
            CoachStreamParser.parse(stream).fold(
                onSuccess = parse,
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
