package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.CoachErrorParser
import io.github.lopezjavier.appfitness.data.remote.CoachIntakeDto
import io.github.lopezjavier.appfitness.data.remote.CoachStreamParser
import io.github.lopezjavier.appfitness.data.remote.ExcludeLineParams
import io.github.lopezjavier.appfitness.data.remote.ExtractRequestBody
import io.github.lopezjavier.appfitness.data.remote.ExtractResultDto
import io.github.lopezjavier.appfitness.data.remote.FreeTextLineDto
import io.github.lopezjavier.appfitness.data.remote.FreeTextLinesParams
import io.github.lopezjavier.appfitness.data.remote.SaveIntakeParams
import io.github.lopezjavier.appfitness.data.remote.coachPlanJson
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.CoachIntakeDraft
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextLine
import io.github.lopezjavier.appfitness.domain.model.requestKey
import io.github.lopezjavier.appfitness.domain.port.CoachModelPreference
import io.github.lopezjavier.appfitness.domain.repository.CoachIntakeRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/**
 * The intake row (owner-select RLS), its RPCs and the `intake_extract` kind of the `coach` function (whole SSE body
 * read as text, like [SupabaseCoachPlanRepository.requestPlan]).
 */
class SupabaseCoachIntakeRepository internal constructor(
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val functions: Functions,
    private val modelPreference: CoachModelPreference,
    private val requestTimeout: Duration,
) : CoachIntakeRepository {
    constructor(auth: Auth, postgrest: Postgrest, functions: Functions, modelPreference: CoachModelPreference) :
        this(auth, postgrest, functions, modelPreference, COACH_CLIENT_TIMEOUT)

    override suspend fun get(): Result<CoachIntake?> = auth.ownRow {
        postgrest.from("coach_user_intake").select().decodeList<CoachIntakeDto>().firstOrNull()?.toDomain()
    }

    override suspend fun save(intake: CoachIntake): Result<CoachIntake> = auth.ownRow {
        postgrest.rpc("save_coach_intake", SaveIntakeParams.of(intake)).decodeAs<CoachIntakeDto>().toDomain()
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun extract(freeText: String, fields: CoachIntakeDraft?): Result<CoachIntakeDraft> {
        if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
        return try {
            val model = modelPreference.selected.value.requestKey()
            val body = coachPlanJson.encodeToString(ExtractRequestBody.of(freeText, fields, model))
            val stream = functions.invoke("coach") {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(body)
                coachTimeout(requestTimeout)
            }.bodyAsText()
            CoachStreamParser.parse(stream).fold(
                onSuccess = { text -> draftOf(text) },
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

    override suspend fun freeTextLines(today: LocalDate): Result<List<FreeTextLine>> = auth.ownRow {
        postgrest.rpc("coach_free_text_lines", FreeTextLinesParams(today)).decodeList<FreeTextLineDto>()
            .map { it.toDomain() }
    }

    override suspend fun setLineExcluded(kind: FreeTextKind, sourceId: String, excluded: Boolean): Result<Unit> =
        auth.ownRow {
            postgrest.rpc("set_free_text_line_excluded", ExcludeLineParams.of(kind, sourceId, excluded))
            Unit
        }

    override suspend fun acknowledgeMaintenanceHandover(): Result<Unit> = auth.ownRow {
        postgrest.rpc("ack_maintenance_handover")
        Unit
    }

    /** An answer that is not the expected `{fields}` object is [CoachFailure.Other]. */
    private fun draftOf(text: String): Result<CoachIntakeDraft> =
        runCatching { coachPlanJson.decodeFromString<ExtractResultDto>(text).toDomain() }
            .fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(CoachException(CoachFailure.Other)) },
            )
}
