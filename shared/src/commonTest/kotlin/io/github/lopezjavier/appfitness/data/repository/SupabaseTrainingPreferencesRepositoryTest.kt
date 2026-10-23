package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseTrainingPreferencesRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var body = "[]"
    private var failing = false

    private suspend fun repository(signIn: Boolean = true): SupabaseTrainingPreferencesRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
                    failing -> throw IOException("offline")
                    else -> respond(body, HttpStatusCode.OK, headers)
                }
            }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseTrainingPreferencesRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    @Test
    fun get_readsTheOwnRow() = runTest {
        body = """[{"warm_up_included":true,"previous_values_mode":"routine"}]"""

        val result = repository().get().getOrThrow()

        assertEquals(TrainingPreferences(true, PreviousValuesMode.Routine), result)
        val query = rest().single().url.toString()
        assertTrue(query.contains("/rest/v1/training_preferences"), query)
        assertTrue(query.contains("user_id=eq.user-1"), query)
    }

    @Test
    fun get_readsTheCalculatorColumns() = runTest {
        body = """[{"warm_up_included":false,"previous_values_mode":"overall","bar_weight_kg":7.5,""" +
            """"plate_kg":[20,2.5,0.5],"plate_rounding_kg":5.0,"dumbbell_rounding_kg":1.0}]"""

        val result = repository().get().getOrThrow()

        assertEquals(7.5, result.barWeightKg)
        assertEquals(listOf(20.0, 2.5, 0.5), result.plateKg)
        assertEquals(5.0, result.plateRoundingKg)
        assertEquals(1.0, result.dumbbellRoundingKg)
        assertTrue(rest().single().url.toString().contains("plate_kg"))
    }

    @Test
    fun get_rowWithoutCalculatorColumnsUsesTheDefaults() = runTest {
        body = """[{"warm_up_included":true}]"""

        val result = repository().get().getOrThrow()

        assertEquals(TrainingPreferences(warmUpIncluded = true), result)
        assertEquals(20.0, result.barWeightKg)
        assertEquals(10, result.plateKg.size)
    }

    @Test
    fun get_rowWithoutTheModeColumnDefaultsToOverall() = runTest {
        body = """[{"warm_up_included":false}]"""

        assertEquals(PreviousValuesMode.Overall, repository().get().getOrThrow().previousValuesMode)
    }

    @Test
    fun get_withoutARowIsTheDefault() = runTest {
        assertEquals(TrainingPreferences(warmUpIncluded = false), repository().get().getOrThrow())
    }

    @Test
    fun get_signedOutIsUnauthorized() = runTest {
        assertEquals(AppError.Unauthorized, repository(signIn = false).get().appErrorOrNull())
    }

    @Test
    fun get_offlineIsNetworkError() = runTest {
        val repo = repository()
        failing = true

        assertEquals(AppError.Network, repo.get().appErrorOrNull())
    }
}
