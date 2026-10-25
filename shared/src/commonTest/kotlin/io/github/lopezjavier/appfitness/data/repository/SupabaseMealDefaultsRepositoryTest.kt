package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.MealDefault
import io.github.lopezjavier.appfitness.domain.model.MealDefaultAssignment
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DEFAULTS_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseMealDefaultsRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun repository(
        status: HttpStatusCode = HttpStatusCode.OK,
        response: () -> String,
    ): SupabaseMealDefaultsRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(DEFAULTS_SESSION, HttpStatusCode.OK, headers)
                } else {
                    respond(response(), status, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseMealDefaultsRepository(client.auth, client.postgrest)
    }

    private suspend fun failing(status: HttpStatusCode, code: String) =
        repository(status) { """{"code":"$code","message":"boom","details":null,"hint":null}""" }

    private fun lastBody() = Json.parseToJsonElement((requests.last().body as TextContent).text).jsonObject

    @Test
    fun list_decodesTheRowsAndSelectsOnlyTheNeededColumns() = runTest {
        val repo = repository {
            """[{"meal_slot_id":"s1","template_id":"t1","weekday":1},""" +
                """{"meal_slot_id":"s1","template_id":"t2","weekday":6}]"""
        }

        val result = repo.list().getOrThrow()

        assertEquals(listOf(MealDefault("s1", "t1", 1), MealDefault("s1", "t2", 6)), result)
        val url = requests.last().url
        assertTrue(url.encodedPath.endsWith("/meal_defaults"), url.toString())
        assertEquals("meal_slot_id,template_id,weekday", url.parameters["select"])
    }

    @Test
    fun save_sendsOneElementPerMealAndComidaWithSortedWeekdays() = runTest {
        val repo = repository(HttpStatusCode.NoContent) { "" }

        repo.save(listOf(MealDefaultAssignment("s1", "t1", setOf(5, 1, 3)))).getOrThrow()

        assertTrue(requests.last().url.encodedPath.endsWith("/rpc/save_meal_defaults"))
        val sent = lastBody()["p_defaults"]!!.jsonArray.single().jsonObject
        assertEquals("s1", sent["meal_slot_id"]?.jsonPrimitive?.content)
        assertEquals("t1", sent["template_id"]?.jsonPrimitive?.content)
        assertEquals(listOf(1, 3, 5), sent["weekdays"]!!.jsonArray.map { it.jsonPrimitive.int })
    }

    @Test
    fun save_emptyList_sendsAnEmptyArray() = runTest {
        val repo = repository(HttpStatusCode.NoContent) { "" }

        repo.save(emptyList()).getOrThrow()

        assertEquals(0, lastBody()["p_defaults"]!!.jsonArray.size)
    }

    @Test
    fun applyForDate_sendsTheIsoDateAndReturnsTheCount() = runTest {
        val repo = repository { "3" }

        val added = repo.applyForDate(LocalDate(2026, 10, 26)).getOrThrow()

        assertEquals(3, added)
        assertTrue(requests.last().url.encodedPath.endsWith("/rpc/apply_meal_defaults"))
        assertEquals("2026-10-26", lastBody()["p_log_date"]?.jsonPrimitive?.content)
    }

    @Test
    fun invalidParameterOrCheckViolation_isValidation() = runTest {
        assertEquals(
            AppError.Validation,
            failing(HttpStatusCode.BadRequest, "22023").save(emptyList()).appErrorOrNull(),
        )
        assertEquals(
            AppError.Validation,
            failing(HttpStatusCode.BadRequest, "23514").save(emptyList()).appErrorOrNull(),
        )
        assertEquals(
            AppError.Validation,
            failing(HttpStatusCode.BadRequest, "22023").applyForDate(LocalDate(2020, 1, 1)).appErrorOrNull(),
        )
    }

    @Test
    fun unknownMealOrComida_isNotFound() = runTest {
        val repo = failing(HttpStatusCode.BadRequest, "P0002")

        assertEquals(AppError.NotFound, repo.save(emptyList()).appErrorOrNull())
    }

    @Test
    fun otherServerError_isUnknown() = runTest {
        val repo = failing(HttpStatusCode.InternalServerError, "XX000")

        assertEquals(AppError.Unknown, repo.applyForDate(LocalDate(2026, 10, 26)).appErrorOrNull())
    }

    @Test
    fun offline_isNetworkError() = runTest {
        var fail = false
        val repo = repository {
            if (fail) throw IOException("offline")
            "[]"
        }
        fail = true

        assertEquals(AppError.Network, repo.list().appErrorOrNull())
        assertEquals(AppError.Network, repo.applyForDate(LocalDate(2026, 10, 26)).appErrorOrNull())
    }

    @Test
    fun signedOut_isUnauthorized() = runTest {
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, MockEngine { respond("[]") }, MemorySessionManager())
        val repo = SupabaseMealDefaultsRepository(client.auth, client.postgrest)

        assertEquals(AppError.Unauthorized, repo.list().appErrorOrNull())
    }
}
