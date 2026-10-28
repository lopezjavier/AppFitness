package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseNotificationSettingsRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun signedIn(rows: String = "[]") = MockEngine { request ->
        requests += request
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        when {
            request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
            request.method == HttpMethod.Get -> respond(rows, HttpStatusCode.OK, headers)
            else -> respond("", HttpStatusCode.Created, headers)
        }
    }.let { engine ->
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        SupabaseNotificationSettingsRepository(client.auth, client.postgrest)
    }

    @Test
    fun getAll_mapsEnumValuesAndToleratesUpdatedAt() = runTest {
        val repo = signedIn(
            """[{"kind":"weight","enabled":false,"updated_at":"2026-11-17T10:00:00+00:00"},""" +
                """{"kind":"weekly_check_in","enabled":true}]""",
        )

        val all = repo.getAll().getOrThrow()

        assertEquals(mapOf(NotificationKind.Weight to false, NotificationKind.WeeklyCheckIn to true), all)
    }

    @Test
    fun getAll_withoutRows_isEmptyMeaningEnabled() = runTest {
        assertTrue(signedIn().getAll().getOrThrow().isEmpty())
    }

    @Test
    fun setEnabled_upsertsOnUserAndKind() = runTest {
        val repo = signedIn()

        repo.setEnabled(NotificationKind.Circumference, false).getOrThrow()

        val write = requests.last { it.method == HttpMethod.Post }
        assertTrue(write.url.parameters["on_conflict"] == "user_id,kind")
        val body = (write.body as TextContent).text
        assertTrue(""""kind":"circumference"""" in body && """"enabled":false""" in body, body)
    }
}
