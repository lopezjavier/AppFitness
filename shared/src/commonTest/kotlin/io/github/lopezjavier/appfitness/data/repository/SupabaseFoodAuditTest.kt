package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.FoodAuditRow
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val AUDIT_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val AUDIT_ROWS =
    """[{"food_id":"a1","name":"Tarta","brand":"Casa","kcal_100":500,"computed":249.6,"gap":-250.4,""" +
        """"result":"clearly_wrong"},""" +
        """{"food_id":"a2","name":"Pan","brand":null,"kcal_100":260,"computed":300,"gap":40,""" +
        """"result":"mismatch"}]"""

private class AuditClock : Clock {
    override fun today() = LocalDate(2026, 10, 20)

    override fun now() = Instant.parse("2026-10-20T10:00:00Z")
}

/** `SupabaseFoodRepository.foodEnergyAudit` over the `food_energy_audit()` RPC (US-117). */
class SupabaseFoodAuditTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()
    private var status = HttpStatusCode.OK

    private suspend fun signedIn(response: () -> String): SupabaseFoodRepository {
        val engine = MockEngine { request ->
            requests += request
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            if (request.url.encodedPath.contains("/auth/")) {
                respond(AUDIT_SESSION, HttpStatusCode.OK, headers)
            } else {
                bodies += request.body.toByteArray().decodeToString()
                respond(response(), status, headers)
            }
        }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseFoodRepository(client.auth, client.postgrest, client.functions, AuditClock())
    }

    @Test
    fun decodesBothLevelsKeepingTheServerOrderAndANullBrand() = runTest {
        val repo = signedIn { AUDIT_ROWS }

        val rows = repo.foodEnergyAudit().getOrThrow()

        assertEquals(listOf("a1", "a2"), rows.map { it.foodId })
        assertEquals(
            FoodAuditRow("a1", "Tarta", "Casa", 500.0, 249.6, -250.4, EnergyCheckLevel.ClearlyWrong),
            rows[0],
        )
        assertEquals(null, rows[1].brand)
        assertEquals(EnergyCheckLevel.Mismatch, rows[1].level)
    }

    @Test
    fun isAPostToTheRpcWithAnEmptyBodyAndWritesNothing() = runTest {
        val repo = signedIn { AUDIT_ROWS }

        repo.foodEnergyAudit().getOrThrow()

        val request = requests.last()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/food_energy_audit"), request.url.toString())
        assertTrue(bodies.last().isBlank() || bodies.last() == "{}", bodies.last())
        assertTrue(requests.none { it.url.encodedPath.endsWith("/rest/v1/foods") }, requests.toString())
    }

    @Test
    fun emptyArray_isAnEmptyList() = runTest {
        val repo = signedIn { "[]" }

        assertEquals(emptyList(), repo.foodEnergyAudit().getOrThrow())
    }

    @Test
    fun unknownResult_isAFailure() = runTest {
        val repo = signedIn {
            """[{"food_id":"a1","name":"X","brand":null,"kcal_100":1,"computed":2,"gap":1,"result":"ok"}]"""
        }

        assertEquals(AppError.Unknown, repo.foodEnergyAudit().appErrorOrNull())
    }

    @Test
    fun serverError_isAFailure() = runTest {
        status = HttpStatusCode.InternalServerError
        val repo = signedIn { """{"message":"boom"}""" }

        assertTrue(repo.foodEnergyAudit().isFailure)
    }

    @Test
    fun networkError_isNetwork() = runTest {
        var fail = false
        val repo = signedIn { if (fail) throw IOException("offline") else "[]" }
        fail = true

        assertEquals(AppError.Network, repo.foodEnergyAudit().appErrorOrNull())
    }

    @Test
    fun signedOut_isUnauthorized() = runTest {
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, MockEngine { respond("[]") }, MemorySessionManager())
        val repo = SupabaseFoodRepository(client.auth, client.postgrest, client.functions, AuditClock())

        assertEquals(AppError.Unauthorized, repo.foodEnergyAudit().appErrorOrNull())
    }
}
