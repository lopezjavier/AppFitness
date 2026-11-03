package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
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

private const val FAVORITES_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseFavoriteExercisesRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()
    private var offline = false
    private var rows = "[]"

    private suspend fun signedIn(): SupabaseFavoriteExercisesRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(FAVORITES_SESSION, HttpStatusCode.OK, headers)
                } else {
                    bodies += request.body.toByteArray().decodeToString()
                    if (offline) throw IOException("offline")
                    respond(rows, HttpStatusCode.OK, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseFavoriteExercisesRepository(client.auth, client.postgrest)
    }

    @Test
    fun startsEmpty() = runTest {
        assertEquals(emptySet(), signedIn().favoriteIds.value)
    }

    @Test
    fun refresh_readsTheCallersRowsIntoTheSet() = runTest {
        val repo = signedIn()
        rows = """[{"exercise_id":"e1"},{"exercise_id":"e2"}]"""

        repo.refresh().getOrThrow()

        val request = requests.last()
        assertEquals(HttpMethod.Get, request.method)
        assertTrue(request.url.encodedPath.endsWith("/exercise_favorites"), request.url.toString())
        assertEquals("exercise_id", request.url.parameters["select"])
        assertEquals(setOf("e1", "e2"), repo.favoriteIds.value)
    }

    @Test
    fun refresh_whenOffline_keepsTheLastKnownSet() = runTest {
        val repo = signedIn()
        rows = """[{"exercise_id":"e1"}]"""
        repo.refresh().getOrThrow()
        offline = true

        val result = repo.refresh()

        assertEquals(AppError.Network, result.appErrorOrNull())
        assertEquals(setOf("e1"), repo.favoriteIds.value)
    }

    @Test
    fun setFavorite_true_upsertsIgnoringDuplicatesAndAddsTheId() = runTest {
        val repo = signedIn()

        repo.setFavorite("e1", true).getOrThrow()

        val request = requests.last()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/exercise_favorites"), request.url.toString())
        assertTrue(request.headers[HttpHeaders.Prefer].orEmpty().contains("ignore-duplicates"))
        assertTrue(bodies.last().contains("\"exercise_id\":\"e1\""), bodies.last())
        assertEquals(setOf("e1"), repo.favoriteIds.value)
    }

    @Test
    fun setFavorite_false_deletesTheRowAndRemovesTheId() = runTest {
        val repo = signedIn()
        repo.setFavorite("e1", true).getOrThrow()

        repo.setFavorite("e1", false).getOrThrow()

        val request = requests.last()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("eq.e1", request.url.parameters["exercise_id"])
        assertEquals(emptySet(), repo.favoriteIds.value)
    }

    @Test
    fun setFavorite_twice_isIdempotent() = runTest {
        val repo = signedIn()

        repo.setFavorite("e1", true).getOrThrow()
        repo.setFavorite("e1", true).getOrThrow()

        assertEquals(setOf("e1"), repo.favoriteIds.value)
    }

    @Test
    fun setFavorite_whenItFails_leavesTheSetUnchangedAndReturnsTheError() = runTest {
        val repo = signedIn()
        repo.setFavorite("e1", true).getOrThrow()
        offline = true

        val added = repo.setFavorite("e2", true)
        val removed = repo.setFavorite("e1", false)

        assertEquals(AppError.Network, added.appErrorOrNull())
        assertEquals(AppError.Network, removed.appErrorOrNull())
        assertEquals(setOf("e1"), repo.favoriteIds.value)
    }
}
