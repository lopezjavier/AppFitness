package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.HealthReading
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
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
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""
private val SYNCED_AT = Instant.parse("2026-10-22T09:00:00Z")

class SupabaseHealthSyncRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun signedIn(
        stateBody: String = "[]",
        rpcBody: String = "2",
        failing: Boolean = false,
        rejectWith: String? = null,
    ): SupabaseHealthSyncRepository {
        val engine = MockEngine { request ->
            requests += request
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)

                failing -> throw IOException("offline")

                rejectWith != null -> respond(
                    """{"code":"$rejectWith","message":"boom","details":null,"hint":null}""",
                    HttpStatusCode.BadRequest,
                    headers,
                )

                request.method == HttpMethod.Get -> respond(stateBody, HttpStatusCode.OK, headers)

                else -> respond(rpcBody, HttpStatusCode.OK, headers)
            }
        }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseHealthSyncRepository(client.auth, client.postgrest)
    }

    private val reading = HealthReading(
        externalId = "w1",
        measuredAt = Instant.parse("2026-10-20T07:31:00Z"),
        weightKg = 73.9,
        bodyFatPct = 15.1,
        bmrKcal = 1720,
    )

    @Test
    fun getState_decodesTheStoredRow() = runTest {
        val repo = signedIn(stateBody = """[{"last_synced_at":"2026-10-22T09:00:00+00:00","changes_token":"tok"}]""")

        assertEquals(HealthSyncState(SYNCED_AT, "tok"), repo.getState().getOrThrow())
    }

    @Test
    fun getState_withoutARow_isNull() = runTest {
        assertNull(signedIn().getState().getOrThrow())
    }

    @Test
    fun importReadings_sendsTheRowsAndStateAsRpcParameters_andReturnsTheCount() = runTest {
        val repo = signedIn(rpcBody = "1")

        val imported = repo.importReadings(listOf(reading), SYNCED_AT, "tok").getOrThrow()

        assertEquals(1, imported)
        val rpc = requests.last()
        assertTrue(rpc.url.encodedPath.endsWith("/rpc/import_health_measurements"), rpc.url.toString())
        assertEquals(
            """{"p_rows":[{"external_id":"w1","measured_at":"2026-10-20T07:31:00Z","weight_kg":73.9,""" +
                """"body_fat_pct":15.1,"bmr_kcal":1720}],""" +
                """"p_synced_at":"2026-10-22T09:00:00Z","p_changes_token":"tok"}""",
            (rpc.body as TextContent).text,
        )
    }

    @Test
    fun importReadings_withNoRows_stillSendsTheEmptyArray() = runTest {
        val repo = signedIn(rpcBody = "0")

        assertEquals(0, repo.importReadings(emptyList(), SYNCED_AT, null).getOrThrow())
        assertTrue((requests.last().body as TextContent).text.startsWith("""{"p_rows":[],"""))
    }

    @Test
    fun networkFailure_isAppErrorNetwork() = runTest {
        val repo = signedIn(failing = true)

        assertEquals(AppError.Network, repo.getState().appErrorOrNull())
        assertEquals(AppError.Network, repo.importReadings(emptyList(), SYNCED_AT, null).appErrorOrNull())
    }

    @Test
    fun serverRejection_isAnError() = runTest {
        val repo = signedIn(rejectWith = "22023")

        assertTrue(repo.importReadings(listOf(reading), SYNCED_AT, null).isFailure)
    }

    @Test
    fun signedOut_isUnauthorized() = runTest {
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, MockEngine { respond("[]") }, MemorySessionManager())
        val repo = SupabaseHealthSyncRepository(client.auth, client.postgrest)

        assertEquals(AppError.Unauthorized, repo.getState().appErrorOrNull())
        assertEquals(AppError.Unauthorized, repo.importReadings(emptyList(), SYNCED_AT, null).appErrorOrNull())
    }
}
