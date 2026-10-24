package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""
private const val CATALOG_ROW =
    """{"id":"e1","key":"bench","name":null,"muscle_group":"chest","secondary_muscles":["triceps"],""" +
        """"equipment":"barbell","metric_type":"weight_reps","is_custom":false}"""
private const val CUSTOM_ROW =
    """{"id":"e2","key":null,"name":"Press Hammer","muscle_group":"chest","secondary_muscles":[],""" +
        """"equipment":"machine","metric_type":"weight_reps","is_custom":true}"""

class SupabaseExerciseRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var rows = "[$CATALOG_ROW]"
    private var failing = false
    private var rpcError: Pair<HttpStatusCode, String>? = null

    private fun rpcFails(status: HttpStatusCode, code: String, message: String, details: String? = null) {
        val detailsJson = details?.let { "\"$it\"" } ?: "null"
        rpcError = status to """{"code":"$code","message":"$message","details":$detailsJson,"hint":null}"""
    }

    private fun rpcPosts() = requests.filter { it.method == HttpMethod.Post && it.url.encodedPath.contains("/rpc/") }

    private suspend fun signedIn(): SupabaseExerciseRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)

                    failing -> throw IOException("offline")

                    rpcError != null && request.url.encodedPath.contains("/rpc/") ->
                        respond(rpcError!!.second, rpcError!!.first, headers)

                    request.method == HttpMethod.Get -> respond(rows, HttpStatusCode.OK, headers)

                    else -> respond("[$CUSTOM_ROW]", HttpStatusCode.Created, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseExerciseRepository(client.auth, client.postgrest)
    }

    private fun reads() = requests.filter { it.method == HttpMethod.Get && !it.url.encodedPath.contains("/auth/") }

    @Test
    fun getAll_mapsRowsToDomain() = runTest {
        val repo = signedIn()

        val exercise = repo.getAll().getOrThrow().single()

        assertEquals("bench", exercise.key)
        assertNull(exercise.name)
        assertEquals(MuscleGroup.Chest, exercise.muscleGroup)
        assertEquals(listOf(MuscleGroup.Triceps), exercise.secondaryMuscles)
        assertEquals(Equipment.Barbell, exercise.equipment)
        assertTrue(!exercise.isCustom)
    }

    @Test
    fun getAll_isCachedForTheSession() = runTest {
        val repo = signedIn()

        repo.getAll().getOrThrow()
        repo.getAll().getOrThrow()

        assertEquals(1, reads().size)
    }

    @Test
    fun createCustom_postsTheCustomRow_andInvalidatesTheCache() = runTest {
        val repo = signedIn()
        repo.getAll().getOrThrow()

        val created = repo.createCustom("  Press Hammer ", MuscleGroup.Chest, Equipment.Machine).getOrThrow()

        val post = requests.single { it.method == HttpMethod.Post && !it.url.encodedPath.contains("/auth/") }
        assertEquals(
            """[{"name":"Press Hammer","muscle_group":"chest","equipment":"machine",""" +
                """"metric_type":"weight_reps","is_custom":true,"per_hand":false}]""",
            (post.body as TextContent).text,
        )
        assertEquals("Press Hammer", created.name)
        assertTrue(created.isCustom)
        repo.getAll().getOrThrow()
        assertEquals(2, reads().size)
    }

    @Test
    fun createCustom_sendsThePerHandFlag() = runTest {
        val repo = signedIn()

        repo.createCustom("Press", MuscleGroup.Chest, Equipment.Machine, perHand = true).getOrThrow()

        val post = requests.single { it.method == HttpMethod.Post && !it.url.encodedPath.contains("/auth/") }
        assertTrue((post.body as TextContent).text.contains(""""per_hand":true"""))
    }

    @Test
    fun getAll_missingPerHandColumn_defaultsToFalse() = runTest {
        val repo = signedIn()

        assertTrue(!repo.getAll().getOrThrow().single().perHand)
    }

    @Test
    fun getAll_mapsPerHand() = runTest {
        val repo = signedIn()
        rows = "[" + CATALOG_ROW.replace("\"is_custom\":false", "\"is_custom\":false,\"per_hand\":true") + "]"

        assertTrue(repo.getAll().getOrThrow().single().perHand)
    }

    @Test
    fun updateCustom_callsTheRpcAndInvalidatesTheCache() = runTest {
        val repo = signedIn()
        repo.getAll().getOrThrow()

        repo.updateCustom("e2", "Press", MuscleGroup.Back, Equipment.Cable, perHand = true).getOrThrow()

        val call = rpcPosts().single()
        assertTrue(call.url.encodedPath.endsWith("/rpc/update_custom_exercise"))
        val body = (call.body as TextContent).text
        assertTrue(body.contains(""""p_id":"e2"""") && body.contains(""""p_per_hand":true"""))
        repo.getAll().getOrThrow()
        assertEquals(2, reads().size)
    }

    @Test
    fun updateCustom_notOwnExercise_isNotFound() = runTest {
        val repo = signedIn()
        rpcFails(HttpStatusCode.BadRequest, "P0002", "exercise not found")

        val result = repo.updateCustom("e1", "x", MuscleGroup.Back, Equipment.Cable, perHand = false)

        assertEquals(AppError.NotFound, result.appErrorOrNull())
    }

    @Test
    fun updateCustom_invalidName_isValidation() = runTest {
        val repo = signedIn()
        rpcFails(HttpStatusCode.BadRequest, "22023", "name must be 1-100 characters")

        val result = repo.updateCustom("e2", " ", MuscleGroup.Back, Equipment.Cable, perHand = false)

        assertEquals(AppError.Validation, result.appErrorOrNull())
    }

    @Test
    fun deleteCustom_callsTheRpcAndInvalidatesTheCache() = runTest {
        val repo = signedIn()
        repo.getAll().getOrThrow()

        repo.deleteCustom("e2").getOrThrow()

        assertTrue(rpcPosts().single().url.encodedPath.endsWith("/rpc/delete_custom_exercise"))
        repo.getAll().getOrThrow()
        assertEquals(2, reads().size)
    }

    @Test
    fun deleteCustom_inUse_carriesBothCounts() = runTest {
        val repo = signedIn()
        rpcFails(
            HttpStatusCode.Conflict,
            "23503",
            "exercise in use (3 sets, 2 routines)",
            "sets=3;routines=2",
        )

        assertEquals(AppError.InUse(3, 2), repo.deleteCustom("e2").appErrorOrNull())
    }

    @Test
    fun deleteCustom_notFound_isNotFound() = runTest {
        val repo = signedIn()
        rpcFails(HttpStatusCode.BadRequest, "P0002", "exercise not found")

        assertEquals(AppError.NotFound, repo.deleteCustom("zzz").appErrorOrNull())
    }

    @Test
    fun deleteCustom_inUse_keepsTheCache() = runTest {
        val repo = signedIn()
        repo.getAll().getOrThrow()
        rpcFails(HttpStatusCode.Conflict, "23503", "exercise in use (1 sets, 0 routines)", "sets=1;routines=0")

        repo.deleteCustom("e2")
        repo.getAll().getOrThrow()

        assertEquals(1, reads().size)
    }

    @Test
    fun getById_unknownId_isNotFound() = runTest {
        val repo = signedIn()
        rows = "[]"

        assertEquals(AppError.NotFound, repo.getById("zzz").appErrorOrNull())
    }

    @Test
    fun networkError_isAppErrorNetwork() = runTest {
        val repo = signedIn()
        failing = true

        assertEquals(AppError.Network, repo.getAll().appErrorOrNull())
        assertEquals(
            AppError.Network,
            repo.createCustom("x", MuscleGroup.Back, Equipment.Band, ExerciseMetricType.WeightReps).appErrorOrNull(),
        )
    }
}
