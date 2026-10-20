package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SLOTS_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseMealSlotRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun signedInRepository(response: () -> String): SupabaseMealSlotRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(SLOTS_SESSION, HttpStatusCode.OK, headers)
                } else {
                    respond(response(), HttpStatusCode.OK, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseMealSlotRepository(client.auth, client.postgrest)
    }

    @Test
    fun listEnabled_filtersEnabledAndOrdersByPosition() = runTest {
        val repo = signedInRepository { "[]" }

        repo.listEnabled().getOrThrow()

        val url = requests.last().url.toString()
        assertTrue(url.contains("enabled=eq.true"), url)
        assertTrue(url.contains("order=position.asc"), url)
        assertEquals("id,code,label,position,enabled", requests.last().url.parameters["select"])
    }

    @Test
    fun listEnabled_mapsDefaultAndCustomSlots() = runTest {
        val repo =
            signedInRepository {
                """[{"id":"s1","code":"breakfast","label":"Desayuno","position":0,"enabled":true},""" +
                    """{"id":"s2","code":null,"label":"Pre-entreno","position":4,"enabled":true}]"""
            }

        assertEquals(
            listOf(
                MealSlot("s1", MealCode.Breakfast, "Desayuno", 0, true),
                MealSlot("s2", null, "Pre-entreno", 4, true),
            ),
            repo.listEnabled().getOrThrow(),
        )
    }

    @Test
    fun listAll_doesNotFilterEnabledAndKeepsDisabledSlots() = runTest {
        val repo =
            signedInRepository {
                """[{"id":"s1","code":"breakfast","label":"Desayuno","position":0,"enabled":true},""" +
                    """{"id":"s2","code":"snack","label":"Merienda","position":1,"enabled":false}]"""
            }

        val slots = repo.listAll().getOrThrow()

        val url = requests.last().url.toString()
        assertTrue(!url.contains("enabled=eq"), url)
        assertTrue(url.contains("order=position.asc"), url)
        assertEquals(listOf(true, false), slots.map { it.enabled })
    }

    @Test
    fun save_callsRpcWithSlotsInOrderWithoutViewedDay() = runTest {
        val repo = signedInRepository { "" }

        val result =
            repo.save(
                listOf(
                    MealSlotDraft("s1", MealCode.Breakfast, "Desayuno"),
                    MealSlotDraft(null, null, "Segundo desayuno"),
                    MealSlotDraft("s2", MealCode.Snack, "Merienda"),
                ),
            )

        assertTrue(result.isSuccess)
        val request = requests.last()
        assertTrue(request.url.encodedPath.endsWith("/rpc/save_meal_slots"), request.url.toString())
        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        assertTrue("p_viewed_day" !in body)
        val slots = body["p_slots"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("s1", null, "s2"), slots.map { it["id"]?.jsonPrimitive?.contentOrNull })
        assertEquals(
            listOf("Desayuno", "Segundo desayuno", "Merienda"),
            slots.map { it["label"]?.jsonPrimitive?.content },
        )
        assertTrue(slots.none { "enabled" in it })
        assertTrue(slots.none { "code" in it })
    }

    private suspend fun failing(
        status: HttpStatusCode,
        code: String,
        call: suspend (SupabaseMealSlotRepository) -> Result<Unit>,
    ): Result<Unit> {
        val engine =
            MockEngine { request ->
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(SLOTS_SESSION, HttpStatusCode.OK, headers)
                } else {
                    respond("""{"code":"$code","message":"boom","details":null,"hint":null}""", status, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return call(SupabaseMealSlotRepository(client.auth, client.postgrest))
    }

    private suspend fun failingSave(status: HttpStatusCode, code: String): Result<Unit> =
        failing(status, code) { it.save(listOf(MealSlotDraft("s1", null, "A"))) }

    private suspend fun failingDelete(status: HttpStatusCode, code: String): Result<Unit> =
        failing(status, code) { it.delete("s1") }

    @Test
    fun delete_callsRpcWithTheSlotId() = runTest {
        val repo = signedInRepository { "" }

        val result = repo.delete("s1")

        assertTrue(result.isSuccess)
        val request = requests.last()
        assertTrue(request.url.encodedPath.endsWith("/rpc/delete_meal_slot"), request.url.toString())
        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        assertEquals("s1", body["p_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun delete_lastMealOrWithEntries_isValidationError() = runTest {
        assertEquals(AppError.Validation, failingDelete(HttpStatusCode.BadRequest, "23514").appErrorOrNull())
    }

    @Test
    fun delete_unknownSlot_isNotFound() = runTest {
        assertEquals(AppError.NotFound, failingDelete(HttpStatusCode.BadRequest, "P0002").appErrorOrNull())
    }

    @Test
    fun delete_offline_isNetworkError() = runTest {
        var fail = false
        val repo =
            signedInRepository {
                if (fail) throw IOException("offline")
                ""
            }
        fail = true

        assertEquals(AppError.Network, repo.delete("s1").appErrorOrNull())
    }

    @Test
    fun save_checkViolation_isValidationError() = runTest {
        assertEquals(AppError.Validation, failingSave(HttpStatusCode.BadRequest, "23514").appErrorOrNull())
    }

    @Test
    fun save_invalidShape_isValidationError() = runTest {
        assertEquals(AppError.Validation, failingSave(HttpStatusCode.BadRequest, "22023").appErrorOrNull())
    }

    @Test
    fun save_foreignSlot_isNotFound() = runTest {
        assertEquals(AppError.NotFound, failingSave(HttpStatusCode.BadRequest, "P0002").appErrorOrNull())
    }

    @Test
    fun save_otherServerError_isUnknown() = runTest {
        assertEquals(AppError.Unknown, failingSave(HttpStatusCode.InternalServerError, "XX000").appErrorOrNull())
    }

    @Test
    fun networkErrors_areMapped() = runTest {
        var fail = false
        val repo =
            signedInRepository {
                if (fail) throw IOException("offline")
                "[]"
            }
        fail = true

        assertEquals(AppError.Network, repo.listEnabled().appErrorOrNull())
    }

    @Test
    fun signedOut_isUnauthorized() = runTest {
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, MockEngine { respond("[]") }, MemorySessionManager())
        val repo = SupabaseMealSlotRepository(client.auth, client.postgrest)

        assertEquals(AppError.Unauthorized, repo.listEnabled().appErrorOrNull())
    }
}
