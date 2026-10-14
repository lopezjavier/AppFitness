package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
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

class SupabaseProfileRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun signedInRepository(profileResponse: () -> String): SupabaseProfileRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(SESSION, HttpStatusCode.OK, headers)
                } else {
                    respond(profileResponse(), HttpStatusCode.OK, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseProfileRepository(client.auth, client.postgrest)
    }

    @Test
    fun decodesTheProfileRowAndFiltersByTheCurrentUser() = runTest {
        val repo =
            signedInRepository {
                """[{"display_name":"Ana","avatar_url":"https://img/ana.png","onboarding_completed":true}]"""
            }

        val profile = repo.getOwnProfile().getOrThrow()

        assertEquals(ProfileSummary("Ana", "https://img/ana.png", true), profile)
        val url = requests.last().url.toString()
        assertTrue(url.contains("id=eq.user-1"), url)
        assertTrue(url.contains("select=display_name"), url)
    }

    @Test
    fun nullAvatar_isKeptNull() = runTest {
        val repo = signedInRepository { """[{"display_name":"Ana","avatar_url":null,"onboarding_completed":false}]""" }

        assertEquals(ProfileSummary("Ana", null, false), repo.getOwnProfile().getOrThrow())
    }

    @Test
    fun networkError_isAppErrorNetwork() = runTest {
        var fail = false
        val repo =
            signedInRepository {
                if (fail) throw IOException("offline")
                "[]"
            }
        fail = true

        assertEquals(AppError.Network, repo.getOwnProfile().appErrorOrNull())
    }
}
