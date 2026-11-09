package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.InMemoryCoachModelPreference
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.model.CoachModelChoice
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

/**
 * E16 acceptance criterion 6: the weekly summary with its nutrition diagnosis and plateau, and the diet-break toggle,
 * make no model call. The summary is a table read; the toggle may reach the `coach` function only as the deterministic
 * `plan_diet_break` kind, never with a model key (the function records no usage row for it), even when a paid model is
 * selected.
 */
class E16FlowsMakeNoModelCallTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val today = LocalDate(2027, 1, 6)

    @Test
    fun summaryDiagnosisAndDietBreakToggle_sendOnlyTheDeterministicKindWithoutAModelKey() = runTest {
        val engine = MockEngine { request ->
            requests += request
            val body = if (request.url.encodedPath.contains("/auth/")) SESSION else "null"
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        val preference = InMemoryCoachModelPreference().also { it.select(CoachModelChoice.Sonnet) }
        val plans = SupabaseCoachPlanRepository(client.auth, client.postgrest, client.functions, preference)
        val summary = SupabaseCoachSummaryRepository(client.auth, client.postgrest)

        summary.getLatest(today)
        summary.markSeen("p1", 4)
        plans.setDietBreak("p1", today, true)
        plans.setDietBreak("p1", today, false)
        plans.applyDietBreak(today)

        val functionCalls = requests.filter { it.url.encodedPath.contains("/functions/") }
        val bodies = functionCalls.map { (it.body as TextContent).text }
        assertEquals(2, bodies.size, bodies.toString())
        assertTrue(bodies.all { it.contains(""""kind":"plan_diet_break"""") }, bodies.toString())
        assertTrue(bodies.none { it.contains(""""model"""") }, bodies.toString())
    }
}
