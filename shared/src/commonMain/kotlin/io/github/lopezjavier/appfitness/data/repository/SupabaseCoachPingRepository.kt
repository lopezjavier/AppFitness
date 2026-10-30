package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.lopezjavier.appfitness.data.remote.CoachErrorParser
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.repository.CoachPingRepository
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * Calls the `coach` function with `{"kind":"ping"}` and reads the whole SSE body as text: a `done` frame is success, an
 * `error` frame or a JSON error body is mapped through [CoachErrorParser].
 */
class SupabaseCoachPingRepository(private val auth: Auth, private val functions: Functions) : CoachPingRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun ping(): Result<Unit> {
        if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
        return try {
            val body = functions.invoke("coach") {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody("""{"kind":"ping"}""")
            }.bodyAsText()
            parseStream(body)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: RestException) {
            Result.failure(CoachException(CoachErrorParser.parse(failure.description ?: failure.error)))
        } catch (failure: Exception) {
            Result.failure(AppException(failure.toAppError(), failure))
        }
    }

    private fun parseStream(text: String): Result<Unit> {
        val frames = text.split("\n\n").map { it.lines() }
        val error = frames.firstOrNull { it.contains("event: error") }
        if (error != null) {
            val data = error.firstOrNull { it.startsWith("data:") }?.removePrefix("data:")?.trim().orEmpty()
            return Result.failure(CoachException(CoachErrorParser.parse(data)))
        }
        return if (frames.any { it.contains("event: done") }) {
            Result.success(Unit)
        } else {
            Result.failure(CoachException(CoachFailure.Other))
        }
    }
}
