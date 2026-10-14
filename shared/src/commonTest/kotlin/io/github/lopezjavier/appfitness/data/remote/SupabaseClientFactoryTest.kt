package io.github.lopezjavier.appfitness.data.remote

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** ADR-17: the new `sb_publishable_` key format must travel as the `apikey` header. */
class SupabaseClientFactoryTest {
    private val config =
        AppConfig(
            supabaseUrl = "http://localhost:54321",
            supabasePublishableKey = "sb_publishable_test",
            googleWebClientId = "",
            isLocal = true,
            testLogin = TestLoginCredentials("a@b.c", "pw"),
        )

    @Test
    fun publishableKey_isSentAsApikeyHeader() = runTest {
        val captured = mutableListOf<HttpRequestData>()
        val engine =
            MockEngine { request ->
                captured += request
                respond(
                    content = """{"error":"invalid_grant","error_description":"nope"}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())

        runCatching {
            client.auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                email = "a@b.c"
                password = "pw"
            }
        }

        val request = captured.last()
        assertEquals("sb_publishable_test", request.headers["apikey"])
        assertEquals("localhost", request.url.host)
        client.close()
    }
}
