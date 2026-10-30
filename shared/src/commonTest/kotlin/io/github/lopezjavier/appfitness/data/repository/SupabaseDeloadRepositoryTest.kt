package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.DeloadExerciseSuggestion
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
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

class SupabaseDeloadRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var body = "[]"
    private var offline = false

    private suspend fun repo(signIn: Boolean = true): SupabaseDeloadRepository {
        val engine = MockEngine { request ->
            requests += request
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
                offline -> throw IOException("offline")
                else -> respond(body, HttpStatusCode.OK, headers)
            }
        }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseDeloadRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    @Test
    fun suggestionsFor_callsTheRpcAndMapsTheExercises() = runTest {
        body = """{"week":4,"exercises":[{"exercise_id":"e1","planned_sets":5,"deload_sets":3,"x":null},""" +
            """{"exercise_id":"e2","planned_sets":1,"deload_sets":1}]}"""

        val result = repo().suggestionsFor("s1").getOrThrow()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/deload_suggestions"))
        assertEquals("""{"p_session_id":"s1"}""", (request.body as TextContent).text)
        assertEquals(listOf(DeloadExerciseSuggestion("e1", 5, 3), DeloadExerciseSuggestion("e2", 1, 1)), result)
    }

    @Test
    fun suggestionsFor_emptyArrayOutsideADeloadWeekIsEmpty() = runTest {
        assertEquals(emptyList(), repo().suggestionsFor("s1").getOrThrow())
    }

    @Test
    fun suggestionsFor_objectWithoutExercisesIsEmpty() = runTest {
        body = """{"week":4}"""

        assertEquals(emptyList(), repo().suggestionsFor("s1").getOrThrow())
    }

    @Test
    fun suggestionsFor_signedOut_isUnauthorized() = runTest {
        assertEquals(AppError.Unauthorized, repo(signIn = false).suggestionsFor("s1").appErrorOrNull())
    }

    @Test
    fun suggestionsFor_offline_isNetworkError() = runTest {
        val repository = repo()
        offline = true

        assertEquals(AppError.Network, repository.suggestionsFor("s1").appErrorOrNull())
    }
}
