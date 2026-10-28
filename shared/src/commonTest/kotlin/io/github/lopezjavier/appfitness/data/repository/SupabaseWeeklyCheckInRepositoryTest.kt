package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CheckIn
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
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseWeeklyCheckInRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var writeStatus = HttpStatusCode.Created
    private var writeBody = ""

    private suspend fun signedIn(rows: String = "[]") = MockEngine { request ->
        requests += request
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        when {
            request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
            request.method == HttpMethod.Get -> respond(rows, HttpStatusCode.OK, headers)
            else -> respond(writeBody, writeStatus, headers)
        }
    }.let { engine ->
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        SupabaseWeeklyCheckInRepository(client.auth, client.postgrest)
    }

    private val week = LocalDate(2026, 11, 9)

    @Test
    fun get_mapsTheRowAndFiltersByWeek() = runTest {
        val repo = signedIn("""[{"week_start":"2026-11-09","energy":4,"notes":"slept badly"}]""")

        val checkIn = repo.get(week).getOrThrow()

        assertEquals(CheckIn(week, 4, "slept badly"), checkIn)
        val url = requests.last().url.toString()
        assertTrue(url.contains("week_start=eq.2026-11-09"), url)
    }

    @Test
    fun get_nullNotesBecomeEmpty() = runTest {
        val repo = signedIn("""[{"week_start":"2026-11-09","energy":2,"notes":null}]""")

        assertEquals("", repo.get(week).getOrThrow()?.notes)
    }

    @Test
    fun get_withoutRowIsNull() = runTest {
        assertNull(signedIn().get(week).getOrThrow())
    }

    @Test
    fun save_upsertsOnUserAndWeekWithTheAnswer() = runTest {
        val repo = signedIn()

        repo.save(CheckIn(week, 4, "good week")).getOrThrow()

        val write = requests.last { it.method == HttpMethod.Post }
        assertEquals("user_id,week_start", write.url.parameters["on_conflict"])
        val body = (write.body as TextContent).text
        assertTrue(""""week_start":"2026-11-09"""" in body, body)
        assertTrue(""""energy":4""" in body, body)
        assertTrue(""""notes":"good week"""" in body, body)
        assertTrue("user_id" !in body, body)
    }

    @Test
    fun save_clearingTheNotesSendsAnEmptyString() = runTest {
        val repo = signedIn()

        repo.save(CheckIn(week, 3, "")).getOrThrow()

        val body = (requests.last { it.method == HttpMethod.Post }.body as TextContent).text
        assertTrue(""""notes":""""" in body, body)
    }

    @Test
    fun save_checkViolationIsAValidationError() = runTest {
        val repo = signedIn()
        writeStatus = HttpStatusCode.BadRequest
        writeBody = """{"code":"23514","message":"new row violates check constraint","details":null,"hint":null}"""

        assertEquals(AppError.Validation, repo.save(CheckIn(week, 6, "")).appErrorOrNull())
    }
}
