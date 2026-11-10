package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ExerciseChatContext
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val CHAT_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private fun errorBody(code: String) = """{"error":{"code":"$code","message":"m"}}"""

class SupabaseExerciseChatRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()

    private val context = ExerciseChatContext("Press banca", listOf("Pecho", "Tríceps"), "Baja la barra.\nEmpuja.")

    private suspend fun signedIn(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = """{"answer":"Hola"}""",
        fail: Boolean = false,
        failWith: Throwable? = null,
    ): SupabaseExerciseChatRepository {
        val engine =
            MockEngine { request ->
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(CHAT_SESSION, HttpStatusCode.OK, headers)
                } else {
                    requests += request
                    bodies += request.body.toByteArray().decodeToString()
                    failWith?.let { throw it }
                    if (fail) throw IOException("offline")
                    respond(body, status, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseExerciseChatRepository(client.auth, client.functions)
    }

    private suspend fun errorOf(status: HttpStatusCode, body: String) =
        signedIn(status = status, body = body).ask(context, "¿Cómo?").appErrorOrNull()

    @Test
    fun ask_postsExactlyTheExerciseAndTheQuestionAndReturnsTheAnswer() = runTest {
        val answer = signedIn().ask(context, "¿Cómo?").getOrThrow()

        assertEquals("Hola", answer)
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/functions/v1/exercise-chat"), request.url.toString())
        assertEquals(
            """{"exercise":{"name":"Press banca","muscles":["Pecho","Tríceps"],""" +
                """"instructions":"Baja la barra.\nEmpuja."},"question":"¿Cómo?"}""",
            bodies.single(),
        )
    }

    @Test
    fun ask_sendsAnEmptyInstructionsStringForACustomExercise() = runTest {
        signedIn().ask(context.copy(instructions = ""), "q").getOrThrow()

        assertTrue(bodies.single().contains(""""instructions":"""""), bodies.single())
    }

    @Test
    fun ask_carriesTheSessionAuthorization() = runTest {
        signedIn().ask(context, "q").getOrThrow()

        assertNotNull(requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun ask_mapsQuotaExceededCodeAndStatus() = runTest {
        assertEquals(AppError.QuotaExceeded, errorOf(HttpStatusCode.TooManyRequests, errorBody("QUOTA_EXCEEDED")))
        assertEquals(AppError.QuotaExceeded, errorOf(HttpStatusCode.TooManyRequests, "not json"))
    }

    @Test
    fun ask_mapsProviderTimeoutToServiceUnavailable() = runTest {
        val result = errorOf(HttpStatusCode.GatewayTimeout, errorBody("PROVIDER_TIMEOUT"))

        assertEquals(AppError.ServiceUnavailable, result)
        assertEquals(AppError.ServiceUnavailable, errorOf(HttpStatusCode.GatewayTimeout, "not json"))
    }

    @Test
    fun ask_mapsProviderErrorNotConfiguredAndInternalToServiceUnavailable() = runTest {
        assertEquals(AppError.ServiceUnavailable, errorOf(HttpStatusCode.BadGateway, errorBody("PROVIDER_ERROR")))
        assertEquals(
            AppError.ServiceUnavailable,
            errorOf(HttpStatusCode.ServiceUnavailable, errorBody("NOT_CONFIGURED")),
        )
        assertEquals(AppError.ServiceUnavailable, errorOf(HttpStatusCode.InternalServerError, errorBody("INTERNAL")))
    }

    @Test
    fun ask_mapsInvalidInputAndPayloadTooLargeToValidation() = runTest {
        assertEquals(AppError.Validation, errorOf(HttpStatusCode.BadRequest, errorBody("INVALID_INPUT")))
        assertEquals(AppError.Validation, errorOf(HttpStatusCode.PayloadTooLarge, errorBody("PAYLOAD_TOO_LARGE")))
    }

    @Test
    fun ask_mapsUnauthorizedToUnauthorized() = runTest {
        assertEquals(AppError.Unauthorized, errorOf(HttpStatusCode.Unauthorized, errorBody("UNAUTHORIZED")))
    }

    @Test
    fun ask_mapsAnUnknownCodeWithAnUnmappedStatusToUnknown() = runTest {
        assertEquals(AppError.Unknown, errorOf(HttpStatusCode.Forbidden, errorBody("WHATEVER")))
    }

    @Test
    fun ask_fallsBackToTheStatusWhenTheErrorBodyCannotBeParsed() = runTest {
        assertEquals(AppError.ServiceUnavailable, errorOf(HttpStatusCode.BadGateway, "<html>"))
        assertEquals(AppError.Validation, errorOf(HttpStatusCode.BadRequest, "{}"))
    }

    @Test
    fun ask_mapsATransportFailureToNetwork() = runTest {
        assertEquals(AppError.Network, signedIn(fail = true).ask(context, "q").appErrorOrNull())
    }

    @Test
    fun ask_mapsARequestTimeoutToServiceUnavailable() = runTest {
        val timeout = HttpRequestTimeoutException("http://localhost:54321/functions/v1/exercise-chat", 30_000L)

        assertEquals(AppError.ServiceUnavailable, signedIn(failWith = timeout).ask(context, "q").appErrorOrNull())
    }

    @Test
    fun ask_mapsAMalformedSuccessBodyToUnknown() = runTest {
        assertEquals(AppError.Unknown, signedIn(body = """{"unexpected":true}""").ask(context, "q").appErrorOrNull())
    }
}
