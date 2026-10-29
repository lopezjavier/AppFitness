package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.DraftExercise
import io.github.lopezjavier.appfitness.domain.model.DraftWorkoutSet
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraft
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val AUTH_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private class NowClock(private val at: Instant) : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 22)

    override fun now(): Instant = at
}

class SupabaseSaveWorkoutTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var rpcBody = "\"w1\""
    private var rpcStatus = HttpStatusCode.OK
    private var offline = false
    private val now = Instant.parse("2026-10-22T09:00:00Z")

    private suspend fun signedIn(): Pair<SupabaseWorkoutSessionRepository, SupabaseRoutineRepository> {
        val engine = MockEngine { request ->
            requests += request
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.url.encodedPath.contains("/auth/") -> respond(AUTH_SESSION, HttpStatusCode.OK, headers)
                offline -> throw IOException("offline")
                else -> respond(rpcBody, rpcStatus, headers)
            }
        }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseWorkoutSessionRepository(client.auth, client.postgrest, NowClock(now)) { TimeZone.UTC } to
            SupabaseRoutineRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun bodyOf(request: HttpRequestData) = (request.body as TextContent).text

    private fun draft(
        date: LocalDate = LocalDate(2026, 10, 20),
        description: String? = "  Buen día ",
        gym: String? = "   ",
    ) = WorkoutDraft(
        name = " Torso ",
        description = description,
        gym = gym,
        date = date,
        routineId = "r1",
        durationMinutes = 45,
        exercises = listOf(
            DraftExercise("e1", 2, 90, listOf(DraftWorkoutSet(SetType.Working, 8.0, SetEntry.WeightReps(80.5, 5)))),
            DraftExercise("e2", 2, 60, listOf(DraftWorkoutSet(SetType.WarmUp, null, SetEntry.Duration(45)))),
        ),
    )

    @Test
    fun saveCompleted_createSendsTheNoonLocalStartAndOnlyTheColumnsOfEachMetric() = runTest {
        val (sessions, _) = signedIn()

        val id = sessions.saveCompleted(draft()).getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/save_completed_workout", call.url.encodedPath)
        assertEquals(
            """{"p_workout":{"routine_id":"r1","name":"Torso","description":"Buen día",""" +
                """"started_at":"2026-10-20T12:00:00Z","duration_s":2700,"exercises":[""" +
                """{"exercise_id":"e1","superset_group":2,"rest_seconds":90,"sets":[""" +
                """{"set_type":"working","rpe":8.0,"weight_kg":80.5,"reps":5}]},""" +
                """{"exercise_id":"e2","superset_group":2,"rest_seconds":60,"sets":[""" +
                """{"set_type":"warm_up","duration_s":45}]}]}}""",
            bodyOf(call),
        )
        assertEquals("w1", id)
    }

    @Test
    fun saveCompleted_sendsTheTrimmedSetNote_andOmitsABlankOne() = runTest {
        val (sessions, _) = signedIn()
        val noted = draft().copy(
            exercises = listOf(
                DraftExercise(
                    "e1",
                    null,
                    90,
                    listOf(
                        DraftWorkoutSet(SetType.Working, null, SetEntry.WeightReps(80.0, 5), " pausa larga "),
                        DraftWorkoutSet(SetType.Working, null, SetEntry.WeightReps(80.0, 5), "  "),
                    ),
                ),
            ),
        )

        sessions.saveCompleted(noted).getOrThrow()

        val body = bodyOf(rest().single())
        assertTrue(""""notes":"pausa larga"""" in body, body)
        assertEquals(1, Regex(""""notes":""").findAll(body).count(), body)
    }

    @Test
    fun saveCompleted_createdTodayBeforeNoonIsCappedToNow() = runTest {
        val (sessions, _) = signedIn()

        sessions.saveCompleted(draft(date = LocalDate(2026, 10, 22))).getOrThrow()

        assertTrue(bodyOf(rest().single()).contains(""""started_at":"2026-10-22T09:00:00Z""""))
    }

    @Test
    fun saveCompleted_updateCarriesTheIdAndNeverTheDateOrRoutine() = runTest {
        val (sessions, _) = signedIn()

        sessions.saveCompleted(draft(), sessionId = "w9").getOrThrow()

        val body = bodyOf(rest().single())
        assertTrue(
            body.startsWith("""{"p_workout":{"id":"w9","name":"Torso","description":"Buen día","duration_s":2700"""),
        )
        assertTrue(!body.contains("started_at"))
        assertTrue(!body.contains("routine_id"))
    }

    @Test
    fun saveCompleted_serverRejectionIsAFailure() = runTest {
        val (sessions, _) = signedIn()
        rpcStatus = HttpStatusCode.BadRequest
        rpcBody = """{"code":"22023","message":"invalid name"}"""

        assertEquals(AppError.Unknown, sessions.saveCompleted(draft()).appErrorOrNull())
    }

    @Test
    fun saveCompleted_offlineIsANetworkError() = runTest {
        val (sessions, _) = signedIn()
        offline = true

        assertEquals(AppError.Network, sessions.saveCompleted(draft()).appErrorOrNull())
    }

    @Test
    fun createFromSession_callsTheRpcWithTheTrimmedNameAndReturnsTheRoutineId() = runTest {
        val (_, routines) = signedIn()
        rpcBody = "\"r7\""

        val id = routines.createFromSession("s1", " Mi torso ").getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/save_workout_as_routine", call.url.encodedPath)
        assertEquals("""{"p_session_id":"s1","p_name":"Mi torso"}""", bodyOf(call))
        assertEquals("r7", id)
    }

    @Test
    fun createFromSession_serverRejectionIsAFailure() = runTest {
        val (_, routines) = signedIn()
        rpcStatus = HttpStatusCode.BadRequest
        rpcBody = """{"code":"22023","message":"an exercise needs 1 to 10 sets"}"""

        assertTrue(routines.createFromSession("s1", "Mi torso").isFailure)
    }
}
