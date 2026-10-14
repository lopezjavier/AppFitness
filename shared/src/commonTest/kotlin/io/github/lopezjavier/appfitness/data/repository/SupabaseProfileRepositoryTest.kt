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
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
