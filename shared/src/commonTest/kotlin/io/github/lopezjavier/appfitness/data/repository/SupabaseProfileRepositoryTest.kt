package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.model.WeightEntry
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseProfileRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun signedInRepository(profileResponse: () -> String): SupabaseProfileRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(SESSION, HttpStatusCode.OK, headers)
                } else {
                    respond(profileResponse(), HttpStatusCode.OK, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseProfileRepository(client.auth, client.postgrest)
    }

    @Test
    fun decodesTheProfileRowAndFiltersByTheCurrentUser() = runTest {
        val repo =
            signedInRepository {
                """[{"display_name":"Ana","avatar_url":"https://img/ana.png","onboarding_completed":true}]"""
            }

        val profile = repo.getOwnProfile().getOrThrow()

        assertEquals(ProfileSummary("Ana", "https://img/ana.png", true), profile)
        val url = requests.last().url.toString()
        assertTrue(url.contains("id=eq.user-1"), url)
        assertTrue(url.contains("select=display_name"), url)
    }

    @Test
    fun nullAvatar_isKeptNull() = runTest {
        val repo = signedInRepository { """[{"display_name":"Ana","avatar_url":null,"onboarding_completed":false}]""" }

        assertEquals(ProfileSummary("Ana", null, false), repo.getOwnProfile().getOrThrow())
    }

    @Test
    fun networkError_isAppErrorNetwork() = runTest {
        var fail = false
        val repo =
            signedInRepository {
                if (fail) throw IOException("offline")
                "[]"
            }
        fail = true

        assertEquals(AppError.Network, repo.getOwnProfile().appErrorOrNull())
    }

    @Test
    fun getBiometrics_decodesNullsAndRoundsTheNumericHeight() = runTest {
        val full =
            signedInRepository {
                """[{"sex":"male","birth_date":"1999-06-15","height_cm":178.0,"activity_level":"very_high"}]"""
            }
        assertEquals(
            SavedBiometrics(Sex.Male, LocalDate(1999, 6, 15), 178, ActivityLevel.VeryHigh),
            full.getBiometrics().getOrThrow(),
        )
        val url = requests.last().url.toString()
        assertTrue(url.contains("id=eq.user-1"), url)
        assertTrue(url.contains("select=sex"), url)

        val empty =
            signedInRepository {
                """[{"sex":null,"birth_date":null,"height_cm":null,"activity_level":null}]"""
            }
        assertEquals(SavedBiometrics(), empty.getBiometrics().getOrThrow())
    }

    @Test
    fun updateBiometrics_patchesTheOwnRowWithTheFourFieldsOnly() = runTest {
        val repo = signedInRepository { "[]" }

        val result = repo.updateBiometrics(Biometrics(Sex.Female, LocalDate(1999, 6, 15), 165, ActivityLevel.Light))

        assertTrue(result.isSuccess)
        val patch = requests.last()
        assertEquals(HttpMethod.Patch, patch.method)
        assertTrue(patch.url.toString().contains("id=eq.user-1"), patch.url.toString())
        val body = (patch.body as TextContent).text
        assertEquals(
            """{"sex":"female","birth_date":"1999-06-15","height_cm":165,"activity_level":"light"}""",
            body,
        )
        assertFalse(body.contains("onboarding_completed"))
    }

    @Test
    fun markOnboardingCompleted_patchesOnlyTheFlag() = runTest {
        val repo = signedInRepository { "[]" }

        assertTrue(repo.markOnboardingCompleted().isSuccess)

        val patch = requests.last()
        assertEquals(HttpMethod.Patch, patch.method)
        assertTrue(patch.url.toString().contains("id=eq.user-1"), patch.url.toString())
        assertEquals("""{"onboarding_completed":true}""", (patch.body as TextContent).text)
    }

    @Test
    fun getTargetWeight_decodesTheColumnOfTheOwnRow() = runTest {
        val repo = signedInRepository { """[{"target_weight_kg":70.5}]""" }

        assertEquals(70.5, repo.getTargetWeight().getOrThrow())
        val url = requests.last().url.toString()
        assertTrue(url.contains("id=eq.user-1"), url)
        assertTrue(url.contains("select=target_weight_kg"), url)
    }

    @Test
    fun getTargetWeight_isNullWhenNotSet() = runTest {
        val repo = signedInRepository { """[{"target_weight_kg":null}]""" }

        assertEquals(null, repo.getTargetWeight().getOrThrow())
    }

    @Test
    fun setTargetWeight_patchesOnlyTheTargetOfTheOwnRow() = runTest {
        val repo = signedInRepository { "[]" }

        assertTrue(repo.setTargetWeight(70.5).isSuccess)

        val patch = requests.last()
        assertEquals(HttpMethod.Patch, patch.method)
        assertTrue(patch.url.toString().contains("id=eq.user-1"), patch.url.toString())
        assertEquals("""{"target_weight_kg":70.5}""", (patch.body as TextContent).text)
    }

    @Test
    fun setTargetWeight_nullClearsTheColumn() = runTest {
        val repo = signedInRepository { "[]" }

        assertTrue(repo.setTargetWeight(null).isSuccess)

        assertEquals("""{"target_weight_kg":null}""", (requests.last().body as TextContent).text)
    }

    @Test
    fun targetWeight_networkError_isAppErrorNetwork() = runTest {
        var fail = false
        val repo =
            signedInRepository {
                if (fail) throw IOException("offline")
                "[]"
            }
        fail = true

        assertEquals(AppError.Network, repo.getTargetWeight().appErrorOrNull())
        assertEquals(AppError.Network, repo.setTargetWeight(70.0).appErrorOrNull())
    }

    @Test
    fun biometrics_networkError_isAppErrorNetwork() = runTest {
        var fail = false
        val repo =
            signedInRepository {
                if (fail) throw IOException("offline")
                "[]"
            }
        fail = true

        assertEquals(AppError.Network, repo.getBiometrics().appErrorOrNull())
        val update = Biometrics(Sex.Male, LocalDate(1999, 6, 15), 178, ActivityLevel.Moderate)
        assertEquals(AppError.Network, repo.updateBiometrics(update).appErrorOrNull())
    }
}

class SupabaseProfileRepositorySavePreferencesTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var fail: Throwable? = null
    private var status = HttpStatusCode.NoContent

    private suspend fun repository(session: String = SESSION): SupabaseProfileRepository {
        val engine =
            MockEngine { request ->
                requests += request
                fail?.let { if (!request.url.encodedPath.contains("/auth/")) throw it }
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(session, HttpStatusCode.OK, headers)
                } else {
                    respond("", status, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseProfileRepository(client.auth, client.postgrest)
    }

    private fun sessionWithMetadata(metadata: String) =
        """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
            """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c","user_metadata":$metadata}}"""

    private val biometrics = Biometrics(Sex.Male, LocalDate(1999, 6, 15), 180, ActivityLevel.Moderate)
    private val goals = NutritionGoals(GoalStrategy.Deficit, -400, 2292, MacroSplit(35, 42, 23))
    private val goalsJson =
        """"p_strategy":"deficit","p_calorie_adjustment":-400,"p_target_kcal":2292,""" +
            """"p_protein_pct":35,"p_carbs_pct":42,"p_fat_pct":23"""

    @Test
    fun withoutWeight_callsTheRpcWithoutTheWeightArguments() = runTest {
        val result = repository().savePreferences("Ana", biometrics, goals, null, false)

        assertTrue(result.isSuccess)
        val call = requests.last()
        assertEquals(HttpMethod.Post, call.method)
        assertEquals("/rest/v1/rpc/save_preferences", call.url.encodedPath)
        val body = (call.body as TextContent).text
        assertEquals(
            """{"p_display_name":"Ana","p_sex":"male","p_birth_date":"1999-06-15","p_height_cm":180,""" +
                """"p_activity_level":"moderate",$goalsJson,"p_warm_up_included":false,""" +
                """"p_previous_values_mode":"overall"}""",
            body,
        )
    }

    @Test
    fun withCalculatorSettings_sendsTheFiveCalculatorArguments() = runTest {
        val settings = CalculatorSettings(7.5, listOf(20.0, 2.5, 0.5), 5.0, 1.0, 1.5)

        assertTrue(
            repository().savePreferences("Ana", biometrics, goals, null, false, calculators = settings).isSuccess,
        )

        val body = (requests.last().body as TextContent).text
        assertTrue(
            body.endsWith(
                """"p_previous_values_mode":"overall","p_bar_weight_kg":7.5,"p_plate_kg":[20.0,2.5,0.5],""" +
                    """"p_plate_rounding_kg":5.0,"p_dumbbell_rounding_kg":1.0,"p_dumbbell_handle_kg":1.5}""",
            ),
            body,
        )
    }

    @Test
    fun withWeight_sendsTheWeightAndTheLocalDayBounds() = runTest {
        val weight =
            WeightEntry(
                weightKg = 81.5,
                measuredAt = Instant.parse("2026-10-19T10:00:00Z"),
                dayStart = Instant.parse("2026-10-18T22:00:00Z"),
                dayEnd = Instant.parse("2026-10-19T22:00:00Z"),
            )

        assertTrue(repository().savePreferences("Ana", biometrics, goals, weight, true).isSuccess)

        val body = (requests.last().body as TextContent).text
        assertEquals(
            """{"p_display_name":"Ana","p_sex":"male","p_birth_date":"1999-06-15","p_height_cm":180,""" +
                """"p_activity_level":"moderate",$goalsJson,"p_weight_kg":81.5,""" +
                """"p_measured_at":"2026-10-19T10:00:00Z","p_day_start":"2026-10-18T22:00:00Z",""" +
                """"p_day_end":"2026-10-19T22:00:00Z","p_warm_up_included":true,"p_previous_values_mode":"overall"}""",
            body,
        )
    }

    @Test
    fun ioFailure_isNetwork() = runTest {
        val repo = repository()
        fail = IOException("offline")

        assertEquals(AppError.Network, repo.savePreferences("Ana", biometrics, goals, null, false).appErrorOrNull())
    }

    @Test
    fun clientError_isAnAppError() = runTest {
        val repo = repository()
        status = HttpStatusCode.BadRequest

        assertEquals(AppError.Unknown, repo.savePreferences("Ana", biometrics, goals, null, false).appErrorOrNull())
    }

    @Test
    fun updateDisplayName_patchesOnlyTheNameOfTheOwnRow() = runTest {
        assertTrue(repository().updateDisplayName("Ana López").isSuccess)

        val patch = requests.last()
        assertEquals(HttpMethod.Patch, patch.method)
        assertTrue(patch.url.toString().contains("id=eq.user-1"), patch.url.toString())
        assertEquals("""{"display_name":"Ana López"}""", (patch.body as TextContent).text)
    }

    @Test
    fun hasProvidedFullName_isTrueWhenGoogleGaveAName() = runTest {
        val repo = repository(sessionWithMetadata("""{"full_name":"Ana López"}"""))

        assertTrue(repo.hasProvidedFullName())
    }

    @Test
    fun hasProvidedFullName_isFalseWhenTheNameIsBlank() = runTest {
        val repo = repository(sessionWithMetadata("""{"full_name":"   "}"""))

        assertFalse(repo.hasProvidedFullName())
    }

    @Test
    fun hasProvidedFullName_isFalseWhenTheMetadataHasNoName() = runTest {
        assertFalse(repository(sessionWithMetadata("""{"name":"Ana"}""")).hasProvidedFullName())
        assertFalse(repository().hasProvidedFullName())
    }
}
