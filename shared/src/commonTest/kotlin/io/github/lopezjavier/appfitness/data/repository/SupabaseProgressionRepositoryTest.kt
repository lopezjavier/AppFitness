package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.model.ProgressionAction
import io.github.lopezjavier.appfitness.domain.model.ProgressionReason
import io.github.lopezjavier.appfitness.domain.model.SetUsed
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
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseProgressionRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var body = "[]"
    private var offline = false

    private suspend fun repo(): SupabaseProgressionRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
                    offline -> throw IOException("offline")
                    else -> respond(body, HttpStatusCode.OK, headers)
                }
            }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseProgressionRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    @Test
    fun suggestionsFor_callsTheRpcWithTheSessionAndMapsTheArray() = runTest {
        body = """[{"exercise_id":"e1","rep_min":6,"rep_max":8,"action":"add_rep","base_weight_kg":80,""" +
            """"goal_reps":8,"reason":"below_max","used_rpe":true,"sets_used":[""" +
            """{"session_date":"2026-10-24","weight_kg":80,"reps":7,"rpe":8.5},""" +
            """{"session_date":"2026-10-24","weight_kg":80,"reps":6,"rpe":null}]}]"""

        val result = repo().suggestionsFor("s1").getOrThrow()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/progression_suggestions"))
        assertEquals("""{"p_session_id":"s1"}""", (request.body as TextContent).text)
        val suggestion = result.single()
        assertEquals("e1", suggestion.exerciseId)
        assertEquals(ProgressionAction.AddRep, suggestion.action)
        assertEquals(ProgressionReason.BelowMax, suggestion.reason)
        assertEquals(80.0, suggestion.weightKg)
        assertEquals(8, suggestion.goalReps)
        assertEquals(
            listOf(
                SetUsed(LocalDate(2026, 10, 24), 80.0, 7, 8.5),
                SetUsed(LocalDate(2026, 10, 24), 80.0, 6, null),
            ),
            suggestion.setsUsed,
        )
    }

    @Test
    fun suggestionsFor_mapsEveryActionAndReasonCode() = runTest {
        body = """[
            {"exercise_id":"a","action":"raise_weight","base_weight_kg":80,"goal_reps":6,"reason":"all_sets_at_max"},
            {"exercise_id":"b","action":"lower_weight","base_weight_kg":80,"goal_reps":6,
             "reason":"two_sessions_below_min"},
            {"exercise_id":"c","action":"add_rep","base_weight_kg":80,"goal_reps":8,"reason":"kept_discomfort"}
        ]"""

        val result = repo().suggestionsFor("s1").getOrThrow()

        assertEquals(
            listOf(ProgressionAction.RaiseWeight, ProgressionAction.LowerWeight, ProgressionAction.AddRep),
            result.map { it.action },
        )
        assertEquals(ProgressionReason.KeptDiscomfort, result.last().reason)
    }

    @Test
    fun suggestionsFor_dropsAnElementWithAnUnknownCode() = runTest {
        body = """[{"exercise_id":"a","action":"none","base_weight_kg":80,"goal_reps":6,"reason":"below_max"}]"""

        assertEquals(emptyList(), repo().suggestionsFor("s1").getOrThrow())
    }

    @Test
    fun suggestionsFor_emptyArrayIsAnEmptyList() = runTest {
        assertEquals(emptyList(), repo().suggestionsFor("s1").getOrThrow())
    }

    @Test
    fun suggestionsFor_offlineFails() = runTest {
        val repository = repo()
        offline = true

        assertTrue(repository.suggestionsFor("s1").isFailure)
    }
}
