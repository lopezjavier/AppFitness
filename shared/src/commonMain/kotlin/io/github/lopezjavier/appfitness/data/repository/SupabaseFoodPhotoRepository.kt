package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.lopezjavier.appfitness.data.remote.AmountRequestDto
import io.github.lopezjavier.appfitness.data.remote.AmountResponseDto
import io.github.lopezjavier.appfitness.data.remote.FoodPhotoRequestDto
import io.github.lopezjavier.appfitness.data.remote.FoodPhotoResponseDto
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.AmountEstimate
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoDraft
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.repository.FoodPhotoRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Client timeout for Gemini-backed calls; above the 25 s Gemini timeout of the `food-photo` function. */
internal val FOOD_PHOTO_TIMEOUT: Duration = 30.seconds

private const val CONNECT_TIMEOUT_MS = 15_000L
private const val SOCKET_MARGIN_MS = 5_000L
private const val BAD_REQUEST = 400
private const val UNAUTHORIZED = 401
private const val PAYLOAD_TOO_LARGE = 413
private const val UNPROCESSABLE = 422
private const val TOO_MANY_REQUESTS = 429
private const val SERVER_ERROR = 500
private const val BAD_GATEWAY = 502
private const val SERVICE_UNAVAILABLE = 503

/**
 * Food estimates over the `food-photo` Edge Function. The image and the comment only travel in the request body: they
 * are never logged, cached or kept after the call.
 */
class SupabaseFoodPhotoRepository internal constructor(
    private val auth: Auth,
    private val functions: Functions,
    private val requestTimeout: Duration,
) : FoodPhotoRepository {
    constructor(auth: Auth, functions: Functions) : this(auth, functions, FOOD_PHOTO_TIMEOUT)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun estimate(mode: FoodPhotoMode, imageBase64: String, comment: String?): Result<FoodPhotoDraft> =
        auth.ownRow {
            val body = json.encodeToString(
                FoodPhotoRequestDto.serializer(),
                FoodPhotoRequestDto.from(mode, imageBase64, comment),
            )
            decode(invoke(body)) { json.decodeFromString<FoodPhotoResponseDto>(it).draft.toDomain() }
        }

    override suspend fun estimateAmount(food: Food, imageBase64: String): Result<AmountEstimate> = auth.ownRow {
        val body = json.encodeToString(AmountRequestDto.serializer(), AmountRequestDto.from(food, imageBase64))
        decode(invoke(body)) { json.decodeFromString<AmountResponseDto>(it).estimate.toDomain() }
    }

    private suspend fun invoke(body: String): String = try {
        functions.invoke("food-photo") {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            setBody(body)
            // The client default (10 s) is shorter than a Gemini vision answer.
            // OkHttp also enforces its own 10 s read timeout, so the socket timeout must be raised too. It stays above
            // the request timeout on purpose: supabase-kt wraps socket timeouts into a cause-less HttpRequestException
            // (shown as "offline"), while the request timeout stays a HttpRequestTimeoutException (shown as "retry").
            timeout {
                requestTimeoutMillis = requestTimeout.inWholeMilliseconds
                socketTimeoutMillis = requestTimeout.inWholeMilliseconds + SOCKET_MARGIN_MS
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
            }
        }.bodyAsText()
    } catch (failure: RestException) {
        throw AppException(failure.statusCode.toPhotoError(), failure)
    } catch (failure: HttpRequestTimeoutException) {
        // The estimate took too long: the service is reachable, so offer a retry instead of "offline".
        throw AppException(AppError.ServiceUnavailable, failure)
    }

    private fun <T> decode(text: String, parse: (String) -> T): T = try {
        parse(text)
    } catch (failure: SerializationException) {
        throw AppException(AppError.Unknown, failure)
    }
}

private fun Int.toPhotoError(): AppError = when (this) {
    BAD_REQUEST, PAYLOAD_TOO_LARGE -> AppError.Validation
    UNAUTHORIZED -> AppError.Unauthorized
    UNPROCESSABLE -> AppError.PhotoNotRecognized
    TOO_MANY_REQUESTS -> AppError.QuotaExceeded
    SERVER_ERROR, BAD_GATEWAY, SERVICE_UNAVAILABLE -> AppError.ServiceUnavailable
    else -> AppError.Unknown
}
