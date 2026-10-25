package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.lopezjavier.appfitness.data.remote.FoodPhotoRequestDto
import io.github.lopezjavier.appfitness.data.remote.FoodPhotoResponseDto
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoDraft
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.repository.FoodPhotoRepository
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

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
class SupabaseFoodPhotoRepository(private val auth: Auth, private val functions: Functions) : FoodPhotoRepository {
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
            val text = try {
                functions.invoke("food-photo") {
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.bodyAsText()
            } catch (failure: RestException) {
                throw AppException(failure.statusCode.toPhotoError(), failure)
            }
            try {
                json.decodeFromString<FoodPhotoResponseDto>(text).draft.toDomain()
            } catch (failure: SerializationException) {
                throw AppException(AppError.Unknown, failure)
            }
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
