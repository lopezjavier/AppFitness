package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.model.WeeklyMuscleVolumeRow
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
import kotlin.time.Instant

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""
private const val SESSION_ROW =
    """[{"id":"s1","routine_id":null,"name":"Torso","status":"completed",""" +
        """"started_at":"2026-10-19T10:00:00+00:00","ended_at":"2026-10-19T11:00:00+00:00","workout_sets":[""" +
        """{"exercise_id":"e1","exercise_position":0,"superset_group":null,"set_index":1,"set_type":"working",""" +
        """"weight_kg":100,"reps":4,"completed_at":"2026-10-19T10:10:00+00:00"}]}]"""
private const val SET_ROWS =
    """[{"session_id":"s1","reps":8,"workout_sessions":{"started_at":"2026-10-01T23:30:00+00:00"}},""" +
        """{"session_id":"s1","reps":10,"workout_sessions":{"started_at":"2026-10-01T23:30:00+00:00"}},""" +
        """{"session_id":"s2","reps":12,"workout_sessions":{"started_at":"2026-09-20T08:00:00+00:00"}}]"""

class SupabaseStrengthRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var getBody = "[]"
    private var failing = false

    private suspend fun signedIn(signIn: Boolean = true): SupabaseStrengthRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
                    failing -> throw IOException("offline")
                    else -> respond(getBody, HttpStatusCode.OK, headers)
                }
            }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseStrengthRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    @Test
    fun recentSessions_asksForCompletedSessionsSinceTheInstantAndMapsThem() = runTest {
        val repo = signedIn()
        getBody = SESSION_ROW

        val sessions = repo.recentSessions(Instant.parse("2026-09-07T00:00:00Z")).getOrThrow()

        val query = rest().single().url.toString()
        assertTrue(query.contains("/rest/v1/workout_sessions"), query)
        assertTrue(query.contains("status=eq.completed"), query)
        assertTrue(query.contains("started_at=gte.2026-09-07T00"), query)
        assertEquals("s1", sessions.single().id)
        assertEquals("e1", sessions.single().exercises.single().exerciseId)
    }

    @Test
    fun maxRepsHistory_filtersCompletedNonWarmUpSetsOfTheExercise() = runTest {
        val repo = signedIn()

        repo.maxRepsHistory("e1", LocalDate(2026, 9, 1)).getOrThrow()

        val query = rest().single().url.toString()
        assertTrue(query.contains("/rest/v1/workout_sets"), query)
        assertTrue(query.contains("exercise_id=eq.e1"), query)
        assertTrue(query.contains("workout_sessions.status=eq.completed"), query)
        assertTrue(query.contains("set_type=neq.warm_up"), query)
        assertTrue(query.contains("completed_at=not.is.null"), query)
        assertTrue(query.contains("workout_sessions.started_at=gte.2026-09-01T00"), query)
    }

    @Test
    fun maxRepsHistory_withWarmUpIncludedDoesNotFilterTheSetType() = runTest {
        val repo = signedIn()

        repo.maxRepsHistory("e1", includeWarmUp = true).getOrThrow()

        val query = rest().single().url.toString()
        assertTrue(!query.contains("set_type"), query)
        assertTrue(query.contains("completed_at=not.is.null"), query)
    }

    @Test
    fun maxRepsHistory_withoutLowerBoundDoesNotFilterByDate() = runTest {
        val repo = signedIn()

        repo.maxRepsHistory("e1").getOrThrow()

        assertTrue(!rest().single().url.toString().contains("started_at=gte"))
    }

    @Test
    fun maxRepsHistory_keepsTheHighestRepsPerSessionOldestFirstOnTheUtcDay() = runTest {
        val repo = signedIn()
        getBody = SET_ROWS

        val points = repo.maxRepsHistory("e1").getOrThrow()

        assertEquals(
            listOf(
                MaxRepsPoint("s2", LocalDate(2026, 9, 20), 12),
                MaxRepsPoint("s1", LocalDate(2026, 10, 1), 10),
            ),
            points,
        )
    }

    @Test
    fun weeklyMuscleVolume_callsTheRpcWithTheLowerBoundAndMapsTheRows() = runTest {
        val repo = signedIn()
        getBody = """[{"week_start":"2026-10-12","muscle_group":"chest","total_volume":5400.0},""" +
            """{"week_start":"2026-10-19","muscle_group":"quads","total_volume":1500}]"""

        val rows = repo.weeklyMuscleVolume(LocalDate(2026, 9, 1)).getOrThrow()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/weekly_muscle_volume"), request.url.toString())
        assertEquals(
            listOf(
                WeeklyMuscleVolumeRow(LocalDate(2026, 10, 12), MuscleGroup.Chest, 5400.0),
                WeeklyMuscleVolumeRow(LocalDate(2026, 10, 19), MuscleGroup.Quads, 1500.0),
            ),
            rows,
        )
    }

    @Test
    fun weeklyMuscleVolume_sendsPFromInTheBody() = runTest {
        val repo = signedIn()

        repo.weeklyMuscleVolume(LocalDate(2026, 9, 1)).getOrThrow()

        val body = (rest().single().body as TextContent).text
        assertEquals("""{"p_from":"2026-09-01"}""", body)
    }

    @Test
    fun weeklyMuscleVolume_signedOutIsUnauthorizedAndOfflineIsNetworkError() = runTest {
        val signedOut = signedIn(signIn = false)
        assertEquals(AppError.Unauthorized, signedOut.weeklyMuscleVolume(LocalDate(2026, 9, 1)).appErrorOrNull())
        assertTrue(rest().isEmpty())

        val repo = signedIn()
        failing = true
        assertEquals(AppError.Network, repo.weeklyMuscleVolume(LocalDate(2026, 9, 1)).appErrorOrNull())
    }

    @Test
    fun signedOut_isUnauthorizedWithoutRequests() = runTest {
        val repo = signedIn(signIn = false)

        assertEquals(AppError.Unauthorized, repo.maxRepsHistory("e1").appErrorOrNull())
        assertTrue(rest().isEmpty())
    }

    @Test
    fun offline_isNetworkError() = runTest {
        val repo = signedIn()
        failing = true

        assertEquals(AppError.Network, repo.recentSessions(Instant.parse("2026-09-07T00:00:00Z")).appErrorOrNull())
    }
}
