package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ExerciseOptions
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseExerciseOptionsRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var body = "[]"
    private var status = HttpStatusCode.OK
    private var failing = false

    private suspend fun repository(signIn: Boolean = true): SupabaseExerciseOptionsRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
                    failing -> throw IOException("offline")
                    else -> respond(ByteReadChannel(body), status, headers)
                }
            }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseExerciseOptionsRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    @Test
    fun get_mapsTheEnabledExerciseIds() = runTest {
        body = """[{"exercise_id":"e1"},{"exercise_id":"e2"}]"""

        val result = repository().get().getOrThrow()

        assertEquals(ExerciseOptions(setOf("e1", "e2")), result)
        assertTrue(result.isDoubleDumbbell("e1"))
        val query = rest().single().url.toString()
        assertTrue(query.contains("/rest/v1/exercise_options"), query)
        assertTrue(query.contains("double_dumbbell=eq.true"), query)
    }

    @Test
    fun get_withoutRowsIsTheDefault() = runTest {
        assertEquals(ExerciseOptions(), repository().get().getOrThrow())
    }

    @Test
    fun setDoubleDumbbell_callsTheRpcWithItsArguments() = runTest {
        body = "null"

        repository().setDoubleDumbbell("e1", true).getOrThrow()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rpc/set_exercise_double_dumbbell"), request.url.toString())
        assertEquals(
            """{"p_exercise_id":"e1","p_enabled":true}""",
            (request.body as io.ktor.http.content.TextContent).text,
        )
    }

    @Test
    fun get_signedOutIsUnauthorized() = runTest {
        assertEquals(AppError.Unauthorized, repository(signIn = false).get().appErrorOrNull())
    }

    @Test
    fun get_offlineIsNetworkError() = runTest {
        val repo = repository()
        failing = true

        assertEquals(AppError.Network, repo.get().appErrorOrNull())
        assertEquals(AppError.Network, repo.setDoubleDumbbell("e1", true).appErrorOrNull())
    }

    @Test
    fun setDoubleDumbbell_serverRejectionIsAnError() = runTest {
        status = HttpStatusCode.BadRequest
        body = """{"code":"22023","message":"double dumbbell applies to dumbbell exercises only"}"""

        assertTrue(repository().setDoubleDumbbell("e1", true).isFailure)
    }
}
