package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Equipment
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

    private suspend fun signedIn(): SupabaseExerciseRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
                    failing -> throw IOException("offline")
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
            """[{"name":"Press Hammer","muscle_group":"chest","equipment":"machine","is_custom":true}]""",
            (post.body as TextContent).text,
        )
        assertEquals("Press Hammer", created.name)
        assertTrue(created.isCustom)
        repo.getAll().getOrThrow()
        assertEquals(2, reads().size)
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
        assertEquals(AppError.Network, repo.createCustom("x", MuscleGroup.Back, Equipment.Band).appErrorOrNull())
    }
}
