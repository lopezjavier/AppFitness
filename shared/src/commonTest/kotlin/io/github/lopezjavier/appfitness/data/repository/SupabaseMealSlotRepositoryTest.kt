package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
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

private const val SLOTS_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseMealSlotRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun signedInRepository(response: () -> String): SupabaseMealSlotRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(SLOTS_SESSION, HttpStatusCode.OK, headers)
                } else {
                    respond(response(), HttpStatusCode.OK, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseMealSlotRepository(client.auth, client.postgrest)
    }

    @Test
    fun listEnabled_filtersEnabledAndOrdersByPosition() = runTest {
        val repo = signedInRepository { "[]" }

        repo.listEnabled().getOrThrow()

        val url = requests.last().url.toString()
        assertTrue(url.contains("enabled=eq.true"), url)
        assertTrue(url.contains("order=position.asc"), url)
        assertEquals("id,code,label,position,enabled", requests.last().url.parameters["select"])
    }

    @Test
    fun listEnabled_mapsDefaultAndCustomSlots() = runTest {
        val repo =
            signedInRepository {
                """[{"id":"s1","code":"breakfast","label":"Desayuno","position":0,"enabled":true},""" +
                    """{"id":"s2","code":null,"label":"Pre-entreno","position":4,"enabled":true}]"""
            }

        assertEquals(
            listOf(
                MealSlot("s1", MealCode.Breakfast, "Desayuno", 0, true),
                MealSlot("s2", null, "Pre-entreno", 4, true),
            ),
            repo.listEnabled().getOrThrow(),
        )
    }

    @Test
    fun networkErrors_areMapped() = runTest {
        var fail = false
        val repo =
            signedInRepository {
                if (fail) throw IOException("offline")
                "[]"
            }
        fail = true

        assertEquals(AppError.Network, repo.listEnabled().appErrorOrNull())
    }

    @Test
    fun signedOut_isUnauthorized() = runTest {
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, MockEngine { respond("[]") }, MemorySessionManager())
        val repo = SupabaseMealSlotRepository(client.auth, client.postgrest)

        assertEquals(AppError.Unauthorized, repo.listEnabled().appErrorOrNull())
    }
}
