package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
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

private const val FOOD_PREFS_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseFoodPreferencesRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var body = "[]"
    private var failing = false

    private suspend fun repository(signIn: Boolean = true): SupabaseFoodPreferencesRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath.contains(
                        "/auth/",
                    ) -> respond(FOOD_PREFS_SESSION, HttpStatusCode.OK, headers)

                    failing -> throw IOException("offline")

                    else -> respond(body, HttpStatusCode.OK, headers)
                }
            }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseFoodPreferencesRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    @Test
    fun get_readsTheOwnRow() = runTest {
        body = """[{"olive_oil_griddle_g":10.0,"olive_oil_air_fryer_g":0,"olive_oil_fried_g":500.0}]"""

        val result = repository().get().getOrThrow()

        assertEquals(FoodPreferences(10.0, 0.0, 500.0), result)
        val query = rest().single().url.toString()
        assertTrue(query.contains("/rest/v1/food_preferences"), query)
        assertTrue(query.contains("user_id=eq.user-1"), query)
    }

    @Test
    fun get_aMissingRowYieldsTheDefaults() = runTest {
        val result = repository().get().getOrThrow()

        assertEquals(FoodPreferences(5.0, 5.0, 50.0), result)
    }

    @Test
    fun get_offline_isNetworkError() = runTest {
        val repo = repository()
        failing = true

        assertEquals(AppError.Network, repo.get().appErrorOrNull())
    }

    @Test
    fun get_signedOut_isUnauthorized() = runTest {
        assertEquals(AppError.Unauthorized, repository(signIn = false).get().appErrorOrNull())
    }
}
