package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
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
        """"rest_seconds":90},""" +
        """{"exercise_id":"e1","position":0,"superset_group":3,"target_sets":3,"target_reps":8,""" +
        """"rest_seconds":120}]}]"""

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

    private fun exercise(id: String, group: Int?, sets: Int, reps: Int, rest: Int) = RoutineExercise(
        id,
        position = 5,
        supersetGroup = group,
        targetSets = sets,
        targetReps = reps,
        restSeconds = rest,
    )

    private fun routine(id: String? = null, name: String = "Torso fuerza", group: Int? = null) = Routine(
        id = id,
        name = name,
        notes = null,
        archived = false,
        exercises =
        listOf(
            exercise("e1", group, sets = 3, reps = 8, rest = 120),
            exercise("e2", group, sets = 4, reps = 10, rest = 90),
        ),
    )

    @Test
    fun save_sendsAZeroBasedPayloadWithoutE6Keys_andReturnsTheId() = runTest {
        val repo = signedIn()

        val id = repo.save(routine(group = 2)).getOrThrow()

        val call = rest().single()
        assertEquals("/rest/v1/rpc/save_routine", call.url.encodedPath)
        assertEquals(
            """{"p_routine":{"name":"Torso fuerza","exercises":[""" +
                """{"exercise_id":"e1","position":0,"superset_group":2,"target_sets":3,"target_reps":8,""" +
                """"rest_seconds":120},""" +
                """{"exercise_id":"e2","position":1,"superset_group":2,"target_sets":4,"target_reps":10,""" +
                """"rest_seconds":90}]}}""",
            (call.body as TextContent).text,
        )
        assertEquals("new-id", id)
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
    fun listActive_mapsTheEmbeddedCount_andAsksForNewestFirstNonArchived() = runTest {
        val repo = signedIn()
        getBody =
            """[{"id":"r1","name":"A","routine_exercises":[{"count":3}]},""" +
            """{"id":"r2","name":"B","routine_exercises":[]}]"""

        val list = repo.listActive().getOrThrow()

        assertEquals(listOf("A" to 3, "B" to 0), list.map { it.name to it.exerciseCount })
        val query = rest().single().url.encodedQuery
        assertTrue("archived=eq.false" in query, query)
        assertTrue("order=created_at.desc" in query, query)
        assertTrue("routine_exercises(count)" in query || "routine_exercises%28count%29" in query, query)
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
        assertTrue(body.startsWith("""{"p_routine":{"name":"Torso fuerza (copia)","exercises":["""), body)
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
