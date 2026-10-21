package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.FoodDto
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
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
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TEMPLATE_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val TEMPLATES_JSON =
    """[{"id":"t1","name":"Desayuno A","meal_slot_id":"s1","meal_template_items":[""" +
        """{"food_id":"shake","amount":300.0,"portion_label":"1 vaso","position":1,"foods":""" +
        """{"id":"shake","name":"Batido","base_unit":"ml","kcal_100":80.0,"food_portions":[]}},""" +
        """{"food_id":"eggs","amount":120.0,"portion_label":null,"position":0,"foods":""" +
        """{"id":"eggs","name":"Huevos","base_unit":"g","kcal_100":150.0,"food_portions":[]}}]}]"""

class SupabaseMealTemplateRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun repository(
        status: HttpStatusCode = HttpStatusCode.OK,
        response: () -> String,
    ): SupabaseMealTemplateRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(TEMPLATE_SESSION, HttpStatusCode.OK, headers)
                } else {
                    respond(response(), status, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseMealTemplateRepository(client.auth, client.postgrest)
    }

    private suspend fun failing(status: HttpStatusCode, code: String): SupabaseMealTemplateRepository =
        repository(status) { """{"code":"$code","message":"boom","details":null,"hint":null}""" }

    private val items = listOf(NewTemplateItem("eggs", 120.0, null), NewTemplateItem("shake", 300.0, "1 vaso"))

    @Test
    fun list_selectsTemplatesWithSharedFoodColumnsOrderedByName() = runTest {
        val repo = repository { "[]" }

        repo.list().getOrThrow()

        val url = requests.last().url
        assertTrue(url.encodedPath.endsWith("/meal_templates"), url.toString())
        assertTrue(url.toString().contains("order=name.asc"), url.toString())
        val select = url.parameters["select"].orEmpty()
        assertTrue(select.contains("foods(${FoodDto.COLUMNS})"), select)
    }

    @Test
    fun list_mapsItemsOrderedByPositionWithTheirFoods() = runTest {
        val repo = repository { TEMPLATES_JSON }

        val template = repo.list().getOrThrow().single()

        assertEquals("Desayuno A", template.name)
        assertEquals("s1", template.mealSlotId)
        assertEquals(listOf("eggs", "shake"), template.items.map { it.food.id })
        assertEquals(listOf(120.0, 300.0), template.items.map { it.amount })
        assertEquals(listOf(null, "1 vaso"), template.items.map { it.portionLabel })
    }

    @Test
    fun save_newTemplate_callsRpcWithNullIdAndItemsInOrder() = runTest {
        val repo = repository { "\"t9\"" }

        val id = repo.save(null, "  Desayuno A ", "s1", items).getOrThrow()

        assertEquals("t9", id)
        val request = requests.last()
        assertTrue(request.url.encodedPath.endsWith("/rpc/save_meal_template"), request.url.toString())
        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        assertEquals(JsonNull, body["p_template_id"])
        assertEquals("Desayuno A", body["p_name"]?.jsonPrimitive?.content)
        assertEquals("s1", body["p_meal_slot_id"]?.jsonPrimitive?.content)
        val sent = body["p_items"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("eggs", "shake"), sent.map { it["food_id"]?.jsonPrimitive?.content })
        assertEquals(JsonNull, sent[0]["portion_label"])
        assertEquals("1 vaso", sent[1]["portion_label"]?.jsonPrimitive?.content)
    }

    @Test
    fun save_existingTemplate_sendsItsId() = runTest {
        val repo = repository { "\"t1\"" }

        repo.save("t1", "Desayuno A", null, items).getOrThrow()

        val body = Json.parseToJsonElement((requests.last().body as TextContent).text).jsonObject
        assertEquals("t1", body["p_template_id"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, body["p_meal_slot_id"])
    }

    @Test
    fun save_duplicateName_isNameAlreadyExists() = runTest {
        val repo = failing(HttpStatusCode.Conflict, "23505")

        assertEquals(AppError.NameAlreadyExists, repo.save(null, "A", null, items).appErrorOrNull())
    }

    @Test
    fun save_invalidInput_isValidation() = runTest {
        val repo = failing(HttpStatusCode.BadRequest, "22023")

        assertEquals(AppError.Validation, repo.save(null, "A", null, items).appErrorOrNull())
    }

    @Test
    fun save_foreignTemplate_isNotFound() = runTest {
        val repo = failing(HttpStatusCode.BadRequest, "P0002")

        assertEquals(AppError.NotFound, repo.save("other", "A", null, items).appErrorOrNull())
    }

    @Test
    fun save_otherServerError_isUnknown() = runTest {
        val repo = failing(HttpStatusCode.InternalServerError, "XX000")

        assertEquals(AppError.Unknown, repo.save(null, "A", null, items).appErrorOrNull())
    }

    @Test
    fun rename_patchesTheNameOfThatTemplate() = runTest {
        val repo = repository(HttpStatusCode.NoContent) { "" }

        val result = repo.rename("t1", " Cena ")

        assertTrue(result.isSuccess)
        val request = requests.last()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("eq.t1", request.url.parameters["id"])
        val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        assertEquals("Cena", body["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun rename_duplicateName_isNameAlreadyExists() = runTest {
        val repo = failing(HttpStatusCode.Conflict, "23505")

        assertEquals(AppError.NameAlreadyExists, repo.rename("t1", "A").appErrorOrNull())
    }

    @Test
    fun delete_deletesThatTemplate() = runTest {
        val repo = repository(HttpStatusCode.NoContent) { "" }

        val result = repo.delete("t1")

        assertTrue(result.isSuccess)
        assertEquals(HttpMethod.Delete, requests.last().method)
        assertEquals("eq.t1", requests.last().url.parameters["id"])
    }

    @Test
    fun offline_isNetworkError() = runTest {
        var fail = false
        val repo =
            repository {
                if (fail) throw IOException("offline")
                "[]"
            }
        fail = true

        assertEquals(AppError.Network, repo.list().appErrorOrNull())
        assertEquals(AppError.Network, repo.delete("t1").appErrorOrNull())
    }

    @Test
    fun signedOut_isUnauthorized() = runTest {
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, MockEngine { respond("[]") }, MemorySessionManager())
        val repo = SupabaseMealTemplateRepository(client.auth, client.postgrest)

        assertEquals(AppError.Unauthorized, repo.list().appErrorOrNull())
    }
}
