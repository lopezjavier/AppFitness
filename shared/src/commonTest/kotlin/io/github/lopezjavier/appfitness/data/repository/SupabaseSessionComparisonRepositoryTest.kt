package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ComparisonSet
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

/** The `session_comparison` read of "Frente a la última vez" (T-323). */
class SupabaseSessionComparisonRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var rpcBody = "[]"
    private var failing = false

    private suspend fun signedIn(): SupabaseWorkoutSessionRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
                    failing -> throw IOException("offline")
                    else -> respond(rpcBody, HttpStatusCode.OK, headers)
                }
            }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseWorkoutSessionRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    @Test
    fun postsTheSessionIdAndDecodesBothSessions() = runTest {
        val repo = signedIn()
        rpcBody =
            """[{"exercise_id":"e1","open_discomfort":true,""" +
            """"current":{"session_id":"s2","day":"2026-10-20","deload":false,"sets":[""" +
            """{"weight_kg":82.5,"reps":6,"rpe":8.5,"rep_min":6,"rep_max":10}]},""" +
            """"previous":{"session_id":"s1","day":"2026-10-13","deload":false,"sets":[""" +
            """{"weight_kg":80,"reps":8,"rpe":null,"rep_min":6,"rep_max":10}]}}]"""

        val rows = repo.sessionComparison("s2").getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/session_comparison", call.url.encodedPath)
        assertEquals("""{"p_session_id":"s2"}""", (call.body as TextContent).text)
        val row = rows.single()
        assertEquals("e1", row.exerciseId)
        assertTrue(row.openDiscomfort)
        assertEquals(LocalDate(2026, 10, 20), row.current.date)
        assertEquals(ComparisonSet(82.5, 6, 8.5, 6, 10), row.current.sets.single())
        assertEquals(ComparisonSet(80.0, 8, null, 6, 10), row.previous?.sets?.single())
        assertEquals("s1", row.previous?.sessionId)
    }

    @Test
    fun withoutAPreviousSessionOrARepRange_decodesNulls() = runTest {
        val repo = signedIn()
        rpcBody =
            """[{"exercise_id":"e1","open_discomfort":false,""" +
            """"current":{"session_id":"s2","day":"2026-10-20","deload":true,"sets":[""" +
            """{"weight_kg":60,"reps":10,"rpe":null,"rep_min":null,"rep_max":null}]},"previous":null}]"""

        val row = repo.sessionComparison("s2").getOrThrow().single()

        assertNull(row.previous)
        assertTrue(row.current.deload)
        assertEquals(ComparisonSet(60.0, 10), row.current.sets.single())
    }

    @Test
    fun emptyArray_isEmpty() = runTest {
        val repo = signedIn()

        assertTrue(repo.sessionComparison("s2").getOrThrow().isEmpty())
    }

    @Test
    fun offline_isNetworkError() = runTest {
        val repo = signedIn()
        failing = true

        assertEquals(AppError.Network, repo.sessionComparison("s2").appErrorOrNull())
    }
}
