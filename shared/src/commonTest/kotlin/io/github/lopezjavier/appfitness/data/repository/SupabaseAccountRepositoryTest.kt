package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseAccountRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun repository(onFunction: () -> Pair<HttpStatusCode, String>): SupabaseAccountRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(SESSION, HttpStatusCode.OK, headers)
                } else {
                    val (status, body) = onFunction()
                    respond(body, status, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseAccountRepository(client.functions)
    }

    @Test
    fun status200_isDeleted_andPostsToTheFunctionWithTheUserJwt() = runTest {
        val repo = repository { HttpStatusCode.OK to """{"status":"deleted"}""" }

        assertEquals(DeleteAccountOutcome.Deleted, repo.deleteAccount())

        val request = requests.last()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/functions/v1/delete-account"), request.url.toString())
        assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun status401_isDeleted() = runTest {
        val repo = repository { HttpStatusCode.Unauthorized to """{"error":{"code":"unauthorized"}}""" }

        assertEquals(DeleteAccountOutcome.Deleted, repo.deleteAccount())
    }

    @Test
    fun status500_isFailed() = runTest {
        val repo = repository { HttpStatusCode.InternalServerError to """{"error":{"code":"delete_failed"}}""" }

        assertEquals(DeleteAccountOutcome.Failed, repo.deleteAccount())
    }

    @Test
    fun ioError_isNoNetwork() = runTest {
        val repo = repository { throw IOException("offline") }

        assertEquals(DeleteAccountOutcome.NoNetwork, repo.deleteAccount())
    }

    @Test
    fun status403_isProtected() = runTest {
        val repo = repository { HttpStatusCode.Forbidden to """{"error":{"code":"account_protected"}}""" }

        assertEquals(DeleteAccountOutcome.Protected, repo.deleteAccount())
    }

    @Test
    fun isProtected_true_getsAccountStatusWithTheUserJwt() = runTest {
        val repo = repository { HttpStatusCode.OK to """{"protected":true}""" }

        assertEquals(true, repo.isProtected().getOrNull())

        val request = requests.last()
        assertEquals(HttpMethod.Get, request.method)
        assertTrue(request.url.encodedPath.endsWith("/functions/v1/account-status"), request.url.toString())
        assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun isProtected_false() = runTest {
        val repo = repository { HttpStatusCode.OK to """{"protected":false}""" }

        assertEquals(false, repo.isProtected().getOrNull())
    }

    @Test
    fun isProtected_serverError_isFailure() = runTest {
        val repo = repository { HttpStatusCode.InternalServerError to """{"error":{"code":"x"}}""" }

        assertTrue(repo.isProtected().isFailure)
    }

    @Test
    fun isProtected_networkError_isFailure() = runTest {
        val repo = repository { throw IOException("offline") }

        assertTrue(repo.isProtected().isFailure)
    }

    @Test
    fun isProtected_malformedBody_isFailure() = runTest {
        val repo = repository { HttpStatusCode.OK to """not json""" }

        assertTrue(repo.isProtected().isFailure)
    }
}
