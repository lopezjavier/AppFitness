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
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.CoachIntakeDraft
import io.github.lopezjavier.appfitness.domain.model.CoachModelChoice
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextStatus
import io.github.lopezjavier.appfitness.domain.model.InjuredArea
import io.github.lopezjavier.appfitness.domain.model.PreBreakBest
import io.github.lopezjavier.appfitness.domain.model.Situation
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

private const val INTAKE_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val ROW =
    """{"user_id":"user-1","training_years":4.0,"situation":"returning_injury","injured_area":"shoulder",""" +
        """"pre_break_bests":[{"exercise_id":"bench","weight_kg":90,"reps":5}],"known_maintenance_kcal":null,""" +
        """"known_cut_kcal":null,"free_text":"hola","maintenance_handover_at":null,""" +
        """"saved_at":"2026-11-02T10:00:00+00:00","updated_at":"2026-11-02T10:00:00+00:00"}"""

private const val EXTRACT_ANSWER =
    """{"fields":{"training_years":4,"situation":"returning_injury","injured_area":"shoulder",""" +
        """"pre_break_bests":[{"exercise_id":"bench","weight_kg":90,"reps":5}],""" +
        """"known_maintenance_kcal":null,"known_cut_kcal":null}}"""

private fun intakeDelta(text: String) =
    "event: delta\ndata: " + JsonObject(mapOf("text" to JsonPrimitive(text))) + "\n\nevent: done\ndata: {}\n\n"

class SupabaseCoachIntakeRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var offline = false
    private val today = LocalDate(2026, 11, 2)
    private val preference = InMemoryCoachModelPreference()

    private suspend fun repo(
        signIn: Boolean = true,
        respond: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ): SupabaseCoachIntakeRepository {
        val engine = MockEngine { request ->
            requests += request
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.url.encodedPath.contains("/auth/") -> respond(INTAKE_SESSION, HttpStatusCode.OK, json)

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
        return SupabaseCoachIntakeRepository(client.auth, client.postgrest, client.functions, preference)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun bodyOf(request: HttpRequestData) = (request.body as TextContent).text

    private fun failureOf(result: Result<*>): CoachFailure {
        val exception = result.exceptionOrNull()
        assertIs<CoachException>(exception)
        return exception.failure
    }

    @Test
    fun get_readsTheOwnRowAndMapsIt() = runTest {
        val repository = repo { HttpStatusCode.OK to "[$ROW]" }

        val intake = repository.get().getOrThrow()

        assertTrue(rest().single().url.encodedPath.endsWith("/rest/v1/coach_user_intake"))
        assertEquals(Situation.ReturningInjury, intake?.situation)
        assertEquals(InjuredArea.Shoulder, intake?.injuredArea)
        assertEquals(listOf(PreBreakBest("bench", 90.0, 5)), intake?.preBreakBests)
    }

    @Test
    fun get_withoutARow_isNull() = runTest {
        val repository = repo { HttpStatusCode.OK to "[]" }

        assertNull(repository.get().getOrThrow())
    }

    @Test
    fun save_callsTheRpcWithTheFilledFieldsAndReturnsTheStoredRow() = runTest {
        val repository = repo { HttpStatusCode.OK to ROW }

        val stored = repository.save(CoachIntake(trainingYears = 4.0, freeText = "hola")).getOrThrow()

        val call = rest().single()
        assertEquals(HttpMethod.Post, call.method)
        assertTrue(call.url.encodedPath.endsWith("/rest/v1/rpc/save_coach_intake"), call.url.toString())
        assertEquals("""{"p":{"training_years":4.0,"pre_break_bests":[],"free_text":"hola"}}""", bodyOf(call))
        assertEquals(4.0, stored.trainingYears)
    }

    @Test
    fun save_serverRejection_isAFailure() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to """{"code":"22023","message":"invalid situation","details":null,"hint":null}"""
        }

        assertTrue(repository.save(CoachIntake()).isFailure)
    }

    @Test
    fun extract_postsTheKindTextFilledFieldsAndModel_andMapsTheAnswer() = runTest {
        val repository = repo { HttpStatusCode.OK to intakeDelta(EXTRACT_ANSWER) }
        preference.select(CoachModelChoice.Haiku)

        val draft = repository.extract("Entrené 4 años", CoachIntakeDraft(knownCutKcal = 1900)).getOrThrow()

        val call = rest().single()
        assertTrue(call.url.encodedPath.endsWith("/functions/v1/coach"), call.url.toString())
        assertEquals(
            """{"kind":"intake_extract","input":{"free_text":"Entrené 4 años","fields":{"known_cut_kcal":1900}},""" +
                """"model":"haiku"}""",
            bodyOf(call),
        )
        assertEquals(4.0, draft.trainingYears)
        assertEquals(Situation.ReturningInjury, draft.situation)
        assertEquals(listOf(PreBreakBest("bench", 90.0, 5)), draft.preBreakBests)
        assertNull(draft.knownMaintenanceKcal)
    }

    @Test
    fun extract_userLimit_isMappedWithItsScope() = runTest {
        val repository = repo {
            HttpStatusCode.TooManyRequests to """{"error":{"code":"coach_limit_reached","scope":"user"}}"""
        }

        assertEquals(
            CoachFailure.LimitReached(CoachFailure.Scope.User),
            failureOf(repository.extract("hola")),
        )
    }

    @Test
    fun extract_invalidOutput_isMapped() = runTest {
        val repository = repo { HttpStatusCode.BadGateway to """{"error":{"code":"coach_invalid_output"}}""" }

        assertEquals(CoachFailure.Invalid, failureOf(repository.extract("hola")))
    }

    @Test
    fun extract_unreadableAnswer_isOther() = runTest {
        val repository = repo { HttpStatusCode.OK to intakeDelta("not json") }

        assertEquals(CoachFailure.Other, failureOf(repository.extract("hola")))
    }

    @Test
    fun extract_offline_isNetwork() = runTest {
        val repository = repo { HttpStatusCode.OK to "" }
        offline = true

        assertEquals(AppError.Network, repository.extract("hola").appErrorOrNull())
    }

    @Test
    fun freeTextLines_callsTheRpcWithTodayAndMapsStatuses() = runTest {
        val repository = repo {
            HttpStatusCode.OK to
                """[{"kind":"intake","source_id":"intake","date":"2026-11-02","text":"hola","status":"sent"},""" +
                """{"kind":"check_in","source_id":"2026-10-26","date":"2026-10-28","text":"x","status":"excluded"}]"""
        }

        val lines = repository.freeTextLines(today).getOrThrow()

        val call = rest().single()
        assertTrue(call.url.encodedPath.endsWith("/rest/v1/rpc/coach_free_text_lines"), call.url.toString())
        assertEquals("""{"p_today":"2026-11-02"}""", bodyOf(call))
        assertEquals(listOf(FreeTextStatus.Sent, FreeTextStatus.Excluded), lines.map { it.status })
        assertEquals(listOf(FreeTextKind.Intake, FreeTextKind.CheckIn), lines.map { it.kind })
    }

    @Test
    fun setLineExcluded_callsTheRpcWithKindIdAndFlag() = runTest {
        val repository = repo { HttpStatusCode.NoContent to "" }

        repository.setLineExcluded(FreeTextKind.CheckIn, "2026-10-26", true).getOrThrow()

        val call = rest().single()
        assertTrue(call.url.encodedPath.endsWith("/rest/v1/rpc/set_free_text_line_excluded"), call.url.toString())
        assertEquals("""{"p_kind":"check_in","p_id":"2026-10-26","p_excluded":true}""", bodyOf(call))
    }

    @Test
    fun acknowledgeMaintenanceHandover_callsTheRpc() = runTest {
        val repository = repo { HttpStatusCode.NoContent to "" }

        repository.acknowledgeMaintenanceHandover().getOrThrow()

        assertTrue(rest().single().url.encodedPath.endsWith("/rest/v1/rpc/ack_maintenance_handover"))
    }

    @Test
    fun withoutASession_everyCallIsUnauthorized() = runTest {
        val repository = repo(signIn = false) { HttpStatusCode.OK to "[]" }

        assertEquals(AppError.Unauthorized, repository.get().appErrorOrNull())
        assertEquals(AppError.Unauthorized, repository.extract("hola").appErrorOrNull())
        assertEquals(AppError.Unauthorized, repository.freeTextLines(today).appErrorOrNull())
        assertTrue(requests.isEmpty())
    }
}
