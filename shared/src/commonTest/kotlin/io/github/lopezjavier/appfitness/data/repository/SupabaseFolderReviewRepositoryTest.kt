package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.InMemoryCoachModelPreference
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.FolderCopy
import io.github.lopezjavier.appfitness.domain.model.FolderFalta
import io.github.lopezjavier.appfitness.domain.model.FolderOp
import io.github.lopezjavier.appfitness.domain.model.ReviewPurpose
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val REVIEW_RESULT =
    """{"review_id":"r1","purpose":"review","message":"Falta un deltoide lateral","falta":[""" +
        """{"code":"H3","routine_index":1,"data":{"group":"side_delts"}}],"notes":["below_recommended"],""" +
        """"comments":[{"text":"Buen volumen"}],"changes":[{"id":"c1","op":"add_exercise","routine_index":1,""" +
        """"exercise_key":"lateral_raise","sets":[{"n":3,"type":"working","rep_min":12,"rep_max":15,"rpe":8,""" +
        """"rest_seconds":75}],"reason":"Anade elevaciones laterales","fixes":"H3"}]}"""

private const val STORED_ROW =
    """[{"id":"r1","folder_id":"f1","purpose":"review","plan_id":null,"input_hash":"h","result":{"message":"m",""" +
        """"falta":[],"notes":[],"comments":[],"changes":[]}}]"""

private const val TRAINING =
    """{"days_per_week":3,"days_chosen":true,"session_minutes":60,"rep_scheme":"hypertrophy","block_weeks":4,""" +
        """"equipment":["dumbbell"],"weekly_sets":{"chest":10},"sessions_per_muscle":{"chest":2}}"""

private const val EXERCISE =
    """{"exercise_id":"e1","exercise_key":"k","role":"main","muscle_group":"chest","sets":3,"rep_min":8,""" +
        """"rep_max":12,"rest_seconds":90,"reason":"r"}"""

private fun deltaFrame(text: String) =
    "event: delta\ndata: " + JsonObject(mapOf("text" to JsonPrimitive(text))) + "\n\nevent: done\ndata: {}\n\n"

class SupabaseFolderReviewRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var offline = false
    private val today = LocalDate(2026, 12, 28)
    private val preference = InMemoryCoachModelPreference()

    private suspend fun repo(
        signIn: Boolean = true,
        responseDelayMs: Long = 0,
        requestTimeout: kotlin.time.Duration = COACH_CLIENT_TIMEOUT,
        respond: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): SupabaseFolderReviewRepository {
        val engine = MockEngine { request ->
            requests += request
            if (responseDelayMs > 0 && request.url.encodedPath.contains("/functions/")) delay(responseDelayMs)
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, json)

                offline -> throw IOException("offline")

                else -> {
                    val (status, body) = respond(request)
                    val type = if (request.url.encodedPath.contains("/functions/") && status == HttpStatusCode.OK) {
                        "text/event-stream"
                    } else {
                        "application/json"
                    }
                    respond(body, status, headersOf(HttpHeaders.ContentType, type))
                }
            }
        }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseFolderReviewRepository(
            client.auth,
            client.postgrest,
            client.functions,
            preference,
            requestTimeout,
        )
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun failureOf(result: Result<*>): CoachFailure {
        val exception = result.exceptionOrNull()
        assertIs<CoachException>(exception)
        return exception.failure
    }

    @Test
    fun review_sendsTheFolderAndPurposeAndReturnsTheTypedReview() = runTest {
        val repository = repo { HttpStatusCode.OK to deltaFrame(REVIEW_RESULT) }

        val review = repository.review("f1", ReviewPurpose.Review, null, today).getOrThrow()

        assertEquals(
            """{"kind":"folder_review","input":{"today":"2026-12-28","folder_id":"f1","purpose":"review"}}""",
            (rest().first().body as TextContent).text,
        )
        assertEquals("r1", review.id)
        assertEquals("f1", review.folderId)
        assertEquals(listOf(FolderFalta("H3", 1, null, mapOf("group" to "side_delts"))), review.falta)
        assertEquals(listOf("below_recommended"), review.notes)
        assertIs<FolderOp.AddExercise>(review.changes.single().op)
        assertEquals("H3", review.changes.single().fixes)
    }

    @Test
    fun review_forABlock_sendsThePlanId() = runTest {
        val repository = repo { HttpStatusCode.OK to deltaFrame(REVIEW_RESULT.replace("\"review\"", "\"block\"")) }

        val review = repository.review("f1", ReviewPurpose.Block, "p1", today).getOrThrow()

        val body = (rest().first().body as TextContent).text
        assertTrue(body.contains(""""purpose":"block","plan_id":"p1""""), body)
        assertEquals(ReviewPurpose.Block, review.purpose)
    }

    @Test
    fun review_withoutAReviewId_failsAsOther() = runTest {
        val repository = repo { HttpStatusCode.OK to deltaFrame("""{"message":"m","falta":[],"changes":[]}""") }

        assertEquals(CoachFailure.Other, failureOf(repository.review("f1", ReviewPurpose.Review, null, today)))
    }

    @Test
    fun review_limitReached_isMapped() = runTest {
        val repository = repo {
            HttpStatusCode.TooManyRequests to """{"error":{"code":"coach_limit_reached","scope":"user"}}"""
        }

        assertEquals(
            CoachFailure.LimitReached(CoachFailure.Scope.User),
            failureOf(repository.review("f1", ReviewPurpose.Review, null, today)),
        )
    }

    @Test
    fun review_offline_isNetworkError() = runTest {
        val repository = repo { HttpStatusCode.OK to deltaFrame(REVIEW_RESULT) }
        offline = true

        assertEquals(AppError.Network, repository.review("f1", ReviewPurpose.Review, null, today).appErrorOrNull())
    }

    @Test
    fun review_slowerThanTheTimeout_isTimeout() = runTest {
        val repository = repo(responseDelayMs = 2_000L, requestTimeout = 50.milliseconds) {
            HttpStatusCode.OK to deltaFrame(REVIEW_RESULT)
        }

        assertEquals(CoachFailure.Timeout, failureOf(repository.review("f1", ReviewPurpose.Review, null, today)))
    }

    @Test
    fun stored_readsTheOwnRowByIdWithItsColumns() = runTest {
        val repository = repo { HttpStatusCode.OK to STORED_ROW }

        val review = repository.stored("r1").getOrThrow()

        val url = rest().first().url
        assertEquals("eq.r1", url.parameters["id"])
        assertEquals("id,folder_id,purpose,plan_id,input_hash,result", url.parameters["select"])
        assertEquals("r1", review?.id)
        assertEquals("f1", review?.folderId)
    }

    @Test
    fun stored_missingRow_isNull() = runTest {
        val repository = repo { HttpStatusCode.OK to "[]" }

        assertNull(repository.stored("r1").getOrThrow())
    }

    @Test
    fun applyCopy_sendsTheReviewAndTheChosenIds() = runTest {
        val repository = repo { HttpStatusCode.OK to """{"folder_id":"f2","routine_ids":["a","b"]}""" }

        val copy = repository.applyCopy("r1", listOf("c1", "c3")).getOrThrow()

        val call = rest().first()
        assertTrue(call.url.encodedPath.endsWith("/rpc/apply_folder_review_copy"))
        assertEquals("""{"p_review":"r1","p_change_ids":["c1","c3"]}""", (call.body as TextContent).text)
        assertEquals(FolderCopy("f2", listOf("a", "b")), copy)
    }

    @Test
    fun applyCopy_staleFolder_isConflict() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to """{"code":"55000","message":"folder changed","details":null,"hint":null}"""
        }

        assertEquals(CoachFailure.Conflict, failureOf(repository.applyCopy("r1", emptyList())))
    }

    @Test
    fun applyCopy_unknownChange_isInvalidReplacement() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to """{"code":"22023","message":"unknown change","details":null,"hint":null}"""
        }

        assertEquals(CoachFailure.InvalidReplacement, failureOf(repository.applyCopy("r1", listOf("zz"))))
    }

    private fun blockRoutes(request: HttpRequestData): Pair<HttpStatusCode, String> = when {
        request.url.encodedPath.contains("/functions/") ->
            HttpStatusCode.OK to deltaFrame("""{"plan_id":"p1","sessions":["s1"]}""")

        request.url.encodedPath.endsWith("/coach_plan_sessions") ->
            HttpStatusCode.OK to
                """[{"week":1,"session_index":0,"name":"A","focus":["chest"],"exercises":[$EXERCISE],""" +
                """"routine_id":null}]"""

        else ->
            HttpStatusCode.OK to
                """[{"id":"p1","status":"proposed","training":$TRAINING,"folder_id":null}]"""
    }

    @Test
    fun blockFromReview_sendsTheFolderStartAndReadsTheStoredBlock() = runTest {
        val repository = repo(respond = ::blockRoutes)

        val block = repository.blockFromReview("p1", today, "r1", listOf("c1")).getOrThrow()

        assertEquals(
            """{"kind":"plan_block","input":{"today":"2026-12-28","plan_id":"p1","start":"folder",""" +
                """"review_id":"r1","accepted_change_ids":["c1"]}}""",
            (rest().first().body as TextContent).text,
        )
        assertEquals("p1", block.planId)
    }

    @Test
    fun blockFromReview_needsChanges_carriesTheFailures() = runTest {
        val repository = repo {
            HttpStatusCode.UnprocessableEntity to
                """{"error":{"code":"coach_block_needs_changes","failures":[""" +
                """{"code":"H3","routine_index":1,"data":{}}]}}"""
        }

        assertEquals(
            CoachFailure.NeedsChanges(listOf(FolderFalta("H3", 1, null, emptyMap()))),
            failureOf(repository.blockFromReview("p1", today, "r1", emptyList())),
        )
    }

    @Test
    fun blockFromReview_signedOut_isUnauthorizedWithoutRequests() = runTest {
        val repository = repo(signIn = false, respond = ::blockRoutes)

        assertEquals(AppError.Unauthorized, repository.blockFromReview("p1", today, "r1", emptyList()).appErrorOrNull())
        assertTrue(rest().isEmpty())
    }
}
