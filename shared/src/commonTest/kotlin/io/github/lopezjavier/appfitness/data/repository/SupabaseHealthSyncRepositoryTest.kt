package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.DailyActivity
import io.github.lopezjavier.appfitness.domain.model.HealthActivityBatch
import io.github.lopezjavier.appfitness.domain.model.HealthActivityImportResult
import io.github.lopezjavier.appfitness.domain.model.HealthReading
import io.github.lopezjavier.appfitness.domain.model.HealthSyncState
import io.github.lopezjavier.appfitness.domain.model.RestingHeartRate
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import io.github.lopezjavier.appfitness.domain.model.SleepStage
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
    fun getState_decodesTheAutoSyncDate() = runTest {
        val repo = signedIn(
            stateBody = """[{"last_synced_at":"2026-10-22T09:00:00+00:00","changes_token":null,""" +
                """"last_auto_sync_date":"2026-10-21"}]""",
        )

        assertEquals(HealthSyncState(SYNCED_AT, null, LocalDate(2026, 10, 21)), repo.getState().getOrThrow())
    }

    @Test
    fun getState_withoutAutoSyncDate_leavesItNull() = runTest {
        val repo =
            signedIn(stateBody = """[{"last_synced_at":"2026-10-22T09:00:00+00:00","last_auto_sync_date":null}]""")

        assertNull(repo.getState().getOrThrow()?.lastAutoSyncDate)
    }

    @Test
    fun importActivity_sendsTheThreeArrays_andDecodesTheCounts() = runTest {
        val repo = signedIn(rpcBody = """{"daily":2,"sleep":1,"resting_hr":1}""")
        val batch = HealthActivityBatch(
            daily = listOf(
                DailyActivity(LocalDate(2026, 10, 27), 8500, 420.5, 2600.0),
                DailyActivity(LocalDate(2026, 10, 26), null, 300.0, null),
            ),
            sleep = listOf(
                SleepSession(
                    "s1",
                    Instant.parse("2026-10-26T22:30:00Z"),
                    Instant.parse("2026-10-27T06:00:00Z"),
                    435,
                    listOf(
                        SleepStage(
                            "deep",
                            Instant.parse("2026-10-26T23:00:00Z"),
                            Instant.parse("2026-10-27T00:00:00Z"),
                        ),
                    ),
                ),
            ),
            restingHeartRate = listOf(RestingHeartRate(LocalDate(2026, 10, 27), 52, null)),
        )

        val result = repo.importActivity(batch).getOrThrow()

        assertEquals(HealthActivityImportResult(2, 1, 1), result)
        val rpc = requests.last()
        assertTrue(rpc.url.encodedPath.endsWith("/rpc/import_health_activity"), rpc.url.toString())
        assertEquals(
            """{"p_daily":[{"day":"2026-10-27","steps":8500,"active_kcal":420.5,"total_kcal":2600.0},""" +
                """{"day":"2026-10-26","active_kcal":300.0}],""" +
                """"p_sleep":[{"external_id":"s1","started_at":"2026-10-26T22:30:00Z",""" +
                """"ended_at":"2026-10-27T06:00:00Z","minutes":435,"stages":[{"stage":"deep",""" +
                """"started_at":"2026-10-26T23:00:00Z","ended_at":"2026-10-27T00:00:00Z"}]}],""" +
                """"p_resting_hr":[{"day":"2026-10-27","bpm":52}]}""",
            (rpc.body as TextContent).text,
        )
    }

    @Test
    fun importActivity_withNoRestingHeartRate_sendsTheEmptyArray() = runTest {
        val repo = signedIn(rpcBody = """{"daily":0,"sleep":0,"resting_hr":0}""")

        repo.importActivity(HealthActivityBatch(emptyList(), emptyList(), emptyList())).getOrThrow()

        assertEquals(
            """{"p_daily":[],"p_sleep":[],"p_resting_hr":[]}""",
            (requests.last().body as TextContent).text,
        )
    }

    @Test
    fun markAutoSynced_sendsTheDay() = runTest {
        val repo = signedIn(rpcBody = "")

        repo.markAutoSynced(LocalDate(2026, 10, 27)).getOrThrow()

        val rpc = requests.last()
        assertTrue(rpc.url.encodedPath.endsWith("/rpc/mark_health_auto_sync"), rpc.url.toString())
        assertEquals("""{"p_day":"2026-10-27"}""", (rpc.body as TextContent).text)
    }

    @Test
    fun activityCalls_mapServerRejectionToFailure() = runTest {
        val repo = signedIn(rejectWith = "22023")

        assertTrue(repo.importActivity(HealthActivityBatch(emptyList(), emptyList(), emptyList())).isFailure)
        assertTrue(repo.markAutoSynced(LocalDate(2026, 10, 27)).isFailure)
    }

    @Test
    fun unconfiguredRepository_failsTheActivityCalls() = runTest {
        val repo = UnconfiguredHealthSyncRepository()

        assertEquals(
            AppError.Unknown,
            repo.importActivity(HealthActivityBatch(emptyList(), emptyList(), emptyList())).appErrorOrNull(),
        )
        assertEquals(AppError.Unknown, repo.markAutoSynced(LocalDate(2026, 10, 27)).appErrorOrNull())
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
        assertEquals(
            AppError.Unauthorized,
            repo.importActivity(HealthActivityBatch(emptyList(), emptyList(), emptyList())).appErrorOrNull(),
        )
        assertEquals(AppError.Unauthorized, repo.markAutoSynced(LocalDate(2026, 10, 27)).appErrorOrNull())
    }
}
