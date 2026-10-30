package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val PING_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseCoachPingRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun repository(onFunction: () -> Pair<HttpStatusCode, String>): SupabaseCoachPingRepository {
        val engine =
            MockEngine { request ->
                requests += request
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(
                        PING_SESSION,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                } else {
                    val (status, body) = onFunction()
                    val type = if (status == HttpStatusCode.OK) "text/event-stream" else "application/json"
                    respond(body, status, headersOf(HttpHeaders.ContentType, type))
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseCoachPingRepository(client.auth, client.functions)
    }

    private fun failureOf(result: Result<Unit>): CoachFailure {
        val exception = result.exceptionOrNull()
        assertIs<CoachException>(exception)
        return exception.failure
    }

    @Test
    fun doneFrame_isSuccess_andPostsKindPingWithTheUserJwt() = runTest {
        val repo = repository {
            HttpStatusCode.OK to "event: delta\ndata: {\"text\":\"pong\"}\n\nevent: done\ndata: {\"usage\":{}}\n\n"
        }

        assertTrue(repo.ping().isSuccess)

        val request = requests.last()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/functions/v1/coach"), request.url.toString())
        assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
        assertTrue((request.body as TextContent).text.contains("\"kind\":\"ping\""))
    }

    @Test
    fun streamWithoutDoneFrame_isOtherFailure() = runTest {
        val repo = repository { HttpStatusCode.OK to "event: delta\ndata: {\"text\":\"po\"}\n\n" }

        assertEquals(CoachFailure.Other, failureOf(repo.ping()))
    }

    @Test
    fun errorFrame_isMapped() = runTest {
        val repo = repository {
            HttpStatusCode.OK to "event: error\ndata: {\"code\":\"coach_timeout\"}\n\n"
        }

        assertEquals(CoachFailure.Timeout, failureOf(repo.ping()))
    }

    @Test
    fun limitReachedBody_isMappedWithScope() = runTest {
        val repo = repository {
            HttpStatusCode.TooManyRequests to """{"error":{"code":"coach_limit_reached","scope":"global"}}"""
        }

        assertEquals(CoachFailure.LimitReached(CoachFailure.Scope.Global), failureOf(repo.ping()))
    }

    @Test
    fun noCreditBody_isMapped() = runTest {
        val repo = repository {
            HttpStatusCode.PaymentRequired to """{"error":{"code":"coach_no_credit"}}"""
        }

        assertEquals(CoachFailure.NoCredit, failureOf(repo.ping()))
    }

    @Test
    fun serverErrorWithUnknownBody_isOtherFailure() = runTest {
        val repo = repository { HttpStatusCode.InternalServerError to """{"error":{"code":"INTERNAL"}}""" }

        assertEquals(CoachFailure.Other, failureOf(repo.ping()))
    }

    @Test
    fun ioError_isNetworkAppError() = runTest {
        val repo = repository { throw IOException("offline") }

        assertEquals(AppError.Network, repo.ping().appErrorOrNull())
    }
}
