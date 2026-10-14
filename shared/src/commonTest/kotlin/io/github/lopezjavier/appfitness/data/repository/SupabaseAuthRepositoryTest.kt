package io.github.lopezjavier.appfitness.data.repository

import app.cash.turbine.test
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val USER_ID = "11111111-2222-3333-4444-555555555555"
private const val SESSION_JSON =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"$USER_ID","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseAuthRepositoryTest {
    private val credentials = TestLoginCredentials("a@b.c", "pw")
    private val requests = mutableListOf<HttpRequestData>()

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun repository(
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<SupabaseAuthRepository, SupabaseClient> {
        val engine =
            MockEngine { request ->
                requests += request
                handler(request)
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        return SupabaseAuthRepository(client.auth) to client
    }

    @Test
    fun passwordSignIn_usesPasswordGrantAndSucceeds() = runTest {
        val (repo, client) = repository { json(HttpStatusCode.OK, SESSION_JSON) }

        val outcome = repo.signInWithPassword(credentials)

        assertEquals(SignInOutcome.Success, outcome)
        assertTrue(requests.last().url.toString().contains("grant_type=password"))
        client.close()
    }

    @Test
    fun googleSignIn_usesIdTokenGrant() = runTest {
        val (repo, client) = repository { json(HttpStatusCode.OK, SESSION_JSON) }

        val outcome = repo.signInWithGoogle("id-token", "raw-nonce")

        assertEquals(SignInOutcome.Success, outcome)
        assertTrue(requests.last().url.toString().contains("grant_type=id_token"))
        client.close()
    }

    @Test
    fun ioException_mapsToNoNetwork() = runTest {
        val (repo, client) = repository { throw IOException("offline") }

        assertEquals(SignInOutcome.NoNetwork, repo.signInWithPassword(credentials))
        client.close()
    }

    @Test
    fun badRequest_mapsToFailed() = runTest {
        val (repo, client) =
            repository { json(HttpStatusCode.BadRequest, """{"error":"invalid_grant","error_description":"bad"}""") }

        assertEquals(SignInOutcome.Failed, repo.signInWithPassword(credentials))
        client.close()
    }

    @Test
    fun signOut_withFailingLogout_stillLeavesSignedOut() = runTest {
        val (repo, client) =
            repository { request ->
                if (request.url.encodedPath.endsWith("/logout")) throw IOException("offline")
                json(HttpStatusCode.OK, SESSION_JSON)
            }
        repo.signInWithPassword(credentials)

        repo.signOut()

        assertEquals(null, client.auth.currentSessionOrNull())
        repo.authState.test {
            assertEquals(AuthState.SignedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        client.close()
    }

    @Test
    fun signOut_usesLocalScope() = runTest {
        val (repo, client) =
            repository { request ->
                if (request.url.encodedPath.endsWith(
                        "/logout",
                    )
                ) {
                    json(HttpStatusCode.NoContent, "")
                } else {
                    json(HttpStatusCode.OK, SESSION_JSON)
                }
            }
        repo.signInWithPassword(credentials)

        repo.signOut()

        assertTrue(requests.any { it.url.toString().contains("scope=local") })
        client.close()
    }

    @Test
    fun signedIn_authStateCarriesTheUserId() = runTest {
        val (repo, client) = repository { json(HttpStatusCode.OK, SESSION_JSON) }
        repo.signInWithPassword(credentials)

        repo.authState.test {
            assertEquals(AuthState.SignedIn(USER_ID), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        client.close()
    }
}
