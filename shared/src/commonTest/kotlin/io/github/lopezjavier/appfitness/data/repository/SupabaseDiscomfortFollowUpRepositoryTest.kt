package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.DiscomfortNoteReason
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseDiscomfortFollowUpRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var status = HttpStatusCode.OK
    private var body = "[]"
    private var offline = false

    private suspend fun signedIn() = MockEngine { request ->
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        if (request.url.encodedPath.contains("/auth/")) return@MockEngine respond(SESSION, HttpStatusCode.OK, headers)
        requests += request
        if (offline) throw IOException("offline")
        respond(body, status, headers)
    }.let { engine ->
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        SupabaseDiscomfortFollowUpRepository(client.auth, client.postgrest)
    }

    private fun lastBody() = (requests.last().body as TextContent).text

    @Test
    fun followUps_mapsRowsAndTheNoteReason() = runTest {
        val repo = signedIn()
        body = """[
            {"exercise_id":"e1","discomfort_id":"d2","logged_at":"2026-11-01T10:00:00+00:00",
             "offer_swap":true,"show_note":true,"note_reason":"days14"},
            {"exercise_id":"e2","discomfort_id":"d3","logged_at":"2026-11-05T10:00:00+00:00",
             "offer_swap":false,"show_note":true,"note_reason":"sessions3"},
            {"exercise_id":"e3","discomfort_id":"d4","logged_at":"2026-11-06T10:00:00+00:00",
             "offer_swap":true,"show_note":false,"note_reason":null}]"""

        val rows = repo.followUps().getOrThrow()

        assertEquals(listOf("e1", "e2", "e3"), rows.map { it.exerciseId })
        assertEquals(Instant.parse("2026-11-01T10:00:00Z"), rows[0].loggedAt)
        assertEquals("d2", rows[0].discomfortId)
        assertTrue(rows[0].offerSwap && rows[0].showNote)
        assertEquals(DiscomfortNoteReason.Days14, rows[0].noteReason)
        assertEquals(DiscomfortNoteReason.Sessions3, rows[1].noteReason)
        assertFalse(rows[1].offerSwap)
        assertNull(rows[2].noteReason)
        assertTrue(requests.last().url.encodedPath.endsWith("/rpc/coach_discomfort_followups"))
    }

    @Test
    fun followUps_anUnknownNoteReasonIsAFailureNotACrash() = runTest {
        val repo = signedIn()
        body = """[{"exercise_id":"e1","discomfort_id":"d2","logged_at":"2026-11-01T10:00:00+00:00",
            "offer_swap":true,"show_note":true,"note_reason":"weeks"}]"""

        assertTrue(repo.followUps().isFailure)
    }

    @Test
    fun swapCandidates_sendsTheFlaggedExerciseAndKeepsTheServerOrder() = runTest {
        val repo = signedIn()
        body = """[{"exercise_id":"b","is_favorite":true,"similarity":0},
            {"exercise_id":"a","is_favorite":false,"similarity":0.8}]"""

        val rows = repo.swapCandidates("e1").getOrThrow()

        assertEquals(listOf("b", "a"), rows.map { it.exerciseId })
        assertEquals(listOf(true, false), rows.map { it.isFavorite })
        assertTrue(requests.last().url.encodedPath.endsWith("/rpc/coach_swap_candidates"))
        assertTrue(""""p_exercise_id":"e1"""" in lastBody(), lastBody())
    }

    @Test
    fun swap_sendsBothExercisesAndReturnsTheRoutinesChanged() = runTest {
        val repo = signedIn()
        body = """{"routines_changed":3}"""

        assertEquals(3, repo.swap("e1", "e9").getOrThrow())

        assertTrue(requests.last().url.encodedPath.endsWith("/rpc/coach_swap_flagged_exercise"))
        assertTrue(""""p_exercise_id":"e1"""" in lastBody(), lastBody())
        assertTrue(""""p_new_exercise_id":"e9"""" in lastBody(), lastBody())
    }

    @Test
    fun declineSwap_callsItsRpcWithTheExercise() = runTest {
        val repo = signedIn()
        body = "null"

        repo.declineSwap("e1").getOrThrow()

        assertTrue(requests.last().url.encodedPath.endsWith("/rpc/decline_discomfort_swap"))
        assertTrue(""""p_exercise_id":"e1"""" in lastBody(), lastBody())
    }

    @Test
    fun dismissNote_callsItsRpcWithTheExercise() = runTest {
        val repo = signedIn()
        body = "null"

        repo.dismissNote("e1").getOrThrow()

        assertTrue(requests.last().url.encodedPath.endsWith("/rpc/dismiss_discomfort_note"))
        assertTrue(""""p_exercise_id":"e1"""" in lastBody(), lastBody())
    }

    @Test
    fun offlineIsMappedToTheNetworkError() = runTest {
        val repo = signedIn()
        offline = true

        assertEquals(AppError.Network, repo.followUps().appErrorOrNull())
        assertEquals(AppError.Network, repo.swapCandidates("e1").appErrorOrNull())
        assertEquals(AppError.Network, repo.swap("e1", "e9").appErrorOrNull())
        assertEquals(AppError.Network, repo.declineSwap("e1").appErrorOrNull())
        assertEquals(AppError.Network, repo.dismissNote("e1").appErrorOrNull())
    }

    @Test
    fun serverErrorsOnTheSwapAreFailuresNotCrashes() = runTest {
        val repo = signedIn()
        status = HttpStatusCode.BadRequest
        body = """{"code":"22023","message":"replacement is not a candidate","details":null,"hint":null}"""
        assertTrue(repo.swap("e1", "e9").isFailure)

        status = HttpStatusCode.NotFound
        body = """{"code":"P0002","message":"exercise is not in the block","details":null,"hint":null}"""
        assertTrue(repo.swap("e1", "e9").isFailure)
    }
}
