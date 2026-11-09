package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.data.remote.blockReviewOf
import io.github.lopezjavier.appfitness.data.remote.weekSummaryOf
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.PastReport
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.ReportKind
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
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val WEEK_SNAPSHOT =
    """{"plan_id":"p1","week":3,"start_date":"2027-01-04","end_date":"2027-01-10",""" +
        """"sessions_done":3,"sessions_planned":4,"groups":[{"muscle_group":"chest","sets":10}],""" +
        """"complete_days":5,"weigh_in_days":3,"deload":true}"""

private const val PAUSED_SNAPSHOT =
    """{"plan_id":"p1","paused":true,"start_date":"2027-01-11","end_date":"2027-01-17"}"""

private const val BLOCK_SNAPSHOT =
    """{"id":"r1","plan_id":"p1","ended_on":"2027-02-01","inputs":{"adherence":{"completed":20,"planned":24,""" +
        """"pct":83}},"phase_proposal":{"to_phase":"maintenance","reason":"cut_length","figures":{"weeks":12}},""" +
        """"phase_decision":"accepted","decided_at":"2027-02-02T10:00:00+00:00","next_plan_id":"p2",""" +
        """"seen_at":"2027-02-02T09:00:00+00:00"}"""

private fun row(
    id: String,
    kind: String,
    snapshot: String? = null,
    week: String = "3",
    paused: Boolean = false,
    decision: String = "null",
): String {
    val base = """"id":"$id","kind":"$kind","plan_id":"p1","week":$week,"paused":$paused,""" +
        """"period_start":"2027-01-04","period_end":"2027-01-10","created_at":"2027-01-11T08:00:00+00:00""""
    val tail = if (snapshot == null) ""","phase_decision":$decision""" else ""","snapshot":$snapshot"""
    return "{$base$tail}"
}

class SupabaseReportRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var body = "null"
    private var status = HttpStatusCode.OK
    private var offline = false
    private var signedIn = true

    private suspend fun repo(): SupabaseReportRepository {
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
        if (signedIn) {
            SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        }
        return SupabaseReportRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    @Test
    fun list_callsTheRpcWithoutParameters() = runTest {
        body = "[]"

        repo().list().getOrThrow()

        assertTrue(rest().single().url.encodedPath.endsWith("/rest/v1/rpc/list_coach_reports"))
    }

    @Test
    fun list_keepsTheServerOrderAndMapsEveryKind() = runTest {
        body = "[" + listOf(
            row("b2", "block_review", week = "null", decision = "\"kept\""),
            row("w2", "week_summary"),
            row("w1", "week_summary", week = "null", paused = true),
        ).joinToString(",") + "]"

        val items = repo().list().getOrThrow()

        assertEquals(listOf("b2", "w2", "w1"), items.map { it.id })
        assertEquals(ReportKind.BlockReview, items[0].kind)
        assertEquals(PhaseDecision.Kept, items[0].phaseDecision)
        assertNull(items[0].week)
        assertEquals(ReportKind.WeekSummary, items[1].kind)
        assertEquals(3, items[1].week)
        assertNull(items[1].phaseDecision)
        assertTrue(items[2].paused)
        assertEquals("p1", items[1].planId)
        assertEquals(LocalDate(2027, 1, 4), items[1].periodStart)
        assertEquals(LocalDate(2027, 1, 10), items[1].periodEnd)
        assertEquals(Instant.parse("2027-01-11T08:00:00Z"), items[1].createdAt)
    }

    @Test
    fun list_dropsUnknownKindsAndRowsWithoutIdentity() = runTest {
        body = "[" + listOf(
            row("x1", "mystery"),
            """{"kind":"week_summary","plan_id":"p1"}""",
            row("w1", "week_summary"),
        ).joinToString(",") + "]"

        assertEquals(listOf("w1"), repo().list().getOrThrow().map { it.id })
    }

    @Test
    fun list_emptyAndNullAreEmpty() = runTest {
        val repo = repo()
        for (json in listOf("[]", "null")) {
            body = json

            assertTrue(repo.list().getOrThrow().isEmpty())
        }
    }

    @Test
    fun list_offlineIsANetworkFailure() = runTest {
        val repo = repo()
        offline = true

        val failure = repo.list().exceptionOrNull()

        assertEquals(AppError.Network, assertIs<AppException>(failure).error)
    }

    @Test
    fun list_signedOutIsUnauthorized() = runTest {
        signedIn = false

        val failure = repo().list().exceptionOrNull()

        assertEquals(AppError.Unauthorized, assertIs<AppException>(failure).error)
    }

    @Test
    fun get_callsTheRpcWithTheId() = runTest {
        body = "null"

        repo().get("w1").getOrThrow()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/get_coach_report"))
        assertEquals("""{"p_id":"w1"}""", (request.body as TextContent).text)
    }

    @Test
    fun get_weekSnapshotMapsToTheSameSummaryAsTheLiveReader() = runTest {
        body = row("w1", "week_summary", snapshot = WEEK_SNAPSHOT)

        val report = assertIs<PastReport.Week>(assertNotNull(repo().get("w1").getOrThrow()))

        assertEquals(weekSummaryOf(Json.parseToJsonElement(WEEK_SNAPSHOT)), report.summary)
        assertEquals("w1", report.item.id)
        assertEquals(ReportKind.WeekSummary, report.item.kind)
    }

    @Test
    fun get_pausedWeekSnapshotIsAPausedSummary() = runTest {
        body = row("w1", "week_summary", snapshot = PAUSED_SNAPSHOT, week = "null", paused = true)

        val report = assertIs<PastReport.Week>(assertNotNull(repo().get("w1").getOrThrow()))

        assertTrue(report.summary.paused)
        assertTrue(report.item.paused)
    }

    @Test
    fun get_blockSnapshotMapsToTheSameReviewAsTheLiveReader() = runTest {
        body = row("b1", "block_review", snapshot = BLOCK_SNAPSHOT, week = "null")

        val report = assertIs<PastReport.Block>(assertNotNull(repo().get("b1").getOrThrow()))

        assertEquals(blockReviewOf(Json.parseToJsonElement(BLOCK_SNAPSHOT)), report.review)
        assertEquals(PhaseDecision.Accepted, report.review.phaseDecision)
    }

    @Test
    fun get_jsonNullIsNoReport() = runTest {
        body = "null"

        assertNull(repo().get("w1").getOrThrow())
    }

    @Test
    fun get_unknownKindOrUnreadableBodyIsNoReport() = runTest {
        val repo = repo()
        for (json in listOf(
            row("x1", "mystery", snapshot = WEEK_SNAPSHOT),
            row("w1", "week_summary", snapshot = """{"week":3}"""),
            row("b1", "block_review", snapshot = """{"id":"r1"}"""),
            row("w2", "week_summary", snapshot = "null"),
        )) {
            body = json

            assertNull(repo.get("x").getOrThrow())
        }
    }

    @Test
    fun get_offlineIsANetworkFailure() = runTest {
        val repo = repo()
        offline = true

        assertEquals(AppError.Network, assertIs<AppException>(repo.get("w1").exceptionOrNull()).error)
    }

    @Test
    fun recordPausedWeek_callsTheRpcWithToday() = runTest {
        body = "null"

        repo().recordPausedWeek(LocalDate(2027, 1, 11)).getOrThrow()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/record_paused_week_report"))
        assertEquals("""{"p_today":"2027-01-11"}""", (request.body as TextContent).text)
    }

    @Test
    fun recordPausedWeek_failureIsReported() = runTest {
        val repo = repo()
        status = HttpStatusCode.BadRequest
        body = """{"code":"P0002","message":"x","details":null,"hint":null}"""

        assertTrue(repo.recordPausedWeek(LocalDate(2027, 1, 11)).isFailure)
    }
}
