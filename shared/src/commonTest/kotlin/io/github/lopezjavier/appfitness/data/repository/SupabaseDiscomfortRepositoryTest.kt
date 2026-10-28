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
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val ROW =
    """{"id":"d1","exercise_id":"e1","logged_at":"2026-11-01T10:00:00+00:00","note":"dolor fuerte",""" +
        """"severe":true,"resolved_at":null}"""

class SupabaseDiscomfortRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var readBody = "[]"
    private var writeStatus = HttpStatusCode.Created
    private var writeBody = "[$ROW]"

    private suspend fun signedIn() = MockEngine { request ->
        requests += request
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        when {
            request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
            request.method == HttpMethod.Get -> respond(readBody, HttpStatusCode.OK, headers)
            else -> respond(writeBody, writeStatus, headers)
        }
    }.let { engine ->
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        SupabaseDiscomfortRepository(client.auth, client.postgrest)
    }

    @Test
    fun log_sendsExerciseNoteAndSevereAndMapsTheStoredRow() = runTest {
        val repo = signedIn()

        val stored = repo.log("e1", "dolor fuerte", true).getOrThrow()

        assertEquals("d1", stored.id)
        assertEquals(Instant.parse("2026-11-01T10:00:00Z"), stored.loggedAt)
        assertTrue(stored.severe)
        assertNull(stored.resolvedAt)
        val write = requests.last { it.method == HttpMethod.Post }
        val body = (write.body as TextContent).text
        assertTrue(""""exercise_id":"e1"""" in body, body)
        assertTrue(""""note":"dolor fuerte"""" in body, body)
        assertTrue(""""severe":true""" in body, body)
        assertTrue("user_id" !in body, body)
    }

    @Test
    fun unresolved_filtersByExercisesAndUnresolvedNewestFirst() = runTest {
        val repo = signedIn()
        readBody = "[$ROW]"

        val rows = repo.unresolved(setOf("e1", "e2")).getOrThrow()

        assertEquals(listOf("d1"), rows.map { it.id })
        val url = requests.last().url.toString()
        assertTrue("exercise_id=in." in url, url)
        assertTrue("resolved_at=is.null" in url, url)
        assertTrue("order=logged_at.desc" in url, url)
    }

    @Test
    fun unresolved_withoutExercisesSendsNoRequest() = runTest {
        val repo = signedIn()
        val before = requests.size

        assertTrue(repo.unresolved(emptySet()).getOrThrow().isEmpty())
        assertEquals(before, requests.size)
    }

    @Test
    fun resolveOpen_patchesOnlyTheUnresolvedRowsOfTheExercise() = runTest {
        val repo = signedIn()
        writeStatus = HttpStatusCode.NoContent
        writeBody = ""

        repo.resolveOpen("e1", Instant.parse("2026-11-02T08:00:00Z")).getOrThrow()

        val patch = requests.last { it.method == HttpMethod.Patch }
        val url = patch.url.toString()
        assertTrue("exercise_id=eq.e1" in url, url)
        assertTrue("resolved_at=is.null" in url, url)
        val body = (patch.body as TextContent).text
        assertTrue(""""resolved_at":"2026-11-02T08:00:00Z"""" in body, body)
    }

    @Test
    fun failuresAreMappedToAppErrors() = runTest {
        val repo = signedIn()
        writeStatus = HttpStatusCode.InternalServerError
        writeBody = """{"code":"XX000","message":"boom","details":null,"hint":null}"""

        assertEquals(AppError.Unknown, repo.log("e1", null, false).appErrorOrNull())
    }
}
