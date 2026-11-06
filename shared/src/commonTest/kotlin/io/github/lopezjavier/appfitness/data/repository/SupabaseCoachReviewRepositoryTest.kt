package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
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
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val REVIEW_ROW =
    """{"id":"r1","plan_id":"p1","ended_on":"2027-01-05","inputs":{"adherence":{"completed":3,"planned":4,""" +
        """"pct":75}},"phase_proposal":{"to_phase":"maintenance","reason":"floor_limited","figures":{}},""" +
        """"phase_decision":null,"decided_at":null,"next_plan_id":null}"""

private fun frame(text: String) =
    "event: delta\ndata: {\"text\":${kotlinx.serialization.json.JsonPrimitive(text)}}\n\nevent: done\ndata: {}\n\n"

class SupabaseCoachReviewRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val today = LocalDate(2027, 1, 6)

    private suspend fun repo(
        signIn: Boolean = true,
        respond: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): SupabaseCoachReviewRepository {
        val engine = MockEngine { request ->
            requests += request
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            if (request.url.encodedPath.contains("/auth/")) {
                respond(SESSION, HttpStatusCode.OK, json)
            } else {
                val (status, body) = respond(request)
                val type = if (request.url.encodedPath.contains("/functions/") && status == HttpStatusCode.OK) {
                    "text/event-stream"
                } else {
                    "application/json"
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, type))
            }
        }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseCoachReviewRepository(client.auth, client.postgrest, client.functions)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun bodyOf(request: HttpRequestData) = (request.body as TextContent).text

    @Test
    fun openReview_callsTheRpcWithToday_andMapsTheRow() = runTest {
        val repository = repo { HttpStatusCode.OK to REVIEW_ROW }

        val review = assertNotNull(repository.openReview(today).getOrThrow())

        val call = rest().single()
        assertEquals(HttpMethod.Post, call.method)
        assertTrue(call.url.encodedPath.endsWith("/rest/v1/rpc/open_coach_block_review"), call.url.toString())
        assertEquals("""{"p_today":"2027-01-06"}""", bodyOf(call))
        assertEquals("r1", review.id)
        assertEquals(75, review.adherence.pct)
        assertNotNull(review.phaseProposal)
    }

    @Test
    fun openReview_nullAnswer_isNoReview() = runTest {
        val repository = repo { HttpStatusCode.OK to "null" }

        assertNull(repository.openReview(today).getOrThrow())
    }

    @Test
    fun openReview_signedOut_isUnauthorized() = runTest {
        val repository = repo(signIn = false) { HttpStatusCode.OK to "null" }

        assertEquals(AppError.Unauthorized, repository.openReview(today).appErrorOrNull())
        assertTrue(rest().isEmpty())
    }

    @Test
    fun keepPhase_callsTheRpcWithTheReviewId() = runTest {
        val repository = repo { HttpStatusCode.OK to REVIEW_ROW }

        repository.keepPhase("r1").getOrThrow()

        val call = rest().single()
        assertTrue(call.url.encodedPath.endsWith("/rest/v1/rpc/keep_coach_phase"), call.url.toString())
        assertEquals("""{"p_review_id":"r1"}""", bodyOf(call))
    }

    @Test
    fun acceptPhase_postsTheKindWithReviewIdAndToday_andReturnsThePlanId() = runTest {
        val repository = repo { HttpStatusCode.OK to frame("""{"plan_id":"p2","nutrition":{},"outcomes":[]}""") }

        val planId = repository.acceptPhase("r1", today).getOrThrow()

        val call = rest().single()
        assertEquals(HttpMethod.Post, call.method)
        assertTrue(call.url.encodedPath.endsWith("/functions/v1/coach"), call.url.toString())
        assertEquals(
            """{"kind":"plan_phase_change","input":{"today":"2027-01-06","review_id":"r1"}}""",
            bodyOf(call),
        )
        assertEquals("p2", planId)
    }

    @Test
    fun acceptPhase_errorBody_isACoachFailure() = runTest {
        val repository = repo { HttpStatusCode.BadGateway to """{"error":{"code":"coach_invalid_output"}}""" }

        val failure = repository.acceptPhase("r1", today).exceptionOrNull()

        assertIs<CoachException>(failure)
        assertEquals(CoachFailure.Invalid, failure.failure)
    }

    @Test
    fun acceptPhase_streamWithoutPlanId_isOther() = runTest {
        val repository = repo { HttpStatusCode.OK to frame("{}") }

        val failure = repository.acceptPhase("r1", today).exceptionOrNull()

        assertIs<CoachException>(failure)
        assertEquals(CoachFailure.Other, failure.failure)
    }

    @Test
    fun acceptPhase_signedOut_isUnauthorized() = runTest {
        val repository = repo(signIn = false) { HttpStatusCode.OK to frame("{}") }

        assertEquals(AppError.Unauthorized, repository.acceptPhase("r1", today).appErrorOrNull())
    }
}
