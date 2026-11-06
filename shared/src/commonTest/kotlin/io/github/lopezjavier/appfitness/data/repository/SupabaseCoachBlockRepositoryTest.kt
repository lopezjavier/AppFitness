package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.coach.builder.BuiltBlock
import io.github.lopezjavier.appfitness.domain.coach.builder.BuiltExercise
import io.github.lopezjavier.appfitness.domain.coach.builder.BuiltSession
import io.github.lopezjavier.appfitness.domain.coach.builder.CardioPlan
import io.github.lopezjavier.appfitness.domain.coach.builder.ExerciseHistory
import io.github.lopezjavier.appfitness.domain.model.BlockEdit
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
                    respond(body, status, json)
                }
            }
        }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseCoachBlockRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun failureOf(result: Result<*>): CoachFailure {
        val exception = result.exceptionOrNull()
        assertIs<CoachException>(exception)
        return exception.failure
    }

    private fun blockRoutes(request: HttpRequestData): Pair<HttpStatusCode, String> = when {
        request.url.encodedPath.endsWith("/coach_plan_sessions") -> HttpStatusCode.OK to sessionRows()
        else -> HttpStatusCode.OK to planRow()
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
        assertEquals("""{"p_plan_id":"p1","p_edits":[]}""", (call.body as TextContent).text)
        assertEquals("f1", saved.folderId)
        assertEquals(listOf("r1", "r2"), saved.routineIds)
    }

    @Test
    fun saveBlock_withEdits_sendsThemAsSessionPositionAndExercise() = runTest {
        val repository = repo { HttpStatusCode.OK to """{"folder_id":"f1","routine_ids":["r1"]}""" }

        repository.saveBlock("p1", listOf(BlockEdit(2, 3, "e9"), BlockEdit(0, 1, "e8"))).getOrThrow()

        assertEquals(
            """{"p_plan_id":"p1","p_edits":[{"session_index":2,"position":3,"exercise_id":"e9"},""" +
                """{"session_index":0,"position":1,"exercise_id":"e8"}]}""",
            (rest().single().body as TextContent).text,
        )
    }

    @Test
    fun saveBlock_invalidReplacement_isMappedToItsOwnFailure() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to
                """{"code":"22023","message":"not an alternative","details":null,"hint":null}"""
        }

        assertEquals(
            CoachFailure.InvalidReplacement,
            failureOf(repository.saveBlock("p1", listOf(BlockEdit(0, 1, "e9")))),
        )
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

    @Test
    fun removeBlock_callsTheRpcWithThePlanId() = runTest {
        val repository = repo { HttpStatusCode.OK to "" }

        assertTrue(repository.removeBlock("p1").isSuccess)

        val call = rest().single()
        assertTrue(call.url.encodedPath.endsWith("/rest/v1/rpc/remove_coach_block"), call.url.toString())
        assertEquals("""{"p_plan_id":"p1"}""", (call.body as TextContent).text)
    }

    @Test
    fun removeBlock_planNotOpen_isConflict() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to """{"code":"55000","message":"plan is not open","details":null,"hint":null}"""
        }

        assertEquals(CoachFailure.Conflict, failureOf(repository.removeBlock("p1")))
    }

    @Test
    fun removeBlock_planNotFound_staysAnUnmappedError() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to """{"code":"22023","message":"plan not found","details":null,"hint":null}"""
        }

        val result = repository.removeBlock("p1")

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is CoachException)
    }

    @Test
    fun removeBlock_offline_isNetworkError() = runTest {
        val repository = repo { HttpStatusCode.OK to "" }
        offline = true

        assertEquals(AppError.Network, repository.removeBlock("p1").appErrorOrNull())
    }

    @Test
    fun removeBlock_signedOut_isUnauthorizedWithoutRequests() = runTest {
        val repository = repo(signIn = false) { HttpStatusCode.OK to "" }

        assertEquals(AppError.Unauthorized, repository.removeBlock("p1").appErrorOrNull())
        assertTrue(rest().isEmpty())
    }

    private fun builtBlock() = BuiltBlock(
        daysPerWeek = 1,
        intention = TrainingIntention.Muscle,
        equipment = listOf(Equipment.Barbell),
        sessionMinutes = 60,
        routineTotalMin = listOf(100),
        cardio = CardioPlan.of(150, 1).copy(perRoutineMin = listOf(40)),
        source = BuiltBlock.SOURCE_BUILDER,
        sessions = listOf(
            BuiltSession(
                "Cuerpo completo",
                listOf("chest"),
                listOf(
                    BuiltExercise("main", "e1", 3, 8, 12, 120, 8, 62.5, 1, null, null),
                    BuiltExercise("isolation", "e2", 3, 8, 12, 120, 8, null, null, null, null),
                    BuiltExercise.cardio("c1", 40),
                ),
            ),
        ),
    )

    @Test
    fun saveBuiltBlock_callsTheRpcWithTrainingSessionsAndFolderName_omittingAbsentValues() = runTest {
        val repository = repo { HttpStatusCode.OK to """{"folder_id":"f1","routine_ids":["r1"]}""" }

        val saved = repository.saveBuiltBlock("p1", builtBlock(), "Mi bloque").getOrThrow()

        val call = rest().single()
        assertTrue(call.url.encodedPath.endsWith("/rest/v1/rpc/save_built_coach_block"), call.url.toString())
        val body = Json.parseToJsonElement((call.body as TextContent).text).jsonObject
        assertEquals("p1", body.getValue("p_plan_id").jsonPrimitive.content)
        assertEquals("Mi bloque", body.getValue("p_folder_name").jsonPrimitive.content)
        val training = body.getValue("p_training").jsonObject
        assertEquals("builder", training.getValue("source").jsonPrimitive.content)
        assertEquals("muscle", training.getValue("intention").jsonPrimitive.content)
        assertEquals(4, training.getValue("block_weeks").jsonPrimitive.int)
        assertEquals("barbell", training.getValue("equipment").jsonArray.single().jsonPrimitive.content)
        assertEquals("suave", training.getValue("cardio").jsonObject.getValue("pace").jsonPrimitive.content)
        val exercises = body.getValue("p_sessions").jsonArray.single().jsonObject.getValue("exercises").jsonArray
        val first = exercises[0].jsonObject
        assertEquals(62.5, first.getValue("planned_weight_kg").jsonPrimitive.double)
        assertEquals(1, first.getValue("superset_group").jsonPrimitive.int)
        assertEquals(8, first.getValue("target_rpe").jsonPrimitive.int)
        val second = exercises[1].jsonObject
        assertFalse("planned_weight_kg" in second)
        assertFalse("superset_group" in second)
        val cardio = exercises[2].jsonObject
        assertEquals(40, cardio.getValue("duration_min").jsonPrimitive.int)
        assertFalse("rep_min" in cardio)
        assertEquals("f1", saved.folderId)
    }

    @Test
    fun saveBuiltBlock_withoutFolderName_omitsTheParameter() = runTest {
        val repository = repo { HttpStatusCode.OK to """{"folder_id":"f1","routine_ids":[]}""" }

        repository.saveBuiltBlock("p1", builtBlock()).getOrThrow()

        val body = Json.parseToJsonElement((rest().single().body as TextContent).text).jsonObject
        assertFalse("p_folder_name" in body)
        assertFalse("p_previous_plan_id" in body)
    }

    @Test
    fun saveBuiltBlock_nextBlock_sendsThePreviousPlanAndReadsTheNewPlanId() = runTest {
        val repository = repo { HttpStatusCode.OK to """{"folder_id":"f1","routine_ids":["r1"],"plan_id":"p2"}""" }

        val saved = repository.saveBuiltBlock("p1", builtBlock(), previousPlanId = "p0").getOrThrow()

        val body = Json.parseToJsonElement((rest().single().body as TextContent).text).jsonObject
        assertEquals("p0", body.getValue("p_previous_plan_id").jsonPrimitive.content)
        assertEquals("p2", saved.planId)
    }

    @Test
    fun saveBuiltBlock_previousBlockNotAccepted_isConflict() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to
                """{"code":"55000","message":"previous block is not the accepted block","details":null,"hint":null}"""
        }

        val result = repository.saveBuiltBlock("p1", builtBlock(), previousPlanId = "p0")

        assertEquals(CoachFailure.Conflict, failureOf(result))
    }

    @Test
    fun saveBuiltBlock_planNotOpen_isConflict() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to """{"code":"55000","message":"plan is not open","details":null,"hint":null}"""
        }

        assertEquals(CoachFailure.Conflict, failureOf(repository.saveBuiltBlock("p1", builtBlock())))
    }

    @Test
    fun saveBuiltBlock_derivedValuesUnconfirmed_needsConfirmation() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to
                """{"code":"55000","message":"derived values unconfirmed","details":null,"hint":null}"""
        }

        val result = repository.saveBuiltBlock("p2", builtBlock(), previousPlanId = "p1")

        assertEquals(CoachFailure.NeedsConfirmation, failureOf(result))
    }

    @Test
    fun saveBuiltBlock_invariantRefused_isInvalidBlock() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to
                """{"code":"22023","message":"block invariant: h6_equipment","details":null,"hint":null}"""
        }

        assertEquals(CoachFailure.InvalidBlock, failureOf(repository.saveBuiltBlock("p1", builtBlock())))
    }

    @Test
    fun saveBuiltBlock_otherServerError_staysAnUnmappedError() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to """{"code":"P0002","message":"plan not found","details":null,"hint":null}"""
        }

        val result = repository.saveBuiltBlock("p1", builtBlock())

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is CoachException)
    }

    @Test
    fun saveBuiltBlock_offline_isNetworkError() = runTest {
        val repository = repo { HttpStatusCode.OK to "{}" }
        offline = true

        assertEquals(AppError.Network, repository.saveBuiltBlock("p1", builtBlock()).appErrorOrNull())
    }

    @Test
    fun loadBuilderInputs_decodesHistoryCardioOptionsAndDefault() = runTest {
        val repository = repo {
            HttpStatusCode.OK to
                """{"history":[{"exercise_id":"e1","sessions_8w":3,"last_weight_kg":60.5,"last_reps":9},""" +
                """{"exercise_id":"e2","sessions_8w":1,"last_weight_kg":null,"last_reps":null,"extra":1}],""" +
                """"cardio_options":["c1","c2"],"cardio_default":"c2"}"""
        }

        val inputs = repository.loadBuilderInputs(today).getOrThrow()

        val call = rest().single()
        assertTrue(call.url.encodedPath.endsWith("/rest/v1/rpc/coach_builder_inputs"), call.url.toString())
        assertEquals("""{"p_today":"2026-10-29"}""", (call.body as TextContent).text)
        assertEquals(ExerciseHistory(3, 60.5, 9), inputs.history["e1"])
        assertEquals(ExerciseHistory(1, null, null), inputs.history["e2"])
        assertEquals(listOf("c1", "c2"), inputs.cardioOptions)
        assertEquals("c2", inputs.cardioDefault)
    }

    @Test
    fun loadBuilderInputs_emptyAnswer_isEmptyInputs() = runTest {
        val repository = repo { HttpStatusCode.OK to """{"history":[],"cardio_options":[],"cardio_default":null}""" }

        val inputs = repository.loadBuilderInputs(today).getOrThrow()

        assertTrue(inputs.history.isEmpty())
        assertNull(inputs.cardioDefault)
    }

    @Test
    fun loadBuilderInputs_offline_isNetworkError() = runTest {
        val repository = repo { HttpStatusCode.OK to "{}" }
        offline = true

        assertEquals(AppError.Network, repository.loadBuilderInputs(today).appErrorOrNull())
    }
}
