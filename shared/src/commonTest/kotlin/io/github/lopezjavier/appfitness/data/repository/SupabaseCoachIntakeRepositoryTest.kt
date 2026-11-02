package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextStatus
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val INTAKE_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val ROW =
    """{"user_id":"user-1","training_years":4.0,"known_maintenance_kcal":null,"free_text":"hola",""" +
        """"maintenance_handover_at":null,"saved_at":"2026-11-02T10:00:00+00:00"}"""

class SupabaseCoachIntakeRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var offline = false
    private val today = LocalDate(2026, 11, 2)

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
        return SupabaseCoachIntakeRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun bodyOf(request: HttpRequestData) = (request.body as TextContent).text

    @Test
    fun get_readsTheOwnRowAndMapsIt() = runTest {
        val repository = repo { HttpStatusCode.OK to "[$ROW]" }

        val intake = repository.get().getOrThrow()

        assertTrue(rest().single().url.encodedPath.endsWith("/rest/v1/coach_user_intake"))
        assertEquals(CoachIntake(trainingYears = 4.0, freeText = "hola"), intake)
    }

    @Test
    fun get_withoutARow_isNull() = runTest {
        val repository = repo { HttpStatusCode.OK to "[]" }

        assertNull(repository.get().getOrThrow())
    }

    @Test
    fun save_callsTheRpcWithOnlyTheEditedKeyAndReturnsTheStoredRow() = runTest {
        val repository = repo { HttpStatusCode.OK to ROW }

        val stored = repository.save(IntakeUpdate.FreeText("hola")).getOrThrow()

        val call = rest().single()
        assertEquals(HttpMethod.Post, call.method)
        assertTrue(call.url.encodedPath.endsWith("/rest/v1/rpc/save_coach_intake"), call.url.toString())
        assertEquals("""{"p":{"free_text":"hola"}}""", bodyOf(call))
        assertEquals(CoachIntake(trainingYears = 4.0, freeText = "hola"), stored)
    }

    @Test
    fun save_maintenanceCleared_sendsAnExplicitNull() = runTest {
        val repository = repo { HttpStatusCode.OK to ROW }

        repository.save(IntakeUpdate.KnownMaintenance(null)).getOrThrow()

        assertEquals("""{"p":{"known_maintenance_kcal":null}}""", bodyOf(rest().single()))
    }

    @Test
    fun save_serverRejection_isAFailure() = runTest {
        val repository = repo {
            HttpStatusCode.BadRequest to
                """{"code":"22023","message":"invalid known_maintenance_kcal","details":null,"hint":null}"""
        }

        assertTrue(repository.save(IntakeUpdate.KnownMaintenance(900)).isFailure)
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
        assertEquals(AppError.Unauthorized, repository.freeTextLines(today).appErrorOrNull())
        assertTrue(requests.isEmpty())
    }
}
