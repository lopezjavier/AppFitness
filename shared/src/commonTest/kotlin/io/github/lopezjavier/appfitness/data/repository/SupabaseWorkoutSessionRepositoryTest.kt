package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
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
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""
private const val SESSION_ROW =
    """[{"id":"s1","routine_id":"r1","name":"Torso","status":"in_progress",""" +
        """"started_at":"2026-10-19T10:00:00+00:00","ended_at":null,"workout_sets":[""" +
        """{"exercise_id":"e2","exercise_position":1,"superset_group":null,"set_index":1,"set_type":"working",""" +
        """"weight_kg":0,"reps":0,"completed_at":null},""" +
        """{"exercise_id":"e1","exercise_position":0,"superset_group":3,"set_index":2,"set_type":"working",""" +
        """"weight_kg":80.5,"reps":8,"completed_at":"2026-10-19T10:10:00+00:00"},""" +
        """{"exercise_id":"e1","exercise_position":0,"superset_group":3,"set_index":1,"set_type":"warm_up",""" +
        """"weight_kg":40,"reps":10,"completed_at":"2026-10-19T10:05:00+00:00"}]}]"""

class SupabaseWorkoutSessionRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var getBody = "[]"
    private var rpcBody = "\"new-id\""
    private var rpcStatus = HttpStatusCode.OK
    private var failing = false

    private suspend fun signedIn(signIn: Boolean = true): SupabaseWorkoutSessionRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
                    failing -> throw IOException("offline")
                    request.url.encodedPath.contains("/rpc/") -> respond(rpcBody, rpcStatus, headers)
                    request.method == HttpMethod.Get -> respond(getBody, HttpStatusCode.OK, headers)
                    else -> respond("", HttpStatusCode.NoContent)
                }
            }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseWorkoutSessionRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun bodyOf(request: HttpRequestData) = (request.body as TextContent).text

    @Test
    fun startFromRoutine_postsTheRoutineIdAndReturnsTheSessionId() = runTest {
        val repo = signedIn()

        val id = repo.startFromRoutine("r1").getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/start_workout_session", call.url.encodedPath)
        assertEquals("""{"p_routine_id":"r1"}""", bodyOf(call))
        assertEquals("new-id", id)
    }

    @Test
    fun start_whenASessionIsAlreadyInProgress_failsWithAnAppError() = runTest {
        val repo = signedIn()
        rpcStatus = HttpStatusCode.Conflict
        rpcBody = """{"code":"23505","message":"duplicate key value violates unique constraint"}"""

        assertEquals(AppError.Unknown, repo.startFromRoutine("r1").appErrorOrNull())
    }

    @Test
    fun start_signedOut_isUnauthorized() = runTest {
        val repo = signedIn(signIn = false)

        assertEquals(AppError.Unauthorized, repo.startFromRoutine("r1").appErrorOrNull())
        assertTrue(rest().isEmpty())
    }

    @Test
    fun start_offline_isNetworkError() = runTest {
        val repo = signedIn()
        failing = true

        assertEquals(AppError.Network, repo.startFromRoutine("r1").appErrorOrNull())
    }

    @Test
    fun getInProgress_groupsSetsByExerciseInPositionAndSetOrder() = runTest {
        val repo = signedIn()
        getBody = SESSION_ROW

        val session = repo.getInProgress().getOrThrow()!!

        assertEquals("s1", session.id)
        assertEquals("r1", session.routineId)
        assertEquals(SessionStatus.InProgress, session.status)
        assertEquals(Instant.parse("2026-10-19T10:00:00Z"), session.startedAt)
        assertEquals(listOf("e1", "e2"), session.exercises.map { it.exerciseId })
        assertEquals(listOf(0, 1), session.exercises.map { it.position })
        val first = session.exercises[0]
        assertEquals(3, first.supersetGroup)
        assertEquals(listOf(1, 2), first.sets.map { it.setIndex })
        assertEquals(SetType.WarmUp, first.sets[0].setType)
        assertEquals(SetEntry.WeightReps(80.5, 8), first.sets[1].entry)
        assertEquals(2, first.sets.count { it.isCompleted })
        assertEquals(false, session.exercises[1].sets.single().isCompleted)
        val query = rest().single().url.encodedQuery
        assertTrue("status=eq.in_progress" in query, query)
    }

    @Test
    fun getInProgress_withNoOpenSession_isNull() = runTest {
        val repo = signedIn()

        assertNull(repo.getInProgress().getOrThrow())
    }

    @Test
    fun upsertSet_postsTheSetWithTheConflictKey() = runTest {
        val repo = signedIn()

        repo.upsertSet(
            sessionId = "s1",
            exerciseId = "e1",
            exercisePosition = 0,
            supersetGroup = null,
            setIndex = 2,
            entry = SetEntry.WeightReps(80.5, 8),
            completedAt = Instant.parse("2026-10-19T10:10:00Z"),
        ).getOrThrow()

        val call = rest().single()
        assertEquals(HttpMethod.Post, call.method)
        assertEquals("/rest/v1/workout_sets", call.url.encodedPath)
        assertTrue(
            "on_conflict=session_id%2Cexercise_position%2Cset_index" in call.url.encodedQuery ||
                "on_conflict=session_id,exercise_position,set_index" in call.url.encodedQuery,
            call.url.encodedQuery,
        )
        val body = bodyOf(call)
        assertTrue(""""session_id":"s1"""" in body && """"exercise_id":"e1"""" in body, body)
        assertTrue(""""exercise_position":0""" in body && """"set_index":2""" in body, body)
        assertTrue(""""superset_group":null""" in body, body)
        assertTrue(""""set_type":"working"""" in body, body)
        assertTrue(""""weight_kg":80.5""" in body && """"reps":8""" in body, body)
        assertTrue(""""completed_at":"2026-10-19T10:10:00Z"""" in body, body)
    }

    @Test
    fun upsertSet_ofAPendingSet_sendsANullCompletion() = runTest {
        val repo = signedIn()

        repo.upsertSet("s1", "e1", 0, 1, 1, SetEntry.WeightReps(0.0, 0), completedAt = null).getOrThrow()

        assertTrue(""""completed_at":null""" in bodyOf(rest().single()))
    }

    @Test
    fun finish_callsTheRpcWithTheSessionId() = runTest {
        val repo = signedIn()

        repo.finish("s1").getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/finish_workout_session", call.url.encodedPath)
        assertEquals("""{"p_session_id":"s1"}""", bodyOf(call))
    }

    @Test
    fun discard_callsTheRpcWithTheSessionId() = runTest {
        val repo = signedIn()

        repo.discard("s1").getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/discard_workout_session", call.url.encodedPath)
        assertEquals("""{"p_session_id":"s1"}""", bodyOf(call))
    }

    @Test
    fun exerciseHistory_mapsTheRowsAndSendsTheWindow() = runTest {
        val repo = signedIn()
        rpcBody =
            """[{"session_id":"s1","session_date":"2026-10-01","best_one_rm":113.5,"total_volume":2120},""" +
            """{"session_id":"s2","session_date":"2026-10-08","best_one_rm":null,"total_volume":0}]"""

        val points = repo.exerciseHistory("e1", LocalDate(2026, 9, 19)).getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/exercise_history", call.url.encodedPath)
        assertEquals("""{"p_exercise_id":"e1","p_from":"2026-09-19"}""", bodyOf(call))
        assertEquals(listOf(LocalDate(2026, 10, 1), LocalDate(2026, 10, 8)), points.map { it.date })
        assertEquals(113.5, points[0].bestOneRepMax)
        assertEquals(2120.0, points[0].totalVolume)
        assertNull(points[1].bestOneRepMax)
    }

    @Test
    fun lastPerformance_callsTheRpcAndMapsTheRows() = runTest {
        val repo = signedIn()
        rpcBody = """[{"set_index":1,"weight_kg":80,"reps":10},{"set_index":2,"weight_kg":82.5,"reps":8}]"""

        val sets = repo.lastPerformance("e1").getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/last_performance", call.url.encodedPath)
        assertEquals("""{"p_exercise_id":"e1"}""", bodyOf(call))
        assertEquals(listOf(PreviousSet(1, 80.0, 10), PreviousSet(2, 82.5, 8)), sets)
    }

    @Test
    fun lastPerformance_neverDone_isEmpty() = runTest {
        val repo = signedIn()
        rpcBody = "[]"

        assertTrue(repo.lastPerformance("e1").getOrThrow().isEmpty())
    }

    @Test
    fun lastPerformance_offline_isNetworkError() = runTest {
        val repo = signedIn()
        failing = true

        assertEquals(AppError.Network, repo.lastPerformance("e1").appErrorOrNull())
    }

    @Test
    fun lastPerformance_signedOut_isUnauthorized() = runTest {
        val repo = signedIn(signIn = false)

        assertEquals(AppError.Unauthorized, repo.lastPerformance("e1").appErrorOrNull())
    }

    @Test
    fun exerciseHistory_withoutWindow_sendsANullFrom() = runTest {
        val repo = signedIn()
        rpcBody = "[]"

        repo.exerciseHistory("e1").getOrThrow()

        assertEquals("""{"p_exercise_id":"e1","p_from":null}""", bodyOf(rest().single()))
    }

    @Test
    fun listCompleted_filtersByStatusOrdersNewestFirstAndSendsTheWindow() = runTest {
        val repo = signedIn()
        getBody = SESSION_ROW.replace("in_progress", "completed")

        val sessions = repo.listCompleted(offset = 20, limit = 20).getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/workout_sessions", call.url.encodedPath)
        val query = call.url.encodedQuery
        assertTrue("status=eq.completed" in query, query)
        assertTrue("order=started_at.desc" in query && "id.desc" in query, query)
        assertTrue("offset=20" in query && "limit=20" in query, query)
        assertEquals(listOf("s1"), sessions.map { it.id })
        assertEquals(SessionStatus.Completed, sessions.single().status)
    }

    @Test
    fun listCompleted_offline_isNetworkError() = runTest {
        val repo = signedIn()
        failing = true

        assertEquals(AppError.Network, repo.listCompleted(0, 20).appErrorOrNull())
    }

    @Test
    fun getSession_filtersByIdAndCompletedStatus() = runTest {
        val repo = signedIn()
        getBody = SESSION_ROW.replace("in_progress", "completed")

        val session = repo.getSession("s1").getOrThrow()

        val query = rest().single().url.encodedQuery
        assertTrue("id=eq.s1" in query && "status=eq.completed" in query, query)
        assertEquals("s1", session?.id)
    }

    @Test
    fun getSession_withNoRows_isNull() = runTest {
        val repo = signedIn()

        assertNull(repo.getSession("missing").getOrThrow())
    }

    @Test
    fun deleteSession_sendsADeleteWithTheId() = runTest {
        val repo = signedIn()

        repo.deleteSession("s1").getOrThrow()

        val call = rest().single()
        assertEquals(HttpMethod.Delete, call.method)
        assertEquals("/rest/v1/workout_sessions", call.url.encodedPath)
        assertTrue("id=eq.s1" in call.url.encodedQuery, call.url.encodedQuery)
    }

    @Test
    fun deleteSession_offline_isNetworkError() = runTest {
        val repo = signedIn()
        failing = true

        assertEquals(AppError.Network, repo.deleteSession("s1").appErrorOrNull())
    }
}
