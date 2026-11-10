package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.lopezjavier.appfitness.data.remote.ExerciseChatRequestDto
import io.github.lopezjavier.appfitness.data.remote.ExerciseChatResponseDto
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ExerciseChatContext
import io.github.lopezjavier.appfitness.domain.repository.ExerciseChatRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Client timeout; above the Gemini timeout of the `exercise-chat` function. */
internal val EXERCISE_CHAT_TIMEOUT: Duration = 30.seconds

private const val CHAT_CONNECT_TIMEOUT_MS = 15_000L
private const val CHAT_SOCKET_MARGIN_MS = 5_000L
private const val CHAT_BAD_REQUEST = 400
private const val CHAT_UNAUTHORIZED = 401
private const val CHAT_PAYLOAD_TOO_LARGE = 413
private const val CHAT_TOO_MANY_REQUESTS = 429
private const val CHAT_SERVER_ERROR = 500
private const val CHAT_BAD_GATEWAY = 502
private const val CHAT_SERVICE_UNAVAILABLE = 503
private const val CHAT_GATEWAY_TIMEOUT = 504

/** Questions about an exercise over the `exercise-chat` Edge Function. Nothing is logged, cached or stored. */
class SupabaseExerciseChatRepository internal constructor(
    private val auth: Auth,
    private val functions: Functions,
    private val requestTimeout: Duration,
) : ExerciseChatRepository {
    constructor(auth: Auth, functions: Functions) : this(auth, functions, EXERCISE_CHAT_TIMEOUT)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun ask(exercise: ExerciseChatContext, question: String): Result<String> = auth.ownRow {
        val body = json.encodeToString(
            ExerciseChatRequestDto.serializer(),
            ExerciseChatRequestDto.from(exercise, question),
        )
        try {
            json.decodeFromString<ExerciseChatResponseDto>(invoke(body)).answer
        } catch (failure: SerializationException) {
            throw AppException(AppError.Unknown, failure)
        }
    }

    private suspend fun invoke(body: String): String = try {
        functions.invoke("exercise-chat") {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(body)
            // The socket timeout stays above the request timeout so a slow answer is a HttpRequestTimeoutException
            // ("retry") and not a cause-less transport failure ("offline").
            timeout {
                requestTimeoutMillis = requestTimeout.inWholeMilliseconds
                socketTimeoutMillis = requestTimeout.inWholeMilliseconds + CHAT_SOCKET_MARGIN_MS
                connectTimeoutMillis = CHAT_CONNECT_TIMEOUT_MS
            }
        }.bodyAsText()
    } catch (failure: RestException) {
        throw AppException(chatError(failure.statusCode, failure.description ?: failure.error), failure)
    } catch (failure: HttpRequestTimeoutException) {
        throw AppException(AppError.ServiceUnavailable, failure)
    }
}

/** The stable server `code` wins; the status is the fallback when the body cannot be parsed or has no known code. */
private fun chatError(status: Int, body: String): AppError = when (errorCode(body)) {
    "QUOTA_EXCEEDED" -> AppError.QuotaExceeded
    "PROVIDER_ERROR", "NOT_CONFIGURED", "INTERNAL", "PROVIDER_TIMEOUT" -> AppError.ServiceUnavailable
    "INVALID_INPUT", "PAYLOAD_TOO_LARGE" -> AppError.Validation
    "UNAUTHORIZED" -> AppError.Unauthorized
    else -> statusError(status)
}

private fun statusError(status: Int): AppError = when (status) {
    CHAT_BAD_REQUEST, CHAT_PAYLOAD_TOO_LARGE -> AppError.Validation
    CHAT_UNAUTHORIZED -> AppError.Unauthorized
    CHAT_TOO_MANY_REQUESTS -> AppError.QuotaExceeded
    CHAT_SERVER_ERROR, CHAT_BAD_GATEWAY, CHAT_SERVICE_UNAVAILABLE, CHAT_GATEWAY_TIMEOUT -> AppError.ServiceUnavailable
    else -> AppError.Unknown
}

private fun errorCode(body: String): String? {
    val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    val error = root["error"] as? JsonObject ?: root
    return (error["code"] as? JsonPrimitive)?.contentOrNull
}
