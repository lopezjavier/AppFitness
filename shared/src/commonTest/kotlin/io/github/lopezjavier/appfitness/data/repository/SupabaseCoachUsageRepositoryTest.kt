package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachUsage
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val USAGE_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseCoachUsageRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var body = """[{"month":"2026-11-01","input_tokens":1200,"output_tokens":300,"cost_eur":0.42}]"""
    private var offline = false
    private val today = LocalDate(2026, 11, 17)
    private val month = LocalDate(2026, 11, 1)

    private suspend fun repo(signIn: Boolean = true): SupabaseCoachUsageRepository {
        val engine = MockEngine { request ->
            requests += request
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.url.encodedPath.contains("/auth/") -> respond(USAGE_SESSION, HttpStatusCode.OK, headers)
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
        return SupabaseCoachUsageRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    @Test
    fun currentMonth_filtersOnTheFirstDayOfTheMonthAndMapsTheRow() = runTest {
        val usage = repo().currentMonth(today).getOrThrow()

        assertTrue(rest().single().url.encodedPath.endsWith("/rest/v1/coach_usage"))
        assertEquals("eq.2026-11-01", rest().single().url.parameters["month"])
        assertEquals(CoachUsage(month, 1200, 300, 0.42), usage)
    }

    @Test
    fun currentMonth_missingRowIsEmpty() = runTest {
        body = "[]"

        assertEquals(CoachUsage.empty(month), repo().currentMonth(today).getOrThrow())
    }

    @Test
    fun currentMonth_offlineIsNetworkError() = runTest {
        val repository = repo()
        offline = true

        assertEquals(AppError.Network, repository.currentMonth(today).appErrorOrNull())
    }

    @Test
    fun currentMonth_signedOutIsUnauthorizedWithoutRequests() = runTest {
        val repository = repo(signIn = false)

        assertEquals(AppError.Unauthorized, repository.currentMonth(today).appErrorOrNull())
        assertTrue(rest().isEmpty())
    }
}
