package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
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
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""
private const val ROUTINE_ROW =
    """[{"id":"r1","name":"Torso fuerza","notes":null,"archived":false,"routine_exercises":[""" +
        """{"exercise_id":"e2","position":1,"superset_group":null,"target_sets":4,"target_reps":10,""" +
        """"target_weight_kg":50,"planned_set_type":"working","target_rpe":null,"rest_seconds":90,""" +
        """"routine_exercise_sets":[]},""" +
        """{"exercise_id":"e1","position":0,"superset_group":3,"target_sets":4,"target_reps":8,""" +
        """"target_weight_kg":null,"planned_set_type":"working","target_rpe":8.5,"rest_seconds":120,""" +
        """"routine_exercise_sets":[""" +
        """{"target_weight_kg":80,"target_reps":8,"set_type":"working","set_index":2},""" +
        """{"target_weight_kg":40,"target_reps":10,"set_type":"warm_up","set_index":1},""" +
        """{"target_weight_kg":80,"target_reps":8,"set_type":"working","set_index":3},""" +
        """{"target_weight_kg":null,"target_reps":6,"set_type":"failure","set_index":4}]}]}]"""

class SupabaseRoutineRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var getBody = "[]"
    private var rpcBody = "\"new-id\""
    private var failing = false

    private fun client(engine: MockEngine) = SupabaseClientFactory.create(
        AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
        engine,
        MemorySessionManager(),
    )

    private suspend fun signedIn(signIn: Boolean = true): SupabaseRoutineRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
                    failing -> throw IOException("offline")
                    request.url.encodedPath.contains("/rpc/") -> respond(rpcBody, HttpStatusCode.OK, headers)
                    request.method == HttpMethod.Get -> respond(getBody, HttpStatusCode.OK, headers)
                    else -> respond("", HttpStatusCode.NoContent)
                }
            }
        val client = client(engine)
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseRoutineRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun exercise(id: String, group: Int?, sets: List<PlannedSet>, rest: Int, rpe: Double? = null) =
        RoutineExercise(
            id,
            position = 5,
            supersetGroup = group,
            plannedSets = sets,
            restSeconds = rest,
            targetRpe = rpe,
        )

    private fun routine(id: String? = null, name: String = "Torso fuerza", group: Int? = null) = Routine(
        id = id,
        name = name,
        notes = null,
        archived = false,
        exercises =
        listOf(
            exercise(
                "e1",
                group,
                listOf(PlannedSet(40.0, 10, SetType.WarmUp), PlannedSet(null, 8, SetType.Working)),
                rest = 120,
                rpe = 8.5,
            ),
            exercise("e2", group, List(2) { PlannedSet(60.0, 10, SetType.Drop) }, rest = 90),
        ),
    )

    @Test
    fun save_sendsPlannedSetsAndRpeWithAZeroBasedPayload_andReturnsTheId() = runTest {
        val repo = signedIn()

        val id = repo.save(routine(group = 2)).getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/save_routine", call.url.encodedPath)
        assertEquals(
            """{"p_routine":{"name":"Torso fuerza","track_as_routine":true,"auto_update_routine":false,""" +
                """"exercises":[""" +
                """{"exercise_id":"e1","position":0,"superset_group":2,"rest_seconds":120,"target_rpe":8.5,""" +
                """"sets":[{"weight_kg":40.0,"reps":10,"set_type":"warm_up"},{"reps":8,"set_type":"working"}]},""" +
                """{"exercise_id":"e2","position":1,"superset_group":2,"rest_seconds":90,""" +
                """"sets":[{"weight_kg":60.0,"reps":10,"set_type":"drop"},""" +
                """{"weight_kg":60.0,"reps":10,"set_type":"drop"}]}]}}""",
            (call.body as TextContent).text,
        )
        assertEquals("new-id", id)
    }

    @Test
    fun save_sendsTheTrackingFlags() = runTest {
        val repo = signedIn()

        repo.save(routine().copy(trackAsRoutine = false, autoUpdateRoutine = true)).getOrThrow()

        val text = body(rest().single())
        assertTrue(""""track_as_routine":false,"auto_update_routine":true""" in text, text)
    }

    @Test
    fun get_mapsTheTrackingFlags() = runTest {
        val repo = signedIn()
        getBody =
            """[{"id":"r1","name":"A","track_as_routine":false,"auto_update_routine":true,"routine_exercises":[]}]"""

        val routine = repo.get("r1").getOrThrow()

        assertEquals(false to true, routine.trackAsRoutine to routine.autoUpdateRoutine)
    }

    @Test
    fun updateFromSession_callsTheRpcAndReturnsTheRoutineId() = runTest {
        val repo = signedIn()
        rpcBody = "\"r1\""

        val id = repo.updateFromSession("s1").getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/update_routine_from_session", call.url.encodedPath)
        assertEquals("""{"p_session_id":"s1"}""", body(call))
        assertEquals("r1", id)
    }

    @Test
    fun save_ofAnExistingRoutine_carriesItsId() = runTest {
        val repo = signedIn()

        repo.save(routine(id = "r1")).getOrThrow()

        assertTrue((rest().single().body as TextContent).text.startsWith("""{"p_routine":{"id":"r1","name":"""))
    }

    @Test
    fun save_signedOut_isUnauthorized() = runTest {
        val repo = signedIn(signIn = false)

        assertEquals(AppError.Unauthorized, repo.save(routine()).appErrorOrNull())
        assertTrue(rest().isEmpty())
    }

    @Test
    fun save_offline_isNetworkError() = runTest {
        val repo = signedIn()
        failing = true

        assertEquals(AppError.Network, repo.save(routine()).appErrorOrNull())
    }

    @Test
    fun listActive_mapsCountFolderAndNext_andAsksForPositionOrderNonArchived() = runTest {
        val repo = signedIn()
        getBody =
            """[{"id":"r1","name":"A","folder_id":"f1","is_next":true,"routine_exercises":[{"count":3}]},""" +
            """{"id":"r2","name":"B","folder_id":null,"is_next":false,"routine_exercises":[]}]"""

        val list = repo.listActive().getOrThrow()

        assertEquals(listOf("A" to 3, "B" to 0), list.map { it.name to it.exerciseCount })
        assertEquals(listOf("f1", null), list.map { it.folderId })
        assertEquals(listOf(true, false), list.map { it.isNext })
        val query = rest().single().url.encodedQuery
        assertTrue("archived=eq.false" in query, query)
        assertTrue(
            "order=position.asc.nullslast%2Ccreated_at.asc.nullslast" in query,
            query,
        )
        assertTrue("folder_id,is_next" in query || "folder_id%2Cis_next" in query, query)
        assertTrue("routine_exercises(count)" in query || "routine_exercises%28count%29" in query, query)
    }

    @Test
    fun listFolders_mapsRowsInPositionOrder() = runTest {
        val repo = signedIn()
        getBody = """[{"id":"f1","name":"Pierna"},{"id":"f2","name":"Empuje"}]"""

        val folders = repo.listFolders().getOrThrow()

        assertEquals(listOf("f1" to "Pierna", "f2" to "Empuje"), folders.map { it.id to it.name })
        val call = rest().single()
        assertEquals("/rest/v1/routine_folders", call.url.encodedPath)
        assertTrue("order=position.asc" in call.url.encodedQuery, call.url.encodedQuery)
    }

    private fun body(call: HttpRequestData) = (call.body as TextContent).text

    @Test
    fun createFolder_callsTheRpcAndReturnsTheId() = runTest {
        val repo = signedIn()

        val id = repo.createFolder("Pierna").getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/create_routine_folder", call.url.encodedPath)
        assertEquals("""{"p_name":"Pierna"}""", body(call))
        assertEquals("new-id", id)
    }

    @Test
    fun renameFolder_callsTheRpc() = runTest {
        val repo = signedIn()

        repo.renameFolder("f1", "Push").getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/rename_routine_folder", call.url.encodedPath)
        assertEquals("""{"p_id":"f1","p_name":"Push"}""", body(call))
    }

    @Test
    fun deleteFolder_returnsTheNumberOfDeletedRoutines() = runTest {
        val repo = signedIn()
        rpcBody = "3"

        val count = repo.deleteFolder("f1").getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/delete_routine_folder", call.url.encodedPath)
        assertEquals("""{"p_id":"f1"}""", body(call))
        assertEquals(3, count)
    }

    @Test
    fun moveToFolder_sendsAnExplicitNullForTheNoFolderGroup() = runTest {
        val repo = signedIn()

        repo.moveToFolder("r1", null).getOrThrow()
        repo.moveToFolder("r2", "f1").getOrThrow()

        val calls = rest()
        assertEquals("/rest/v1/rpc/move_routine_to_folder", calls[0].url.encodedPath)
        assertEquals("""{"p_routine_id":"r1","p_folder_id":null}""", body(calls[0]))
        assertEquals("""{"p_routine_id":"r2","p_folder_id":"f1"}""", body(calls[1]))
    }

    @Test
    fun saveOrder_sendsTheOrderedIdsAndAnExplicitNullFolder() = runTest {
        val repo = signedIn()

        repo.saveOrder(null, listOf("b", "a")).getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/save_routine_order", call.url.encodedPath)
        assertEquals("""{"p_folder_id":null,"p_routine_ids":["b","a"]}""", body(call))
    }

    @Test
    fun setNext_sendsTheIdOrAnExplicitNullToClear() = runTest {
        val repo = signedIn()

        repo.setNext("r1").getOrThrow()
        repo.setNext(null).getOrThrow()

        val calls = rest()
        assertEquals("/rest/v1/rpc/set_next_routine", calls[0].url.encodedPath)
        assertEquals("""{"p_routine_id":"r1"}""", body(calls[0]))
        assertEquals("""{"p_routine_id":null}""", body(calls[1]))
    }

    @Test
    fun folderWrites_offline_areNetworkErrors() = runTest {
        val repo = signedIn()
        failing = true

        assertEquals(AppError.Network, repo.createFolder("x").appErrorOrNull())
        assertEquals(AppError.Network, repo.deleteFolder("f1").appErrorOrNull())
        assertEquals(AppError.Network, repo.setNext("r1").appErrorOrNull())
    }

    @Test
    fun folderWrites_signedOut_areUnauthorized() = runTest {
        val repo = signedIn(signIn = false)

        assertEquals(AppError.Unauthorized, repo.saveOrder(null, listOf("a")).appErrorOrNull())
        assertTrue(rest().isEmpty())
    }

    @Test
    fun get_returnsTheExercisesInPositionOrder() = runTest {
        val repo = signedIn()
        getBody = ROUTINE_ROW

        val routine = repo.get("r1").getOrThrow()

        assertEquals(listOf("e1", "e2"), routine.exercises.map { it.exerciseId })
        assertEquals(3, routine.exercises[0].supersetGroup)
        assertEquals(90, routine.exercises[1].restSeconds)
    }

    @Test
    fun get_mapsPlannedSetsInSetIndexOrderWithTypeWeightAndRpe() = runTest {
        val repo = signedIn()
        getBody = ROUTINE_ROW

        val first = repo.get("r1").getOrThrow().exercises[0]

        assertEquals(
            listOf(
                PlannedSet(40.0, 10, SetType.WarmUp),
                PlannedSet(80.0, 8, SetType.Working),
                PlannedSet(80.0, 8, SetType.Working),
                PlannedSet(null, 6, SetType.Failure),
            ),
            first.plannedSets,
        )
        assertEquals(4, first.targetSets)
        assertEquals(8.5, first.targetRpe)
    }

    @Test
    fun get_exerciseWithoutPlannedSets_mapsTheLegacySummaryColumns() = runTest {
        val repo = signedIn()
        getBody = ROUTINE_ROW

        val legacy = repo.get("r1").getOrThrow().exercises[1]

        assertEquals(List(4) { PlannedSet(50.0, 10, SetType.Working) }, legacy.plannedSets)
    }

    @Test
    fun get_asksForTheNestedPlannedSets() = runTest {
        val repo = signedIn()

        repo.get("r1")

        val query = rest().single().url.encodedQuery
        assertTrue("routine_exercise_sets(" in query || "routine_exercise_sets%28" in query, query)
    }

    @Test
    fun get_unknownId_isNotFound() = runTest {
        val repo = signedIn()

        assertEquals(AppError.NotFound, repo.get("zzz").appErrorOrNull())
    }

    @Test
    fun duplicate_savesACopyWithoutIdNamedWithTheCopySuffix() = runTest {
        val repo = signedIn()
        getBody = ROUTINE_ROW

        val id = repo.duplicate("r1").getOrThrow()

        assertEquals("new-id", id)
        val rpc = rest().last()
        val body = (rpc.body as TextContent).text
        assertTrue(body.startsWith("""{"p_routine":{"name":"Torso fuerza (copia)","track_as_routine":true"""), body)
        assertTrue(""""position":0""" in body && """"position":1""" in body, body)
    }

    @Test
    fun duplicate_ofALongName_keepsTheResultWithinTheNameLimit() = runTest {
        val repo = signedIn()
        getBody = ROUTINE_ROW.replace("Torso fuerza", "x".repeat(100))

        repo.duplicate("r1").getOrThrow()

        val body = (rest().last().body as TextContent).text
        val name = Regex(""""name":"([^"]*)"""").find(body)!!.groupValues[1]
        assertEquals(100, name.length)
        assertTrue(name.endsWith(" (copia)"))
    }

    @Test
    fun archive_patchesTheArchivedFlagOfThatRoutine() = runTest {
        val repo = signedIn()

        repo.archive("r1").getOrThrow()

        val call = rest().single()
        assertEquals(HttpMethod.Patch, call.method)
        assertEquals("""{"archived":true}""", (call.body as TextContent).text)
        assertTrue("id=eq.r1" in call.url.encodedQuery)
    }
}
