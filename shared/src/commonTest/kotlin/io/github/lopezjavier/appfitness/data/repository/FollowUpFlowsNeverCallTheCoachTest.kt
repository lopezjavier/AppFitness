package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.InMemoryCoachModelPreference
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.coach.builder.BuiltBlock
import io.github.lopezjavier.appfitness.domain.coach.builder.BuiltExercise
import io.github.lopezjavier.appfitness.domain.coach.builder.BuiltSession
import io.github.lopezjavier.appfitness.domain.coach.builder.CardioPlan
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

/**
 * E14 acceptance criterion: the follow-up flows (load change and undo, calorie proposal, deload, weekly summary,
 * block review, next block) run on RPCs and tables only. Whatever the server answers, none of them may reach the
 * `coach` Edge Function. Excluded on purpose: the plan request and `acceptPhase` (the deterministic
 * `plan_phase_change` kind, not a model call), which the user starts from the review.
 */
class FollowUpFlowsNeverCallTheCoachTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val today = LocalDate(2027, 1, 6)

    @Test
    fun theFollowUpAndNextBlockFlowsMakeNoFunctionCall() = runTest {
        val engine = MockEngine { request ->
            requests += request
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            val body = if (request.url.encodedPath.contains("/auth/")) SESSION else "null"
            respond(body, HttpStatusCode.OK, json)
        }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        val changes = SupabaseCoachChangesRepository(client.auth, client.postgrest)
        val plans =
            SupabaseCoachPlanRepository(client.auth, client.postgrest, client.functions, InMemoryCoachModelPreference())
        val weeks = SupabaseCoachWeeksRepository(client.auth, client.postgrest)
        val summary = SupabaseCoachSummaryRepository(client.auth, client.postgrest)
        val review = SupabaseCoachReviewRepository(client.auth, client.postgrest, client.functions)
        val blocks = SupabaseCoachBlockRepository(client.auth, client.postgrest)

        changes.apply("s1")
        changes.list("p1", emptySet())
        changes.undo("c1")
        changes.decide("c1", ProgressionFlagDecision.Keep)
        plans.pendingCalorieProposal(today)
        plans.decideCalorieProposal("r1", apply = true, today = today)
        plans.currentPlan()
        plans.acceptedPlan()
        weeks.evaluateDeload(today)
        weeks.cancelDeload("c1")
        summary.getLatest(today)
        summary.markSeen("p1", 1)
        review.openReview(today)
        review.keepPhase("r1")
        blocks.currentBlock("p1")
        blocks.loadBuilderInputs(today)
        blocks.saveBuiltBlock("p2", nextBlock(), "Bloque", previousPlanId = "p1")
        SupabaseDiscomfortRepository(client.auth, client.postgrest).unresolved(setOf("e1"))
        SupabaseProfileRepository(client.auth, client.postgrest).getBiometrics()

        val calls = requests.filter { !it.url.encodedPath.contains("/auth/") }
        assertTrue(calls.isNotEmpty())
        assertTrue(
            calls.none {
                it.url.encodedPath.contains("/functions/")
            },
            calls.map { it.url.encodedPath }.toString(),
        )
    }

    private fun nextBlock() = BuiltBlock(
        daysPerWeek = 1,
        intention = TrainingIntention.Muscle,
        equipment = listOf(Equipment.Barbell),
        sessionMinutes = 60,
        routineTotalMin = listOf(100),
        cardio = CardioPlan.of(150, 1),
        source = BuiltBlock.SOURCE_BUILDER,
        sessions = listOf(
            BuiltSession(
                "Cuerpo completo",
                listOf("chest"),
                listOf(BuiltExercise("main", "e1", 3, 8, 12, 120, 8, null, null, null, null)),
            ),
        ),
    )
}
