package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.MissingField
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val TRAINING =
    """{"days_per_week":3,"days_chosen":true,"session_minutes":60,"rep_scheme":"hypertrophy","block_weeks":4,""" +
        """"equipment":["dumbbell"],"weekly_sets":{"chest":10},"sessions_per_muscle":{"chest":2}}"""

private fun planRow(folder: String = "null") =
    """[{"id":"p1","status":"proposed","training":$TRAINING,"folder_id":$folder}]"""

private const val EXERCISE =
    """{"exercise_id":"e1","exercise_key":"k","role":"main","muscle_group":"chest","sets":3,"rep_min":8,""" +
        """"rep_max":12,"rest_seconds":90,"reason":"r"}"""

private fun sessionRows(routine: String = "null") =
    """[{"week":1,"session_index":0,"name":"A","focus":["chest"],"exercises":[$EXERCISE],"routine_id":$routine}]"""

private fun deltaFrame(text: String) =
    "event: delta\ndata: " + JsonObject(mapOf("text" to JsonPrimitive(text))) + "\n\nevent: done\ndata: {}\n\n"

class SupabaseCoachBlockRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var offline = false
    private val today = LocalDate(2026, 10, 29)

    private suspend fun repo(
        signIn: Boolean = true,
        respond: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): SupabaseCoachBlockRepository {
        val engine = MockEngine { request ->
            requests += request
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, json)

                offline -> throw IOException("offline")

                else -> {
                    val (status, body) = respond(request)
                    val type = if (request.url.encodedPath.contains("/functions/") && status == HttpStatusCode.OK) {
                        "text/event-stream"
                    } else {
                        "application/json"
                    }
                    respond(body, status, headersOf(HttpHeaders.ContentType, type))
                }
            }
        }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseCoachBlockRepository(client.auth, client.postgrest, client.functions)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun failureOf(result: Result<*>): CoachFailure {
        val exception = result.exceptionOrNull()
        assertIs<CoachException>(exception)
        return exception.failure
    }

    private fun blockRoutes(request: HttpRequestData): Pair<HttpStatusCode, String> = when {
        request.url.encodedPath.contains("/functions/") ->
            HttpStatusCode.OK to deltaFrame("""{"plan_id":"p1","sessions":["s1"]}""")

        request.url.encodedPath.endsWith("/coach_plan_sessions") -> HttpStatusCode.OK to sessionRows()

        else -> HttpStatusCode.OK to planRow()
    }

    @Test
    fun requestBlock_postsExactlyKindTodayAndPlanId_thenReloadsFromTables() = runTest {
        val repository = repo(respond = ::blockRoutes)

        val block = repository.requestBlock("p1", today).getOrThrow()

        val call = rest().first()
        assertEquals(HttpMethod.Post, call.method)
        assertTrue(call.url.encodedPath.endsWith("/functions/v1/coach"), call.url.toString())
        assertEquals(
            """{"kind":"plan_block","input":{"today":"2026-10-29","plan_id":"p1"}}""",
            (call.body as TextContent).text,
        )
        assertEquals("p1", block.planId)
        assertEquals(3, block.training.daysPerWeek)
        assertEquals("A", block.sessions.single().name)
        assertFalse(block.saved)
    }

    @Test
    fun requestBlock_userLimit_isMappedWithItsScope() = runTest {
        val repository = repo {
            HttpStatusCode.TooManyRequests to """{"error":{"code":"coach_limit_reached","scope":"user"}}"""
        }

        assertEquals(
            CoachFailure.LimitReached(CoachFailure.Scope.User),
            failureOf(repository.requestBlock("p1", today)),
        )
    }

    @Test
    fun requestBlock_globalLimit_isMappedWithItsScope() = runTest {
        val repository = repo {
            HttpStatusCode.TooManyRequests to """{"error":{"code":"coach_limit_reached","scope":"global"}}"""
        }

        assertEquals(
            CoachFailure.LimitReached(CoachFailure.Scope.Global),
            failureOf(repository.requestBlock("p1", today)),
        )
    }

    @Test
    fun requestBlock_noCredit_isMapped() = runTest {
        val repository = repo { HttpStatusCode.PaymentRequired to """{"error":{"code":"coach_no_credit"}}""" }

        assertEquals(CoachFailure.NoCredit, failureOf(repository.requestBlock("p1", today)))
    }

    @Test
    fun requestBlock_invalidOutput_isMapped() = runTest {
        val repository = repo { HttpStatusCode.BadGateway to """{"error":{"code":"coach_invalid_output"}}""" }

        assertEquals(CoachFailure.Invalid, failureOf(repository.requestBlock("p1", today)))
    }

    @Test
    fun requestBlock_missingData_isMapped() = runTest {
        val repository = repo {
            HttpStatusCode.UnprocessableEntity to
                """{"error":{"code":"coach_missing_data","missing":["intention","place"]}}"""
        }

        assertEquals(
            CoachFailure.Missing(setOf(MissingField.Intention, MissingField.Place)),
            failureOf(repository.requestBlock("p1", today)),
        )
    }

    @Test
    fun requestBlock_cutStream_isOther() = runTest {
        val repository = repo { HttpStatusCode.OK to "event: delta\ndata: {\"text\":\"{\"}\n\n" }

        assertEquals(CoachFailure.Other, failureOf(repository.requestBlock("p1", today)))
    }

    @Test
    fun requestBlock_offline_isNetworkError() = runTest {
        val repository = repo(respond = ::blockRoutes)
        offline = true

        assertEquals(AppError.Network, repository.requestBlock("p1", today).appErrorOrNull())
    }

    @Test
    fun requestBlock_signedOut_isUnauthorizedWithoutRequests() = runTest {
        val repository = repo(signIn = false, respond = ::blockRoutes)

        assertEquals(AppError.Unauthorized, repository.requestBlock("p1", today).appErrorOrNull())
        assertTrue(rest().isEmpty())
    }

    @Test
    fun currentBlock_readsThePlanAndItsSessionsOrderedByWeekAndIndex() = runTest {
        val repository = repo(respond = ::blockRoutes)

        val block = repository.currentBlock("p1").getOrThrow()

        assertEquals("p1", block?.planId)
        val sessionsRead = rest().single { it.url.encodedPath.endsWith("/coach_plan_sessions") }.url.toString()
        assertTrue(sessionsRead.contains("plan_id=eq.p1"), sessionsRead)
        assertTrue(sessionsRead.contains("order=week.asc"), sessionsRead)
        assertTrue(sessionsRead.contains("session_index.asc"), sessionsRead)
    }

    @Test
    fun currentBlock_isNullWithoutSessions() = runTest {
        val repository = repo { request ->
            HttpStatusCode.OK to if (request.url.encodedPath.endsWith("/coach_plan_sessions")) "[]" else planRow()
        }

        assertNull(repository.currentBlock("p1").getOrThrow())
    }

    @Test
    fun currentBlock_isSavedWhenARoutineIsLinked() = runTest {
        val repository = repo { request ->
            HttpStatusCode.OK to
                if (request.url.encodedPath.endsWith(
                        "/coach_plan_sessions",
                    )
                ) {
                    sessionRows("\"r1\"")
                } else {
                    planRow("\"f1\"")
                }
        }

        assertTrue(repository.currentBlock("p1").getOrThrow()!!.saved)
    }

    @Test
    fun saveBlock_callsTheRpcAndReturnsFolderAndRoutines() = runTest {
        val repository = repo { HttpStatusCode.OK to """{"folder_id":"f1","routine_ids":["r1","r2"]}""" }

        val saved = repository.saveBlock("p1").getOrThrow()

        val call = rest().single()
        assertTrue(call.url.encodedPath.endsWith("/rest/v1/rpc/save_coach_block"), call.url.toString())
        assertEquals("""{"p_plan_id":"p1"}""", (call.body as TextContent).text)
        assertEquals("f1", saved.folderId)
        assertEquals(listOf("r1", "r2"), saved.routineIds)
    }

    @Test
    fun saveBlock_calledTwice_returnsTheSameIds() = runTest {
        val repository = repo { HttpStatusCode.OK to """{"folder_id":"f1","routine_ids":["r1"]}""" }

        assertEquals(repository.saveBlock("p1").getOrThrow(), repository.saveBlock("p1").getOrThrow())
    }

    @Test
    fun saveBlock_planNotOpen_isConflict() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to """{"code":"55000","message":"plan is not open","details":null,"hint":null}"""
        }

        assertEquals(CoachFailure.Conflict, failureOf(repository.saveBlock("p1")))
    }

    @Test
    fun saveBlock_planNotFound_staysAnUnmappedError() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to """{"code":"P0002","message":"plan not found","details":null,"hint":null}"""
        }

        val result = repository.saveBlock("p1")

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is CoachException)
    }

    @Test
    fun saveBlock_offline_isNetworkError() = runTest {
        val repository = repo { HttpStatusCode.OK to """{"folder_id":"f1","routine_ids":[]}""" }
        offline = true

        assertEquals(AppError.Network, repository.saveBlock("p1").appErrorOrNull())
    }
}
