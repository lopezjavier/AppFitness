package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.CoachChangeException
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFailure
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
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
    fun blockPause_sendsPlanAndDateAndMapsThePausedBlock() = runTest {
        val repository = repo {
            HttpStatusCode.OK to """{"paused":true,"paused_weeks":2,"block_end":"2026-12-06"}"""
        }

        val pause = repository.blockPause("p1", today).getOrThrow()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/coach_block_pause"))
        assertEquals("""{"p_plan_id":"p1","p_today":"2026-10-29"}""", bodyOf(request))
        assertTrue(pause.paused)
        assertEquals(2, pause.pausedWeeks)
        assertEquals(LocalDate(2026, 12, 6), pause.blockEnd)
    }

    @Test
    fun blockPause_nullBlockEndAndMissingKeysReadAsNotPaused() = runTest {
        val nullEnd = repo { HttpStatusCode.OK to """{"paused":false,"paused_weeks":0,"block_end":null}""" }
            .blockPause("p1", today).getOrThrow()
        assertEquals(BlockPause(paused = false, pausedWeeks = 0, blockEnd = null), nullEnd)

        val empty = repo { HttpStatusCode.OK to "{}" }.blockPause("p1", today).getOrThrow()
        assertEquals(BlockPause(paused = false, pausedWeeks = 0, blockEnd = null), empty)
    }

    @Test
    fun blockPause_resumedBlockKeepsThePausedWeeks() = runTest {
        val pause = repo { HttpStatusCode.OK to """{"paused":false,"paused_weeks":1,"block_end":"2026-12-06"}""" }
            .blockPause("p1", today).getOrThrow()

        assertEquals(BlockPause(paused = false, pausedWeeks = 1, blockEnd = LocalDate(2026, 12, 6)), pause)
    }

    @Test
    fun blockPause_offline_isNetworkError() = runTest {
        val repository = repo { HttpStatusCode.OK to "{}" }
        offline = true

        assertEquals(AppError.Network, repository.blockPause("p1", today).appErrorOrNull())
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

    private fun changeJson(kind: String, reason: String, week: Int = 4) =
        """{"id":"c1","kind":"$kind","exercise_id":null,"status":"applied","reason":"$reason",""" +
            """"figures":{"week":$week},"before_values":null,"after_values":null,"decision":null}"""

    @Test
    fun evaluateDeload_applied_returnsTheDeloadChangeWithoutAnExercise() = runTest {
        val repository = repo {
            HttpStatusCode.OK to
                """{"status":"applied","reason":"no_e1rm_improvement","week":4,""" +
                """"change":${changeJson("deload_triggered", "no_e1rm_improvement")}}"""
        }

        val change = repository.evaluateDeload(today).getOrThrow()

        assertEquals("c1", change?.id)
        assertEquals(CoachChangeKind.DeloadTriggered, change?.kind)
        assertNull(change?.exerciseId)
        assertEquals(CoachChangeStatus.Applied, change?.status)
        assertEquals(4, change?.figures?.week)
        assertEquals("""{"p_today":"2026-10-29"}""", bodyOf(rest().first()))
        assertTrue(rest().first().url.encodedPath.endsWith("/rest/v1/rpc/evaluate_coach_deload"))
    }

    @Test
    fun evaluateDeload_scheduledKindIsMapped() = runTest {
        val repository = repo {
            HttpStatusCode.OK to
                """{"status":"applied","week":4,"change":${changeJson(
                    "deload_scheduled",
                    "seven_weeks_without_deload",
                )}}"""
        }

        assertEquals(CoachChangeKind.DeloadScheduled, repository.evaluateDeload(today).getOrThrow()?.kind)
    }

    @Test
    fun evaluateDeload_noneIsNull() = runTest {
        val repository = repo { HttpStatusCode.OK to """{"status":"none","reason":"improved"}""" }

        assertNull(repository.evaluateDeload(today).getOrThrow())
    }

    @Test
    fun evaluateDeload_signedOut_isUnauthorizedWithoutRequests() = runTest {
        val repository = repo(signIn = false) { HttpStatusCode.OK to "{}" }

        assertEquals(AppError.Unauthorized, repository.evaluateDeload(today).appErrorOrNull())
        assertTrue(rest().isEmpty())
    }

    @Test
    fun evaluateDeload_offline_isNetworkError() = runTest {
        val repository = repo { HttpStatusCode.OK to "{}" }
        offline = true

        assertEquals(AppError.Network, repository.evaluateDeload(today).appErrorOrNull())
    }

    @Test
    fun requestDeload_callsTheRpcAndReturnsTheUserDeload() = runTest {
        val repository = repo { HttpStatusCode.OK to changeJson("deload_user", "user_request", week = 2) }

        val change = repository.requestDeload().getOrThrow()

        assertEquals(CoachChangeKind.DeloadUser, change.kind)
        assertEquals(2, change.figures.week)
        assertTrue(rest().single().url.encodedPath.endsWith("/rest/v1/rpc/request_coach_deload"))
    }

    @Test
    fun requestDeload_refusedByTheServer_isDeloadNotAvailable() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to
                """{"code":"P0001","message":"coach_deload_not_available","details":null,"hint":null}"""
        }

        val exception = repository.requestDeload().exceptionOrNull()

        assertIs<CoachChangeException>(exception)
        assertEquals(CoachChangeFailure.DeloadNotAvailable, exception.failure)
    }

    @Test
    fun requestDeload_offline_isNetworkError() = runTest {
        val repository = repo { HttpStatusCode.OK to "{}" }
        offline = true

        assertEquals(AppError.Network, repository.requestDeload().appErrorOrNull())
    }

    @Test
    fun cancelDeload_sendsTheChangeIdAndReturnsTheUndoneChange() = runTest {
        val repository = repo {
            HttpStatusCode.OK to changeJson("deload_triggered", "no_e1rm_improvement").replace("applied", "undone")
        }

        val change = repository.cancelDeload("c1").getOrThrow()

        assertEquals(CoachChangeStatus.Undone, change.status)
        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/cancel_coach_deload"))
        assertEquals("""{"p_change_id":"c1"}""", bodyOf(request))
    }

    @Test
    fun cancelDeload_secondCancel_isNotUndoable() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to
                """{"code":"P0001","message":"coach_change_not_undoable","details":null,"hint":null}"""
        }

        val exception = repository.cancelDeload("c1").exceptionOrNull()

        assertIs<CoachChangeException>(exception)
        assertEquals(CoachChangeFailure.NotUndoable, exception.failure)
    }

    @Test
    fun cancelDeload_unknownChange_isNotFound() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to
                """{"code":"P0002","message":"coach change not found","details":null,"hint":null}"""
        }

        assertEquals(AppError.NotFound, repository.cancelDeload("c1").appErrorOrNull())
    }

    @Test
    fun cancelDeload_offline_isNetworkError() = runTest {
        val repository = repo { HttpStatusCode.OK to "{}" }
        offline = true

        assertEquals(AppError.Network, repository.cancelDeload("c1").appErrorOrNull())
    }
}
