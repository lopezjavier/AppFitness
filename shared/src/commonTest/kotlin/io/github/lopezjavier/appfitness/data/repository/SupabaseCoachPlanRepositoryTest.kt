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
import io.github.lopezjavier.appfitness.domain.model.MissingField
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
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
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val NUTRITION =
    """{"target_kcal":1200,"floor_kcal":1200,"limited_by":"floor","rate_pct_week":0.5,"rate_kg_week":-0.375,""" +
        """"strategy":"deficit","protein_g":120,"carbs_g":110,"fat_g":40,"protein_pct":40,"carbs_pct":37,""" +
        """"fat_pct":23,"distribution":{"per_meal_g":30,"g_per_kg":0.4,"min_meals":4},"edited_by_user":false}"""

private const val OUTCOMES =
    """[{"kind":"weight","key":"weight","target_value":72.5,"unit":"kg","reason":"r","deadline":"2026-11-26"}]"""

private fun planRow(id: String = "p1", status: String = "proposed") =
    """{"id":"$id","user_id":"user-1","block_start":"2026-10-29","block_weeks":4,"intention":"fat_loss",""" +
        """"status":"$status","nutrition":$NUTRITION,"outcomes":$OUTCOMES,"method":{},"data_version":1,""" +
        """"accepted_at":null,"created_at":"2026-10-29T10:00:00+00:00","updated_at":"2026-10-29T10:00:00+00:00"}"""

private fun deltaFrame(text: String) =
    "event: delta\ndata: " + JsonObject(mapOf("text" to JsonPrimitive(text))) + "\n\nevent: done\ndata: {}\n\n"

private val PLAN_EVENT = """{"plan_id":"p1","recommendation_id":"r1","nutrition":$NUTRITION,"outcomes":$OUTCOMES}"""

private const val RECOMMENDATION =
    """[{"id":"c1","plan_id":"p1","kind":"calorie_change","reason":"rate_outside_interval","status":"pending",""" +
        """"payload":{"from_kcal":2000,"to_kcal":2100,"change_kcal":100,"change_pct":5.0,"slope_kg_week":-0.1,""" +
        """"interval_low":-0.2,"interval_high":0.0,"rate_kg_week":-0.4}}]"""

class SupabaseCoachPlanRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var offline = false
    private val today = LocalDate(2026, 10, 29)

    private suspend fun repo(
        signIn: Boolean = true,
        respond: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): SupabaseCoachPlanRepository {
        val engine = MockEngine { request ->
            requests += request
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
        return SupabaseCoachPlanRepository(client.auth, client.postgrest, client.functions)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun bodyOf(request: HttpRequestData) = (request.body as TextContent).text

    private fun failureOf(result: Result<*>): CoachFailure {
        val exception = result.exceptionOrNull()
        assertIs<CoachException>(exception)
        return exception.failure
    }

    private fun planRoutes(request: HttpRequestData): Pair<HttpStatusCode, String> = when {
        request.url.encodedPath.contains("/functions/") -> HttpStatusCode.OK to deltaFrame(PLAN_EVENT)
        else -> HttpStatusCode.OK to "[${planRow()}]"
    }

    @Test
    fun requestPlan_postsTheKindAndToday_thenReloadsTheStoredPlanById() = runTest {
        val repository = repo(respond = ::planRoutes)

        val plan = repository.requestPlan(today).getOrThrow()

        val call = rest().first()
        assertEquals(HttpMethod.Post, call.method)
        assertTrue(call.url.encodedPath.endsWith("/functions/v1/coach"), call.url.toString())
        assertEquals("""{"kind":"plan_nutrition","input":{"today":"2026-10-29"}}""", bodyOf(call))
        val read = rest().last()
        assertTrue(read.url.encodedPath.endsWith("/rest/v1/coach_plans"), read.url.toString())
        assertTrue(read.url.toString().contains("id=eq.p1"), read.url.toString())
        assertEquals("p1", plan.id)
        assertEquals(PlanStatus.Proposed, plan.status)
        assertEquals(1200, plan.nutrition.targetKcal)
        assertEquals(OutcomeKind.Weight, plan.outcomes.single().kind)
    }

    @Test
    fun requestPlan_userLimit_isMappedWithItsScope() = runTest {
        val repository = repo {
            HttpStatusCode.TooManyRequests to """{"error":{"code":"coach_limit_reached","scope":"user"}}"""
        }

        assertEquals(
            CoachFailure.LimitReached(CoachFailure.Scope.User),
            failureOf(repository.requestPlan(today)),
        )
    }

    @Test
    fun requestPlan_globalLimit_isMappedWithItsScope() = runTest {
        val repository = repo {
            HttpStatusCode.TooManyRequests to """{"error":{"code":"coach_limit_reached","scope":"global"}}"""
        }

        assertEquals(
            CoachFailure.LimitReached(CoachFailure.Scope.Global),
            failureOf(repository.requestPlan(today)),
        )
    }

    @Test
    fun requestPlan_noCredit_isMapped() = runTest {
        val repository = repo { HttpStatusCode.PaymentRequired to """{"error":{"code":"coach_no_credit"}}""" }

        assertEquals(CoachFailure.NoCredit, failureOf(repository.requestPlan(today)))
    }

    @Test
    fun requestPlan_invalidOutputBody_isMapped() = runTest {
        val repository = repo { HttpStatusCode.BadGateway to """{"error":{"code":"coach_invalid_output"}}""" }

        assertEquals(CoachFailure.Invalid, failureOf(repository.requestPlan(today)))
    }

    @Test
    fun requestPlan_errorFrameInTheStream_isMapped() = runTest {
        val repository = repo { HttpStatusCode.OK to "event: error\ndata: {\"code\":\"coach_plan_infeasible\"}\n\n" }

        assertEquals(CoachFailure.Infeasible, failureOf(repository.requestPlan(today)))
    }

    @Test
    fun requestPlan_missingDataBody_isMapped() = runTest {
        val repository = repo {
            HttpStatusCode.UnprocessableEntity to
                """{"error":{"code":"coach_missing_data","missing":["intention","weight"]}}"""
        }

        assertEquals(
            CoachFailure.Missing(setOf(MissingField.Intention, MissingField.Weight)),
            failureOf(repository.requestPlan(today)),
        )
    }

    @Test
    fun requestPlan_cutStream_isOther() = runTest {
        val repository = repo { HttpStatusCode.OK to "event: delta\ndata: {\"text\":\"{\"}\n\n" }

        assertEquals(CoachFailure.Other, failureOf(repository.requestPlan(today)))
    }

    @Test
    fun requestPlan_whenTheStoredPlanCannotBeFound_isOther() = runTest {
        val repository = repo { request ->
            if (request.url.encodedPath.contains("/functions/")) {
                HttpStatusCode.OK to deltaFrame(PLAN_EVENT)
            } else {
                HttpStatusCode.OK to "[]"
            }
        }

        assertEquals(CoachFailure.Other, failureOf(repository.requestPlan(today)))
    }

    @Test
    fun requestPlan_offline_isNetworkError() = runTest {
        val repository = repo(respond = ::planRoutes)
        offline = true

        assertEquals(AppError.Network, repository.requestPlan(today).appErrorOrNull())
    }

    @Test
    fun requestPlan_signedOut_isUnauthorizedWithoutRequests() = runTest {
        val repository = repo(signIn = false, respond = ::planRoutes)

        assertEquals(AppError.Unauthorized, repository.requestPlan(today).appErrorOrNull())
        assertTrue(rest().isEmpty())
    }

    @Test
    fun currentPlan_prefersTheProposedOverTheAccepted() = runTest {
        val repository = repo { HttpStatusCode.OK to "[${planRow("a", "accepted")},${planRow("p", "proposed")}]" }

        val plan = repository.currentPlan().getOrThrow()

        assertEquals("p", plan?.id)
        val url = rest().single().url.toString()
        assertTrue(url.contains("status=in."), url)
    }

    @Test
    fun currentPlan_returnsTheAcceptedOneWhenNothingIsProposed() = runTest {
        val repository = repo { HttpStatusCode.OK to "[${planRow("a", "accepted")}]" }

        assertEquals(PlanStatus.Accepted, repository.currentPlan().getOrThrow()?.status)
    }

    @Test
    fun currentPlan_isNullWithoutPlans() = runTest {
        val repository = repo { HttpStatusCode.OK to "[]" }

        assertNull(repository.currentPlan().getOrThrow())
    }

    @Test
    fun acceptedPlan_filtersByAcceptedStatus() = runTest {
        val repository = repo { HttpStatusCode.OK to "[${planRow("a", "accepted")}]" }

        assertEquals("a", repository.acceptedPlan().getOrThrow()?.id)
        assertTrue(rest().single().url.toString().contains("status=eq.accepted"))
    }

    @Test
    fun acceptPlan_callsTheRpcAndMapsTheReturnedRow() = runTest {
        val repository = repo { HttpStatusCode.OK to planRow("p1", "accepted") }

        val plan = repository.acceptPlan("p1", today).getOrThrow()

        val call = rest().single()
        assertTrue(call.url.encodedPath.endsWith("/rest/v1/rpc/accept_coach_plan"), call.url.toString())
        assertEquals("""{"p_plan_id":"p1","p_today":"2026-10-29"}""", bodyOf(call))
        assertEquals(PlanStatus.Accepted, plan.status)
    }

    @Test
    fun acceptPlan_unconfirmedValues_isNeedsConfirmation() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to
                """{"code":"55000","message":"derived values unconfirmed","details":null,"hint":null}"""
        }

        assertEquals(CoachFailure.NeedsConfirmation, failureOf(repository.acceptPlan("p1", today)))
    }

    @Test
    fun acceptPlan_planNoLongerProposed_isConflict() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to
                """{"code":"55000","message":"plan is not proposed","details":null,"hint":null}"""
        }

        assertEquals(CoachFailure.Conflict, failureOf(repository.acceptPlan("p1", today)))
    }

    @Test
    fun acceptPlan_offline_isNetworkError() = runTest {
        val repository = repo { HttpStatusCode.OK to planRow() }
        offline = true

        assertEquals(AppError.Network, repository.acceptPlan("p1", today).appErrorOrNull())
    }

    @Test
    fun discardPlan_callsTheRpcWithThePlanId() = runTest {
        val repository = repo { HttpStatusCode.NoContent to "" }

        assertTrue(repository.discardPlan("p1").isSuccess)

        val call = rest().single()
        assertTrue(call.url.encodedPath.endsWith("/rest/v1/rpc/discard_coach_plan"), call.url.toString())
        assertEquals("""{"p_plan_id":"p1"}""", bodyOf(call))
    }

    @Test
    fun pendingCalorieChange_evaluatesThenReadsThePendingRow() = runTest {
        val repository = repo { request ->
            if (request.url.encodedPath.contains("/rpc/")) {
                HttpStatusCode.OK to
                    """{"status":"created","reason":"rate_outside_interval","recommendation_id":"c1"}"""
            } else {
                HttpStatusCode.OK to RECOMMENDATION
            }
        }

        val change = repository.pendingCalorieChange(today).getOrThrow()

        val (rpc, read) = rest()
        assertTrue(rpc.url.encodedPath.endsWith("/rest/v1/rpc/evaluate_coach_calorie_change"))
        assertEquals("""{"p_today":"2026-10-29"}""", bodyOf(rpc))
        assertTrue(read.url.encodedPath.endsWith("/rest/v1/coach_recommendations"))
        assertTrue(read.url.toString().contains("kind=eq.calorie_change"), read.url.toString())
        assertTrue(read.url.toString().contains("status=eq.pending"), read.url.toString())
        assertEquals("c1", change?.id)
        assertEquals(2000, change?.fromKcal)
        assertEquals(2100, change?.toKcal)
        assertEquals("rate_outside_interval", change?.reasonCode)
    }

    @Test
    fun pendingCalorieChange_isNullWhenNothingIsPending() = runTest {
        val repository = repo { request ->
            if (request.url.encodedPath.contains("/rpc/")) {
                HttpStatusCode.OK to """{"status":"none","reason":"inside_interval"}"""
            } else {
                HttpStatusCode.OK to "[]"
            }
        }

        assertNull(repository.pendingCalorieChange(today).getOrThrow())
    }

    @Test
    fun decideCalorieChange_accept_sendsAccepted() = runTest {
        val repository = repo { HttpStatusCode.OK to "{}" }

        assertTrue(repository.decideCalorieChange("c1", accept = true, today = today).isSuccess)

        val call = rest().single()
        assertTrue(call.url.encodedPath.endsWith("/rest/v1/rpc/decide_recommendation"), call.url.toString())
        assertEquals("""{"p_id":"c1","p_status":"accepted","p_today":"2026-10-29"}""", bodyOf(call))
    }

    @Test
    fun decideCalorieChange_discard_sendsDiscarded() = runTest {
        val repository = repo { HttpStatusCode.OK to "{}" }

        assertTrue(repository.decideCalorieChange("c1", accept = false, today = today).isSuccess)

        assertEquals("""{"p_id":"c1","p_status":"discarded","p_today":"2026-10-29"}""", bodyOf(rest().single()))
    }
}
