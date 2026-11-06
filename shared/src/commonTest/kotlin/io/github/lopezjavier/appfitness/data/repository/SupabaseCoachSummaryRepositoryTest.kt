package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.model.StallCause
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val FULL_SUMMARY =
    """{"plan_id":"p1","week":3,"start_date":"2027-01-04","end_date":"2027-01-10","seen":false,""" +
        """"sessions_done":3,"sessions_planned":4,""" +
        """"groups":[{"muscle_group":"chest","sets":10},{"muscle_group":"triceps","sets":2.5},""" +
        """{"muscle_group":"mystery","sets":1}],""" +
        """"weight":{"avg_kg":80.2,"slope_kg_week":-0.3,"interval_low":-0.5,"interval_high":-0.1,""" +
        """"target_rate_kg_week":-0.4},""" +
        """"complete_days":5,"weigh_in_days":3,"missing_records":false,""" +
        """"changes":[{"id":"c2","kind":"load_raise","exercise_id":"e1","status":"applied","reason":"x"},""" +
        """{"id":"c1","kind":"deload_scheduled","status":"applied","reason":"y"}],""" +
        """"calorie_proposal":{"id":"r1","status":"accepted","reason":"rate_outside_interval",""" +
        """"payload":{"from_kcal":2400,"to_kcal":2500,"change_pct":4.2},""" +
        """"created_at":"2027-01-08T10:00:00+00:00","decided_at":"2027-01-09T10:00:00+00:00"},""" +
        """"deload":true}"""

class SupabaseCoachSummaryRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var body = "null"
    private var status = HttpStatusCode.OK
    private var offline = false
    private val today = LocalDate(2027, 1, 11)

    private suspend fun repo(): SupabaseCoachSummaryRepository {
        val engine = MockEngine { request ->
            requests += request
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
                offline -> throw IOException("offline")
                else -> respond(body, status, headers)
            }
        }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseCoachSummaryRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    @Test
    fun getLatest_callsTheRpcWithToday() = runTest {
        body = FULL_SUMMARY

        repo().getLatest(today).getOrThrow()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/coach_week_summary"))
        assertEquals("""{"p_today":"2027-01-11"}""", (request.body as TextContent).text)
    }

    @Test
    fun getLatest_mapsEveryFigure() = runTest {
        body = FULL_SUMMARY

        val summary = assertNotNull(repo().getLatest(today).getOrThrow())

        assertEquals("p1", summary.planId)
        assertEquals(3, summary.week)
        assertEquals(LocalDate(2027, 1, 4), summary.startDate)
        assertEquals(LocalDate(2027, 1, 10), summary.endDate)
        assertFalse(summary.seen)
        assertEquals(3, summary.sessionsDone)
        assertEquals(4, summary.sessionsPlanned)
        assertEquals(5, summary.completeDays)
        assertEquals(3, summary.weighInDays)
        assertFalse(summary.missingRecords)
        assertTrue(summary.deload)
        assertEquals(80.2, summary.weight?.avgKg)
        assertEquals(-0.3, summary.weight?.slopeKgWeek)
        assertEquals(-0.4, summary.weight?.targetRateKgWeek)
    }

    @Test
    fun getLatest_keepsHalfSetsAndDropsUnknownGroups() = runTest {
        body = FULL_SUMMARY

        val groups = assertNotNull(repo().getLatest(today).getOrThrow()).groups

        assertEquals(
            listOf(MuscleGroup.Chest to 10.0, MuscleGroup.Triceps to 2.5),
            groups.map {
                it.muscleGroup to
                    it.sets
            },
        )
    }

    @Test
    fun getLatest_keepsChangesInServerOrderNewestFirst() = runTest {
        body = FULL_SUMMARY

        val changes = assertNotNull(repo().getLatest(today).getOrThrow()).changes

        assertEquals(listOf("c2", "c1"), changes.map { it.id })
        assertEquals(CoachChangeKind.DeloadScheduled, changes.last().kind)
    }

    @Test
    fun getLatest_mapsTheCalorieProposalWithItsStatus() = runTest {
        body = FULL_SUMMARY

        val proposal = assertNotNull(assertNotNull(repo().getLatest(today).getOrThrow()).calorieProposal)

        assertEquals(ProposalStatus.Applied, proposal.status)
        assertEquals(2400, proposal.proposal.fromKcal)
        assertEquals(2500, proposal.proposal.toKcal)
        assertEquals("rate_outside_interval", proposal.proposal.reasonCode)
    }

    @Test
    fun getLatest_pendingAndDiscardedProposalStatuses() = runTest {
        val statuses = listOf("pending" to ProposalStatus.Pending, "discarded" to ProposalStatus.Declined)
        val repo = repo()
        for ((code, expected) in statuses) {
            body = FULL_SUMMARY.replace("\"status\":\"accepted\"", "\"status\":\"$code\"")

            val proposal = assertNotNull(repo.getLatest(today).getOrThrow()?.calorieProposal)

            assertEquals(expected, proposal.status)
        }
    }

    private fun withStalls(stalls: String) =
        """{"plan_id":"p1","week":3,"start_date":"2027-01-04","end_date":"2027-01-10","stalls":$stalls}"""

    @Test
    fun getLatest_mapsEveryStallCause() = runTest {
        body = withStalls(
            """[{"exercise_id":"e1","cause":"sessions","avg_sessions":1.5,"plan_days":1},""" +
                """{"exercise_id":"e2","cause":"sets","avg_sets":7.0,"group":"chest"},""" +
                """{"exercise_id":"e3","cause":"maintain","group":"back"},""" +
                """{"exercise_id":"e4","cause":"protein","avg_protein_g":120,""" +
                """"protein_g_per_kg":1.4,"target_g":160},""" +
                """{"exercise_id":"e5","cause":"sleep","avg_sleep_h":6.2},""" +
                """{"exercise_id":"e6","cause":"none","protein_gate_missing":true}]""",
        )

        val stalls = assertNotNull(repo().getLatest(today).getOrThrow()).stalls

        assertEquals(
            listOf(
                "e1" to StallCause.Sessions(1.5, 1),
                "e2" to StallCause.Sets(7.0, MuscleGroup.Chest),
                "e3" to StallCause.GroupOnMaintain(MuscleGroup.Back),
                "e4" to StallCause.Protein(120, 1.4, 160),
                "e5" to StallCause.Sleep(6.2),
                "e6" to StallCause.NoClearCause(true),
            ),
            stalls.map { it.exerciseId to it.cause },
        )
    }

    @Test
    fun getLatest_missingOrNullStallsIsEmpty() = runTest {
        val repo = repo()
        for (json in listOf(FULL_SUMMARY, withStalls("null"))) {
            body = json

            assertTrue(assertNotNull(repo.getLatest(today).getOrThrow()).stalls.isEmpty())
        }
    }

    @Test
    fun getLatest_dropsStallsWithUnknownCauseOrMissingFigures() = runTest {
        body = withStalls(
            """[{"exercise_id":"e1","cause":"mystery"},{"exercise_id":"e2","cause":"sets","avg_sets":7.0},""" +
                """{"cause":"sleep","avg_sleep_h":6.0},{"exercise_id":"e3","cause":"sleep","avg_sleep_h":6.5}]""",
        )

        val stalls = assertNotNull(repo().getLatest(today).getOrThrow()).stalls

        assertEquals(listOf("e3"), stalls.map { it.exerciseId })
    }

    @Test
    fun getLatest_jsonNullIsNoSummary() = runTest {
        body = "null"

        assertNull(repo().getLatest(today).getOrThrow())
    }

    @Test
    fun getLatest_toleratesNullsAndMissingKeys() = runTest {
        body = """{"plan_id":"p1","week":2,"start_date":"2027-01-04","end_date":"2027-01-10","seen":true,""" +
            """"weight":{"avg_kg":null,"slope_kg_week":null,"interval_low":null,"interval_high":null,""" +
            """"target_rate_kg_week":null},"calorie_proposal":null,"changes":null}"""

        val summary = assertNotNull(repo().getLatest(today).getOrThrow())

        assertTrue(summary.seen)
        assertEquals(0, summary.sessionsDone)
        assertTrue(summary.groups.isEmpty())
        assertTrue(summary.changes.isEmpty())
        assertNull(summary.calorieProposal)
        assertNull(summary.weight?.avgKg)
        assertFalse(summary.deload)
    }

    @Test
    fun getLatest_withoutIdentityKeysIsNoSummary() = runTest {
        body = """{"week":2,"seen":false}"""

        assertNull(repo().getLatest(today).getOrThrow())
    }

    @Test
    fun getLatest_offlineIsAFailure() = runTest {
        val repo = repo()
        offline = true

        assertTrue(repo.getLatest(today).isFailure)
    }

    @Test
    fun markSeen_callsTheRpcWithPlanAndWeek() = runTest {
        body = ""

        repo().markSeen("p1", 3).getOrThrow()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/mark_coach_week_summary_seen"))
        assertEquals("""{"p_plan_id":"p1","p_week":3}""", (request.body as TextContent).text)
    }

    @Test
    fun markSeen_failureIsReported() = runTest {
        val repo = repo()
        status = HttpStatusCode.BadRequest
        body = """{"code":"P0002","message":"plan not found","details":null,"hint":null}"""

        assertTrue(repo.markSeen("p1", 3).isFailure)
    }
}
