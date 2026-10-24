package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
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
        """"weight_kg":80.5,"reps":8,"rpe":7.5,"completed_at":"2026-10-19T10:10:00+00:00"},""" +
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

        assertEquals(AppError.SessionAlreadyInProgress, repo.startFromRoutine("r1").appErrorOrNull())
    }

    @Test
    fun start_whenTheServerFailsForAnotherReason_isUnknown() = runTest {
        val repo = signedIn()
        rpcStatus = HttpStatusCode.InternalServerError
        rpcBody = """{"code":"XX000","message":"boom"}"""

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
    fun getInProgress_readsThePauseState() = runTest {
        val repo = signedIn()
        getBody = SESSION_ROW.replace(
            """"ended_at":null,""",
            """"ended_at":null,"paused_at":"2026-10-19T10:20:00+00:00","paused_total_s":300,""",
        )

        val session = repo.getInProgress().getOrThrow()!!

        assertEquals(Instant.parse("2026-10-19T10:20:00Z"), session.pausedAt)
        assertEquals(300L, session.pausedTotalSeconds)
        assertTrue(rest().single().url.toString().contains("paused_total_s"))
    }

    @Test
    fun getInProgress_readsTheExerciseRestsByPosition() = runTest {
        val repo = signedIn()
        getBody = SESSION_ROW.replace(""""ended_at":null,"""", """"ended_at":null,"exercise_rests":{"0":90,"3":45},"""")

        val session = repo.getInProgress().getOrThrow()!!

        assertEquals(mapOf(0 to 90, 3 to 45), session.exerciseRests)
        assertTrue(rest().single().url.toString().contains("exercise_rests"))
    }

    @Test
    fun getInProgress_withoutRestsHasAnEmptyMap() = runTest {
        val repo = signedIn()
        getBody = SESSION_ROW

        assertEquals(emptyMap(), repo.getInProgress().getOrThrow()!!.exerciseRests)
    }

    @Test
    fun setExerciseRest_callsTheRpc() = runTest {
        val repo = signedIn()

        repo.setExerciseRest("s1", 2, 90).getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/set_session_rest", call.url.encodedPath)
        assertEquals("""{"p_session_id":"s1","p_exercise_position":2,"p_rest_seconds":90}""", bodyOf(call))
    }

    @Test
    fun getInProgress_withoutPauseColumnsIsNotPaused() = runTest {
        val repo = signedIn()
        getBody = SESSION_ROW

        val session = repo.getInProgress().getOrThrow()!!

        assertNull(session.pausedAt)
        assertEquals(0L, session.pausedTotalSeconds)
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
        assertEquals(7.5, first.sets[1].rpe)
        assertNull(first.sets[0].rpe)
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
    fun upsertSet_sendsTheRpe() = runTest {
        val repo = signedIn()

        repo.upsertSet("s1", "e1", 0, null, 2, SetEntry.WeightReps(80.5, 8), null, rpe = 7.5).getOrThrow()

        assertTrue(""""rpe":7.5""" in bodyOf(rest().single()))
    }

    @Test
    fun upsertSet_withoutRpe_sendsANullSoItCanBeCleared() = runTest {
        val repo = signedIn()

        repo.upsertSet("s1", "e1", 0, null, 2, SetEntry.WeightReps(80.5, 8), null).getOrThrow()

        assertTrue(""""rpe":null""" in bodyOf(rest().single()))
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
    fun personalRecords_mapsTheRowsAndDropsUnknownTypes() = runTest {
        val repo = signedIn()
        rpcBody =
            """[{"record_type":"heaviest_lift","value":100,"session_id":"s1","session_date":"2026-10-01"},""" +
            """{"record_type":"total_reps","value":35,"session_id":null,"session_date":null},""" +
            """{"record_type":"future_family","value":1,"session_id":"s1","session_date":"2026-10-01"}]"""

        val records = repo.personalRecords("e1").getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/personal_records", call.url.encodedPath)
        assertEquals("""{"p_exercise_id":"e1"}""", bodyOf(call))
        assertEquals(listOf(PersonalRecordType.HeaviestLift, PersonalRecordType.TotalReps), records.map { it.type })
        assertEquals("s1", records[0].sessionId)
        assertEquals(LocalDate(2026, 10, 1), records[0].date)
        assertNull(records[1].sessionId)
    }

    @Test
    fun sessionRecords_mapsTheAggregates() = runTest {
        val repo = signedIn()
        rpcBody =
            """[{"session_id":"s1","started_at":"2026-10-01T10:00:00+00:00","heaviest_lift":100,"best_set":680,""" +
            """"session_volume":1180,"best_one_rm":116.5,"total_reps":13,"best_duration_s":null,""" +
            """"best_distance_m":null,"total_steps":null,"total_floors":null}]"""

        val rows = repo.sessionRecords("e1").getOrThrow()

        assertEquals("/rest/v1/rpc/exercise_session_records", rest().single().url.encodedPath)
        val row = rows.single()
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"), row.startedAt)
        assertEquals(116.5, row.bestOneRm)
        assertEquals(1180.0, row.sessionVolume)
        assertEquals(13L, row.totalReps)
        assertNull(row.bestDurationS)
    }

    @Test
    fun recordCalls_failWhenOffline() = runTest {
        val repo = signedIn()
        failing = true

        assertTrue(repo.personalRecords("e1").isFailure)
        assertTrue(repo.sessionRecords("e1").isFailure)
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
    fun lastPerformance_withARoutineSendsTheRoutineId() = runTest {
        val repo = signedIn()
        rpcBody = "[]"

        repo.lastPerformance("e1", "r1").getOrThrow()

        assertEquals("""{"p_exercise_id":"e1","p_routine_id":"r1"}""", bodyOf(rest().single()))
    }

    @Test
    fun pause_callsTheRpcWithTheInstant() = runTest {
        val repo = signedIn()

        repo.pause("s1", Instant.parse("2026-10-19T10:10:00Z")).getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/pause_workout_session", call.url.encodedPath)
        assertEquals("""{"p_session_id":"s1","p_at":"2026-10-19T10:10:00Z"}""", bodyOf(call))
    }

    @Test
    fun resume_callsTheRpcWithTheInstant() = runTest {
        val repo = signedIn()

        repo.resume("s1", Instant.parse("2026-10-19T10:15:00Z")).getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/resume_workout_session", call.url.encodedPath)
        assertEquals("""{"p_session_id":"s1","p_at":"2026-10-19T10:15:00Z"}""", bodyOf(call))
    }

    @Test
    fun deleteSet_callsTheRpcWithTheKey() = runTest {
        val repo = signedIn()

        repo.deleteSet("s1", 2, 3).getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/delete_workout_set", call.url.encodedPath)
        assertEquals("""{"p_session_id":"s1","p_exercise_position":2,"p_set_index":3}""", bodyOf(call))
    }

    @Test
    fun sessionControls_offlineAndSignedOutMapErrors() = runTest {
        val offline = signedIn()
        failing = true
        assertEquals(AppError.Network, offline.pause("s1", Instant.parse("2026-10-19T10:10:00Z")).appErrorOrNull())
        assertEquals(AppError.Network, offline.deleteSet("s1", 0, 1).appErrorOrNull())
        val out = signedIn(signIn = false)
        assertEquals(AppError.Unauthorized, out.resume("s1", Instant.parse("2026-10-19T10:10:00Z")).appErrorOrNull())
    }

    @Test
    fun lastPerformance_parsesTheMetricColumns() = runTest {
        val repo = signedIn()
        rpcBody =
            """[{"set_index":1,"weight_kg":0,"reps":0,"rpe":null,"duration_s":1800,"distance_m":5000.5,""" +
            """"steps":null,"floors":null},""" +
            """{"set_index":2,"weight_kg":0,"reps":0,"duration_s":600,"distance_m":null,"steps":900,"floors":12}]"""

        val sets = repo.lastPerformance("e1").getOrThrow()

        assertEquals(1800, sets[0].durationS)
        assertEquals(5000.5, sets[0].distanceM)
        assertNull(sets[0].steps)
        assertEquals(listOf(900, 12), listOf(sets[1].steps, sets[1].floors))
        assertNull(sets[1].distanceM)
    }

    @Test
    fun upsertSet_ofEachVariant_writesOnlyItsApplicableColumns() = runTest {
        val repo = signedIn()
        val cases = listOf(
            SetEntry.RepsOnly(12) to
                """"weight_kg":0.0,"reps":12,"duration_s":null,"distance_m":null,"steps":null,"floors":null""",
            SetEntry.Duration(90) to
                """"weight_kg":0.0,"reps":0,"duration_s":90,"distance_m":null,"steps":null,"floors":null""",
            SetEntry.WeightDuration(32.0, 45) to
                """"weight_kg":32.0,"reps":0,"duration_s":45,"distance_m":null,"steps":null,"floors":null""",
            SetEntry.DistanceDuration(5000.0, 1800) to
                """"weight_kg":0.0,"reps":0,"duration_s":1800,"distance_m":5000.0,"steps":null,"floors":null""",
            SetEntry.Bodyweight(10.0, 8) to
                """"weight_kg":10.0,"reps":8,"duration_s":null,"distance_m":null,"steps":null,"floors":null""",
            SetEntry.Assisted(20.0, 10) to
                """"weight_kg":20.0,"reps":10,"duration_s":null,"distance_m":null,"steps":null,"floors":null""",
            SetEntry.Weighted(15.0, 6) to
                """"weight_kg":15.0,"reps":6,"duration_s":null,"distance_m":null,"steps":null,"floors":null""",
            SetEntry.StepsDuration(4000, 1800) to
                """"weight_kg":0.0,"reps":0,"duration_s":1800,"distance_m":null,"steps":4000,"floors":null""",
            SetEntry.FloorsDuration(30, 600) to
                """"weight_kg":0.0,"reps":0,"duration_s":600,"distance_m":null,"steps":null,"floors":30""",
        )

        cases.forEach { (entry, expected) ->
            repo.upsertSet("s1", "e1", 0, null, 1, entry, null).getOrThrow()
            val body = bodyOf(rest().last())
            assertTrue(expected in body, "$entry wrote $body")
        }
    }

    @Test
    fun getInProgress_mapsTheColumnsToTheVariantOfTheExerciseMetricType() = runTest {
        val repo = signedIn()
        getBody =
            """[{"id":"s1","routine_id":null,"name":"Mix","status":"in_progress",""" +
            """"started_at":"2026-10-19T10:00:00+00:00","ended_at":null,"workout_sets":[""" +
            """{"exercise_id":"e1","exercise_position":0,"set_index":1,"weight_kg":0,"reps":0,""" +
            """"duration_s":90,"exercises":{"metric_type":"duration"},"completed_at":null},""" +
            """{"exercise_id":"e2","exercise_position":1,"set_index":1,"weight_kg":0,"reps":0,""" +
            """"duration_s":1800,"distance_m":5000.5,"exercises":{"metric_type":"distance_duration"},""" +
            """"completed_at":null},""" +
            """{"exercise_id":"e3","exercise_position":2,"set_index":1,"weight_kg":10,"reps":8,""" +
            """"exercises":{"metric_type":"bodyweight"},"completed_at":null},""" +
            """{"exercise_id":"e4","exercise_position":3,"set_index":1,"weight_kg":80,"reps":5,""" +
            """"completed_at":null}]}]"""

        val session = repo.getInProgress().getOrThrow()!!

        assertEquals(
            listOf(
                SetEntry.Duration(90),
                SetEntry.DistanceDuration(5000.5, 1800),
                SetEntry.Bodyweight(10.0, 8),
                SetEntry.WeightReps(80.0, 5),
            ),
            session.exercises.map { it.sets.single().entry },
        )
        assertTrue("exercises(metric_type)" in rest().single().url.encodedQuery.replace("%28", "(").replace("%29", ")"))
    }

    @Test
    fun lastPerformance_decodesTheRpeAndNull() = runTest {
        val repo = signedIn()
        rpcBody =
            """[{"set_index":1,"weight_kg":80,"reps":10,"rpe":8},""" +
            """{"set_index":2,"weight_kg":82.5,"reps":8,"rpe":null}]"""

        val sets = repo.lastPerformance("e1").getOrThrow()

        assertEquals(listOf(8.0, null), sets.map { it.rpe })
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
    fun exerciseSessions_innerEmbedsTheExerciseSetsFiltersAndSendsTheWindow() = runTest {
        val repo = signedIn()
        getBody = SESSION_ROW.replace("in_progress", "completed")

        val sessions = repo.exerciseSessions("e1", offset = 40, limit = 20).getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/workout_sessions", call.url.encodedPath)
        val query = call.url.parameters.entries().joinToString("&") { (k, v) -> "$k=${v.joinToString()}" }
        assertTrue("workout_sets!inner(" in query, query)
        assertTrue("status=eq.completed" in query, query)
        assertTrue("workout_sets.exercise_id=eq.e1" in query, query)
        assertTrue("workout_sets.completed_at=not.is.null" in query, query)
        assertTrue("order=started_at.desc" in call.url.encodedQuery && "id.desc" in call.url.encodedQuery, query)
        assertTrue("offset=40" in call.url.encodedQuery && "limit=20" in call.url.encodedQuery, query)
        assertEquals(listOf("s1"), sessions.map { it.id })
        assertEquals(SessionStatus.Completed, sessions.single().status)
    }

    @Test
    fun exerciseSessions_offline_isNetworkError() = runTest {
        val repo = signedIn()
        failing = true

        assertEquals(AppError.Network, repo.exerciseSessions("e1", 0, 20).appErrorOrNull())
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
