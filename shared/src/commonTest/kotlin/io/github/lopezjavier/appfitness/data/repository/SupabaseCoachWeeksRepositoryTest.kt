package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.DeloadProposal
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val WEEKS =
    """[{"week":2,"start_date":"2026-11-05","end_date":"2026-11-11","phase":"upcoming","deload":true,""" +
        """"planned":1,"completed":0,"adherence_pct":0,"extra":1,"sessions":[""" +
        """{"session_index":0,"name":"Pull","routine_id":null,"session_id":null,"done":false}]},""" +
        """{"week":1,"start_date":"2026-10-29","end_date":"2026-11-04","phase":"current","deload":false,""" +
        """"planned":4,"completed":3,"adherence_pct":75,"sessions":[""" +
        """{"session_index":1,"name":"Pull","routine_id":"r2","session_id":null,"done":false},""" +
        """{"session_index":0,"name":"Push","routine_id":"r1","session_id":"w1","done":true}]},""" +
        """{"week":3,"start_date":"2026-11-12","end_date":"2026-11-18","phase":"someday","planned":0}]"""

class SupabaseCoachWeeksRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var offline = false
    private val today = LocalDate(2026, 10, 29)

    private suspend fun repo(
        signIn: Boolean = true,
        respond: () -> Pair<HttpStatusCode, String>,
    ): SupabaseCoachWeeksRepository {
        val engine = MockEngine { request ->
            requests += request
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, json)
                offline -> throw IOException("offline")
                else -> respond().let { (status, body) -> respond(body, status, json) }
            }
        }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseCoachWeeksRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun bodyOf(request: HttpRequestData) = (request.body as TextContent).text

    @Test
    fun weeks_sendsPlanAndDateAndMapsOrderedWeeksDroppingUnknownPhases() = runTest {
        val repository = repo { HttpStatusCode.OK to WEEKS }

        val weeks = repository.weeks("p1", today).getOrThrow()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/coach_plan_weeks"))
        assertEquals("""{"p_plan_id":"p1","p_today":"2026-10-29"}""", bodyOf(request))
        assertEquals(listOf(1, 2), weeks.map { it.week })
        val first = weeks.first()
        assertEquals(WeekPhase.Current, first.phase)
        assertEquals(LocalDate(2026, 10, 29), first.startDate)
        assertEquals(LocalDate(2026, 11, 4), first.endDate)
        assertEquals(4, first.planned)
        assertEquals(3, first.completed)
        assertEquals(75, first.adherencePct)
        assertEquals(listOf("Push", "Pull"), first.sessions.map { it.name })
        assertEquals(listOf(true, false), first.sessions.map { it.done })
        assertEquals("r1", first.sessions.first().routineId)
        assertTrue(weeks.last().deload)
        assertEquals(WeekPhase.Upcoming, weeks.last().phase)
        assertNull(weeks.last().sessions.single().routineId)
    }

    @Test
    fun weeks_emptyArrayIsAnEmptyList() = runTest {
        assertEquals(emptyList(), repo { HttpStatusCode.OK to "[]" }.weeks("p1", today).getOrThrow())
    }

    @Test
    fun weeks_signedOut_isUnauthorizedWithoutRequests() = runTest {
        val repository = repo(signIn = false) { HttpStatusCode.OK to "[]" }

        assertEquals(AppError.Unauthorized, repository.weeks("p1", today).appErrorOrNull())
        assertTrue(rest().isEmpty())
    }

    @Test
    fun weeks_offline_isNetworkError() = runTest {
        val repository = repo { HttpStatusCode.OK to "[]" }
        offline = true

        assertEquals(AppError.Network, repository.weeks("p1", today).appErrorOrNull())
    }

    @Test
    fun pendingDeload_createdAndPendingMapToAProposal() = runTest {
        for (status in listOf("created", "pending")) {
            val repository = repo {
                HttpStatusCode.OK to
                    """{"status":"$status","reason":"no_e1rm_improvement","recommendation_id":"d1",""" +
                    """"plan_id":"p1","week":4,"unknown":true}"""
            }

            val proposal = repository.pendingDeload(today).getOrThrow()

            assertEquals(DeloadProposal("d1", "p1", 4), proposal)
        }
        assertEquals("""{"p_today":"2026-10-29"}""", bodyOf(rest().first()))
        assertTrue(rest().first().url.encodedPath.endsWith("/rest/v1/rpc/evaluate_coach_deload"))
    }

    @Test
    fun pendingDeload_noneIsNull() = runTest {
        val repository = repo { HttpStatusCode.OK to """{"status":"none","reason":"improved"}""" }

        assertNull(repository.pendingDeload(today).getOrThrow())
    }

    @Test
    fun pendingDeload_signedOut_isUnauthorizedWithoutRequests() = runTest {
        val repository = repo(signIn = false) { HttpStatusCode.OK to "{}" }

        assertEquals(AppError.Unauthorized, repository.pendingDeload(today).appErrorOrNull())
        assertTrue(rest().isEmpty())
    }

    @Test
    fun pendingDeload_offline_isNetworkError() = runTest {
        val repository = repo { HttpStatusCode.OK to "{}" }
        offline = true

        assertEquals(AppError.Network, repository.pendingDeload(today).appErrorOrNull())
    }

    @Test
    fun decideDeload_sendsIdAndChoice() = runTest {
        val repository = repo { HttpStatusCode.OK to """{"status":"accepted","week":4}""" }

        repository.decideDeload("d1", accept = true).getOrThrow()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/decide_coach_deload"))
        assertEquals("""{"p_id":"d1","p_accept":true}""", bodyOf(request))
    }

    @Test
    fun decideDeload_alreadyDecided_isConflict() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to
                """{"code":"55000","message":"recommendation already decided","details":null,"hint":null}"""
        }

        val exception = repository.decideDeload("d1", accept = false).exceptionOrNull()

        assertIs<CoachException>(exception)
        assertEquals(CoachFailure.Conflict, exception.failure)
    }

    @Test
    fun decideDeload_offline_isNetworkError() = runTest {
        val repository = repo { HttpStatusCode.OK to "{}" }
        offline = true

        assertEquals(AppError.Network, repository.decideDeload("d1", accept = true).appErrorOrNull())
    }
}
