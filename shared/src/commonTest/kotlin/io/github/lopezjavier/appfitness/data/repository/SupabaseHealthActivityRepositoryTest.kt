package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.DailyActivity
import io.github.lopezjavier.appfitness.domain.model.RestingHeartRate
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import io.github.lopezjavier.appfitness.domain.model.SleepStage
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""
private const val DAILY_BODY =
    """[{"day":"2026-10-27","steps":8500,"active_kcal":420.5,"total_kcal":null},""" +
        """{"day":"2026-10-26","steps":null,"active_kcal":300.0,"total_kcal":2500.0}]"""
private const val SLEEP_BODY =
    """[{"external_id":"s1","started_at":"2026-10-26T22:30:00+00:00","ended_at":"2026-10-27T06:00:00+00:00",""" +
        """"minutes":435,"stages":[{"stage":"deep","started_at":"2026-10-26T23:00:00Z",""" +
        """"ended_at":"2026-10-27T00:00:00Z"}]}]"""
private const val RESTING_BODY = """[{"day":"2026-10-27","bpm":52,"external_id":null}]"""

class SupabaseHealthActivityRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun signedIn(
        daily: String = DAILY_BODY,
        sleep: String = SLEEP_BODY,
        resting: String = RESTING_BODY,
        failing: Boolean = false,
        signIn: Boolean = true,
    ): SupabaseHealthActivityRepository {
        val engine = MockEngine { request ->
            requests += request
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            val path = request.url.encodedPath
            when {
                path.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
                failing -> throw IOException("offline")
                path.endsWith("/daily_activity") -> respond(daily, HttpStatusCode.OK, headers)
                path.endsWith("/sleep_sessions") -> respond(sleep, HttpStatusCode.OK, headers)
                path.endsWith("/resting_heart_rate") -> respond(resting, HttpStatusCode.OK, headers)
                else -> respond("[]", HttpStatusCode.NotFound, headers)
            }
        }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseHealthActivityRepository(client.auth, client.postgrest, TimeZone.of("Europe/Madrid"))
    }

    @Test
    fun history_decodesTheThreeTables() = runTest {
        val history = signedIn().history(LocalDate(2026, 10, 1), LocalDate(2026, 10, 29)).getOrThrow()

        assertEquals(
            listOf(
                DailyActivity(LocalDate(2026, 10, 27), 8500, 420.5, null),
                DailyActivity(LocalDate(2026, 10, 26), null, 300.0, 2500.0),
            ),
            history.daily,
        )
        assertEquals(
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
            history.sleep.single(),
        )
        assertEquals(RestingHeartRate(LocalDate(2026, 10, 27), 52, null), history.restingHeartRate.single())
    }

    @Test
    fun history_queriesTheWindowAscending_sleepByLocalDayBounds() = runTest {
        signedIn().history(LocalDate(2026, 10, 1), LocalDate(2026, 10, 29)).getOrThrow()

        val data = requests.filterNot { it.url.encodedPath.contains("/auth/") }
        fun query(table: String) = data.single { it.url.encodedPath.endsWith("/$table") }.url.parameters
        val daily = query("daily_activity")
        assertEquals("day.asc.nullslast", daily["order"])
        assertTrue(daily.toString().contains("2026-10-01") && daily.toString().contains("2026-10-29"))
        assertEquals("ended_at.asc.nullslast", query("sleep_sessions")["order"])
        // Madrid is UTC+2 until Oct 25 and UTC+1 after: local midnight is 22:00Z / 23:00Z of the day before.
        val sleep = query("sleep_sessions").toString()
        assertTrue(sleep.contains("2026-09-30T22:00:00Z"), sleep)
        assertTrue(sleep.contains("2026-10-28T23:00:00Z"), sleep)
        assertEquals("day.asc.nullslast", query("resting_heart_rate")["order"])
    }

    @Test
    fun day_returnsTheFiveValuesOfThatDay() = runTest {
        val day = signedIn().day(LocalDate(2026, 10, 27)).getOrThrow()

        assertEquals(8500, day.steps)
        assertEquals(420.5, day.activeKcal)
        assertNull(day.totalKcal)
        assertEquals(435, day.sleep?.minutes)
        assertEquals(52, day.restingHeartRate)
    }

    @Test
    fun day_withNothingStored_isAllNullNotAFailure() = runTest {
        val day = signedIn(daily = "[]", sleep = "[]", resting = "[]").day(LocalDate(2026, 10, 27)).getOrThrow()

        assertNull(day.steps)
        assertNull(day.sleep)
        assertNull(day.restingHeartRate)
    }

    @Test
    fun history_andDay_withoutASession_areUnauthorized() = runTest {
        val repository = signedIn(signIn = false)

        assertEquals(
            AppError.Unauthorized,
            repository.history(LocalDate(2026, 10, 1), LocalDate(2026, 10, 2)).appErrorOrNull(),
        )
        assertEquals(AppError.Unauthorized, repository.day(LocalDate(2026, 10, 1)).appErrorOrNull())
    }

    @Test
    fun history_andDay_whenOffline_areNetworkErrors() = runTest {
        val repository = signedIn(failing = true)

        assertEquals(
            AppError.Network,
            repository.history(LocalDate(2026, 10, 1), LocalDate(2026, 10, 2)).appErrorOrNull(),
        )
        assertEquals(AppError.Network, repository.day(LocalDate(2026, 10, 1)).appErrorOrNull())
    }
}
