package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val DERIVED =
    """{"as_of":"2026-10-29","activity_level":{"value":"moderate","source":"profile","provisional":true,""" +
        """"missing":["steps"]},"basal_kcal":{"value":1700,"missing":[]},"total_expenditure":{"value":2400},""" +
        """"weight_trend":{},"intake":{"logged_days":3,"window_days":28,"reliable":false},"experience":{}}"""

class SupabaseCoachRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var status = HttpStatusCode.OK
    private var offline = false
    private val today = LocalDate(2026, 10, 29)

    private suspend fun repo(signIn: Boolean = true): SupabaseCoachRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
                    offline -> throw IOException("offline")
                    else -> respond(DERIVED, status, headers)
                }
            }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseCoachRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun payload() = (rest().single().body as TextContent).text

    @Test
    fun derivedValues_callsTheRpcWithTodayAndMapsTheObject() = runTest {
        val values = repo().derivedValues(today).getOrThrow()

        assertTrue(rest().single().url.encodedPath.endsWith("/rest/v1/rpc/coach_derived_estimates"))
        assertEquals("""{"p_today":"2026-10-29"}""", payload())
        assertEquals(ActivityLevel.Moderate, values.activityLevel.value)
        assertEquals(2400.0, values.totalExpenditure.kcal)
    }

    @Test
    fun confirm_withoutLevelOmitsIt() = runTest {
        repo().confirm(today, null).getOrThrow()

        assertTrue(rest().single().url.encodedPath.endsWith("/rest/v1/rpc/confirm_derived_values"))
        assertEquals("""{"p_today":"2026-10-29"}""", payload())
    }

    @Test
    fun confirm_withLevelSendsItsWireName() = runTest {
        repo().confirm(today, ActivityLevel.VeryHigh).getOrThrow()

        assertEquals("""{"p_today":"2026-10-29","p_activity_level":"very_high"}""", payload())
    }

    @Test
    fun httpError_isFailure() = runTest {
        val repository = repo()
        status = HttpStatusCode.BadRequest

        assertTrue(repository.derivedValues(today).isFailure)
        assertTrue(repository.confirm(today, null).isFailure)
    }

    @Test
    fun offline_isNetworkError() = runTest {
        val repository = repo()
        offline = true

        assertEquals(AppError.Network, repository.derivedValues(today).appErrorOrNull())
    }

    @Test
    fun signedOut_isUnauthorizedWithoutRequests() = runTest {
        val repository = repo(signIn = false)

        assertEquals(AppError.Unauthorized, repository.derivedValues(today).appErrorOrNull())
        assertEquals(AppError.Unauthorized, repository.confirm(today, null).appErrorOrNull())
        assertTrue(rest().isEmpty())
    }
}
