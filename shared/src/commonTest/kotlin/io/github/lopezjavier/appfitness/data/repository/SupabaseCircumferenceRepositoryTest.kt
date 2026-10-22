package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceEntry
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
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
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""
private const val ROWS =
    """[{"id":"c-2","measured_at":"2026-10-20T08:00:00+00:00","site":"hip","value_cm":98.0,""" +
        """"created_at":"2026-10-20T08:05:00+00:00"},""" +
        """{"id":"c-1","measured_at":"2026-10-14T10:00:00+00:00","site":"waist","value_cm":84.5,""" +
        """"created_at":"2026-10-14T10:01:00+00:00"}]"""
private val HIP_90 = CircumferenceEntry(CircumferenceSite.Hip, 90.0)
private val MEASURED_AT = Instant.parse("2026-10-14T12:30:00Z")

class SupabaseCircumferenceRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun signedIn(
        rows: String = "[]",
        failing: () -> Boolean = { false },
        rejectWritesWith: String? = null,
    ) = MockEngine { request ->
        requests += request
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        when {
            request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)

            failing() -> throw IOException("offline")

            rejectWritesWith != null && request.method != HttpMethod.Get -> respond(
                """{"code":"$rejectWritesWith","message":"boom","details":null,"hint":null}""",
                HttpStatusCode.BadRequest,
                headers,
            )

            request.method == HttpMethod.Get -> respond(rows, HttpStatusCode.OK, headers)

            else -> respond("", HttpStatusCode.Created, headers)
        }
    }.let { engine ->
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        SupabaseCircumferenceRepository(client.auth, client.postgrest)
    }

    private fun writes() = requests.filter { it.method != HttpMethod.Get && !it.url.encodedPath.contains("/auth/") }

    @Test
    fun getAll_decodesRowsAndOrdersNewestFirst() = runTest {
        val repo = signedIn(rows = ROWS)

        val rows = repo.getAll().getOrThrow()

        assertEquals(
            listOf(
                BodyCircumference(
                    "c-2",
                    CircumferenceSite.Hip,
                    98.0,
                    Instant.parse("2026-10-20T08:00:00Z"),
                    Instant.parse("2026-10-20T08:05:00Z"),
                ),
                BodyCircumference(
                    "c-1",
                    CircumferenceSite.Waist,
                    84.5,
                    Instant.parse("2026-10-14T10:00:00Z"),
                    Instant.parse("2026-10-14T10:01:00Z"),
                ),
            ),
            rows,
        )
        val url = requests.last().url.toString()
        assertTrue(url.contains("order=measured_at.desc"), url)
        assertTrue(url.contains("created_at"), url)
    }

    @Test
    fun addAll_postsOneRequestWithEveryItem() = runTest {
        val repo = signedIn()

        val entries =
            listOf(CircumferenceEntry(CircumferenceSite.Waist, 84.5), CircumferenceEntry(CircumferenceSite.Hip, 98.0))
        assertTrue(repo.addAll(entries, MEASURED_AT).isSuccess)

        val post = writes().single()
        assertEquals(HttpMethod.Post, post.method)
        assertEquals(
            """[{"site":"waist","value_cm":84.5,"measured_at":"2026-10-14T12:30:00Z","source":"manual"},""" +
                """{"site":"hip","value_cm":98.0,"measured_at":"2026-10-14T12:30:00Z","source":"manual"}]""",
            (post.body as TextContent).text,
        )
    }

    @Test
    fun update_patchesValueAndDateById() = runTest {
        val repo = signedIn()

        assertTrue(repo.update("c-1", 85.0, MEASURED_AT).isSuccess)

        val patch = writes().single()
        assertEquals(HttpMethod.Patch, patch.method)
        assertTrue(patch.url.toString().contains("id=eq.c-1"), patch.url.toString())
        assertEquals("""{"value_cm":85.0,"measured_at":"2026-10-14T12:30:00Z"}""", (patch.body as TextContent).text)
    }

    @Test
    fun delete_deletesById() = runTest {
        val repo = signedIn()

        assertTrue(repo.delete("c-1").isSuccess)

        val delete = writes().single()
        assertEquals(HttpMethod.Delete, delete.method)
        assertTrue(delete.url.toString().contains("id=eq.c-1"), delete.url.toString())
    }

    @Test
    fun networkFailure_isAppErrorNetwork() = runTest {
        var fail = false
        val repo = signedIn(failing = { fail })
        fail = true

        assertEquals(AppError.Network, repo.getAll().appErrorOrNull())
        assertEquals(AppError.Network, repo.addAll(listOf(HIP_90), MEASURED_AT).appErrorOrNull())
        assertEquals(AppError.Network, repo.delete("c-1").appErrorOrNull())
    }

    @Test
    fun checkViolation_isValidation() = runTest {
        val repo = signedIn(rejectWritesWith = "23514")

        assertEquals(
            AppError.Validation,
            repo.addAll(listOf(HIP_90, CircumferenceEntry(CircumferenceSite.Neck, 19.0)), MEASURED_AT).appErrorOrNull(),
        )
        assertEquals(AppError.Validation, repo.update("c-1", 19.0, MEASURED_AT).appErrorOrNull())
    }

    @Test
    fun signedOut_isUnauthorized() = runTest {
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, MockEngine { respond("[]") }, MemorySessionManager())
        val repo = SupabaseCircumferenceRepository(client.auth, client.postgrest)

        assertEquals(AppError.Unauthorized, repo.getAll().appErrorOrNull())
        assertEquals(AppError.Unauthorized, repo.delete("c-1").appErrorOrNull())
    }
}
