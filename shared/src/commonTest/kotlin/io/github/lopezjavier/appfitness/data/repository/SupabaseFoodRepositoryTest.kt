package io.github.lopezjavier.appfitness.data.repository

import app.cash.turbine.test
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.FoodPortion
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import io.github.lopezjavier.appfitness.domain.model.NewFood
import io.github.lopezjavier.appfitness.domain.model.NewMicronutrients
import io.github.lopezjavier.appfitness.domain.model.NewPortion
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.port.Clock
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

private const val FOOD_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val EGG =
    """{"id":"f1","name":"Huevo","brand":null,"base_unit":"g","kcal_100":143,"protein_100":12.5,""" +
        """"carbs_100":0.7,"fat_100":9.5,"serving_label":"1 huevo","serving_amount":55,"image_url":null,""" +
        """"portions":[{"id":"p2","label":"clara","amount":33,"position":1},""" +
        """{"id":"p1","label":"huevo","amount":55,"position":0}]}"""

private const val RICE =
    """{"id":"f2","name":"Arroz","brand":"Hacendado","base_unit":"g","kcal_100":130,"food_portions":[]}"""

private const val MILK =
    """{"id":"f3","name":"Leche","base_unit":"ml","kcal_100":45,"food_portions":""" +
        """[{"id":"p9","label":"vaso","amount":250,"position":0}]}"""

private class FixedClock : Clock {
    override fun today() = LocalDate(2026, 10, 20)

    override fun now() = Instant.parse("2026-10-20T10:00:00Z")
}

class SupabaseFoodRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()
    private var status = HttpStatusCode.OK

    private suspend fun signedIn(response: (HttpRequestData) -> String): SupabaseFoodRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(FOOD_SESSION, HttpStatusCode.OK, headers)
                } else {
                    bodies += request.body.toByteArray().decodeToString()
                    respond(response(request), status, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseFoodRepository(client.auth, client.postgrest, client.functions, FixedClock())
    }

    @Test
    fun search_callsTheFunctionWithQueryAndPageAndDecodesPortionsInOrder() = runTest {
        val repo = signedIn { """{"results":[$EGG],"source":"cache"}""" }

        val foods = repo.search("huevo", 2).getOrThrow()

        val request = requests.last()
        assertEquals(HttpMethod.Get, request.method)
        assertTrue(request.url.encodedPath.endsWith("/functions/v1/food-search"), request.url.toString())
        assertEquals("huevo", request.url.parameters["q"])
        assertEquals("2", request.url.parameters["page"])
        assertEquals(
            Food(
                id = "f1",
                name = "Huevo",
                brand = null,
                baseUnit = "g",
                nutrition = NutritionPer100(143.0, 12.5, 0.7, 9.5),
                servingLabel = "1 huevo",
                servingAmount = 55.0,
                portions = listOf(FoodPortion("p1", "huevo", 55.0), FoodPortion("p2", "clara", 33.0)),
                imageUrl = null,
            ),
            foods.single(),
        )
    }

    @Test
    fun recent_dedupesLoggedFoodsKeepingTheMostRecentFirst() = runTest {
        val repo = signedIn { request ->
            if (request.url.encodedPath.endsWith("/food_log_entries")) {
                """[{"food_id":"f2"},{"food_id":"f1"},{"food_id":"f2"}]"""
            } else {
                "[$EGG,$RICE]"
            }
        }

        val foods = repo.recent().getOrThrow()

        assertEquals(listOf("f2", "f1"), foods.map { it.id })
        val entriesUrl = requests.first { it.url.encodedPath.endsWith("/food_log_entries") }.url
        assertEquals("gte.2026-10-06", entriesUrl.parameters["log_date"])
        assertTrue(entriesUrl.parameters["order"].orEmpty().startsWith("created_at.desc"), entriesUrl.toString())
    }

    @Test
    fun mostEaten_keepsTheRpcOrder() = runTest {
        val repo = signedIn { request ->
            if (request.url.encodedPath.endsWith("/rpc/most_eaten_foods")) {
                """[{"food_id":"f3","times_logged":9},{"food_id":"f1","times_logged":4}]"""
            } else {
                "[$EGG,$MILK]"
            }
        }

        val foods = repo.mostEaten(10).getOrThrow()

        assertEquals(listOf("f3", "f1"), foods.map { it.id })
        assertTrue(bodies.any { it.contains("\"p_limit\":10") }, bodies.toString())
        assertEquals("ml", foods.first().baseUnit)
        assertEquals(FoodPortion("p9", "vaso", 250.0), foods.first().portions.single())
    }

    @Test
    fun favourites_decodeTheEmbeddedFoodAndPublishTheIds() = runTest {
        val repo = signedIn { """[{"food_id":"f1","created_at":"2026-10-20T10:00:00Z","foods":$EGG}]""" }

        val foods = repo.favourites().getOrThrow()

        assertEquals(listOf("f1"), foods.map { it.id })
        repo.favouriteIds().test { assertEquals(setOf("f1"), awaitItem()) }
    }

    @Test
    fun setFavourite_true_postsTheFoodAndUpdatesTheIds() = runTest {
        val repo = signedIn { "[]" }

        repo.setFavourite("f1", true).getOrThrow()

        val request = requests.last()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/favorite_foods"))
        assertTrue(bodies.last().contains("\"food_id\":\"f1\""), bodies.last())
        repo.favouriteIds().test { assertEquals(setOf("f1"), awaitItem()) }
    }

    @Test
    fun setFavourite_false_deletesTheFoodAndUpdatesTheIds() = runTest {
        val repo = signedIn { "[]" }
        repo.setFavourite("f1", true).getOrThrow()

        repo.setFavourite("f1", false).getOrThrow()

        val request = requests.last()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("eq.f1", request.url.parameters["food_id"])
        repo.favouriteIds().test { assertEquals(emptySet(), awaitItem()) }
    }

    @Test
    fun getById_returnsTheFood_orNotFound() = runTest {
        var body = "[$EGG]"
        val repo = signedIn { body }

        assertEquals("f1", repo.getById("f1").getOrThrow().id)
        assertEquals("eq.f1", requests.last().url.parameters["id"])

        body = "[]"
        assertEquals(AppError.NotFound, repo.getById("missing").appErrorOrNull())
    }

    @Test
    fun getById_ofAFoodSeenInASearch_needsNoRequest() = runTest {
        val repo = signedIn { """{"results":[$EGG],"source":"cache"}""" }
        repo.search("huevo").getOrThrow()
        val before = requests.size

        assertEquals("Huevo", repo.getById("f1").getOrThrow().name)

        assertEquals(before, requests.size)
    }

    @Test
    fun findByBarcode_found_callsTheFunctionWithTheCode() = runTest {
        val repo = signedIn { """{"food":$MILK}""" }

        val food = repo.findByBarcode("8410000000001").getOrThrow()

        val request = requests.last()
        assertEquals(HttpMethod.Get, request.method)
        assertTrue(request.url.encodedPath.endsWith("/functions/v1/food-barcode"), request.url.toString())
        assertEquals("8410000000001", request.url.parameters["code"])
        assertEquals("ml", food?.baseUnit)
        assertEquals("f3", food?.id)
    }

    @Test
    fun findByBarcode_404_isSuccessWithNull() = runTest {
        status = HttpStatusCode.NotFound
        val repo = signedIn { """{"error":{"code":"FOOD_NOT_FOUND","message":"x"}}""" }

        assertEquals(Result.success(null), repo.findByBarcode("8410000000001"))
    }

    @Test
    fun findByBarcode_serverError_isAFailure() = runTest {
        status = HttpStatusCode.InternalServerError
        val repo = signedIn { """{"error":{"code":"INTERNAL"}}""" }

        assertTrue(repo.findByBarcode("8410000000001").isFailure)
    }

    @Test
    fun findByBarcode_networkError_isNetwork() = runTest {
        var fail = false
        val repo = signedIn { if (fail) throw IOException("offline") else "[]" }
        fail = true

        assertEquals(AppError.Network, repo.findByBarcode("8410000000001").appErrorOrNull())
    }

    @Test
    fun findByBarcode_remembersTheFood() = runTest {
        val repo = signedIn { """{"food":$EGG}""" }
        repo.findByBarcode("123").getOrThrow()
        val before = requests.size

        assertEquals("f1", repo.getById("f1").getOrThrow().id)

        assertEquals(before, requests.size)
    }

    @Test
    fun createFood_sendsTheRpcAndReturnsTheStoredFood() = runTest {
        val repo = signedIn { request ->
            if (request.url.encodedPath.endsWith("/rpc/create_personal_food")) "\"f1\"" else "[$EGG]"
        }

        val created = repo.createFood(
            NewFood(
                name = " Tortilla ",
                brand = null,
                baseUnit = "g",
                nutrition = NutritionPer100(190.0, 7.0, 15.0, 11.0),
                barcode = "8410000000001",
                portions = listOf(NewPortion("1 ración", 250.0), NewPortion("mitad", 125.0)),
            ),
        ).getOrThrow()

        val rpcBody = bodies.first { it.contains("p_name") }
        assertTrue(rpcBody.contains("\"p_name\":\"Tortilla\""), rpcBody)
        assertTrue(rpcBody.contains("\"p_base_unit\":\"g\""), rpcBody)
        assertTrue(rpcBody.contains("\"p_barcode\":\"8410000000001\""), rpcBody)
        assertTrue(rpcBody.contains("\"p_kcal\":190.0"), rpcBody)
        assertTrue(
            rpcBody.indexOf("1 ración") in 0 until rpcBody.indexOf("mitad"),
            "portions keep their order: $rpcBody",
        )
        assertEquals("f1", created.id)
        assertEquals("eq.f1", requests.last().url.parameters["id"])
    }

    private val milkFood = Food(
        id = "f3",
        name = "Leche",
        brand = null,
        baseUnit = "ml",
        nutrition = NutritionPer100(45.0, 0.0, 0.0, 0.0),
        servingLabel = null,
        servingAmount = null,
        portions = emptyList(),
        imageUrl = null,
    )

    @Test
    fun micronutrients_ofAnOffFood_readsOnlyTheNutrimentsPathAndMapsPer100ml() = runTest {
        val repo = signedIn {
            """[{"source":"off","micronutrients":null,"nutriments":{"sugars_100ml":4.8,"vitamin-c_100g":0.012}}]"""
        }

        val result = repo.micronutrients(milkFood).getOrThrow()

        val request = requests.last()
        assertTrue(request.url.encodedPath.endsWith("/foods"), request.url.toString())
        assertEquals("eq.f3", request.url.parameters["id"])
        assertEquals("source,micronutrients,nutriments:off_data->nutriments", request.url.parameters["select"])
        assertEquals(
            listOf(
                Micronutrient(MicronutrientKind.Sugars, 4.8, MicronutrientUnit.G),
                Micronutrient(MicronutrientKind.VitaminC, 12.0, MicronutrientUnit.MG),
            ),
            result.items,
        )
    }

    @Test
    fun micronutrients_ofAPersonalFood_mapsTheTypedValues() = runTest {
        val repo = signedIn {
            """[{"source":"user","micronutrients":{"fiber_100g":3.0,"salt_100g":0.4},"nutriments":null}]"""
        }

        val result = repo.micronutrients(milkFood.copy(baseUnit = "g")).getOrThrow()

        assertEquals(listOf(MicronutrientKind.Fiber, MicronutrientKind.Salt), result.items.map { it.kind })
    }

    @Test
    fun micronutrients_ofAFoodWithoutData_isEmpty() = runTest {
        val repo = signedIn { """[{"source":"off","micronutrients":null,"nutriments":null}]""" }

        assertTrue(repo.micronutrients(milkFood).getOrThrow().isEmpty)
    }

    @Test
    fun micronutrients_foodNotFound_isNotFound() = runTest {
        val repo = signedIn { "[]" }

        assertEquals(AppError.NotFound, repo.micronutrients(milkFood).appErrorOrNull())
    }

    @Test
    fun micronutrients_areCachedPerFood() = runTest {
        val repo = signedIn { """[{"source":"off","nutriments":{"fiber_100g":2.0}}]""" }
        repo.micronutrients(milkFood).getOrThrow()
        val before = requests.size

        repo.micronutrients(milkFood).getOrThrow()

        assertEquals(before, requests.size)
    }

    @Test
    fun micronutrients_emptyResultIsNotCached_soAFoodRefilledLaterIsReread() = runTest {
        var body = """[{"source":"off","micronutrients":null,"nutriments":null}]"""
        val repo = signedIn { body }
        assertTrue(repo.micronutrients(milkFood).getOrThrow().isEmpty)

        body = """[{"source":"off","micronutrients":null,"nutriments":{"fiber_100ml":1.5}}]"""
        val second = repo.micronutrients(milkFood).getOrThrow()

        assertEquals(listOf(MicronutrientKind.Fiber), second.items.map { it.kind })
    }

    @Test
    fun micronutrients_realAliasedOffNutrimentsWithMixedValueTypes_areMapped() = runTest {
        val repo = signedIn {
            """[{"source":"off","micronutrients":null,"nutriments":{"energy-kcal_100g":45,""" +
                """"sugars_100ml":"4.8","fiber_100ml":null,"salt_100ml":0.1,"nova-group":4,"vitamin-c_100g":0.012}}]"""
        }

        val kinds = repo.micronutrients(milkFood).getOrThrow().items.map { it.kind }

        assertTrue(MicronutrientKind.Sugars in kinds && MicronutrientKind.Salt in kinds, kinds.toString())
        assertTrue(MicronutrientKind.Fiber !in kinds, kinds.toString())
    }

    @Test
    fun micronutrients_nutrimentsArrivingAsAJsonString_areStillRead() = runTest {
        val repo = signedIn {
            """[{"source":"off","micronutrients":null,"nutriments":"{\"fiber_100ml\":1.5}"}]"""
        }

        assertEquals(listOf(MicronutrientKind.Fiber), repo.micronutrients(milkFood).getOrThrow().items.map { it.kind })
    }

    @Test
    fun micronutrients_typedValuesAsStrings_areStillRead() = runTest {
        val repo = signedIn {
            """[{"source":"user","micronutrients":{"fiber_100g":"3.0"},"nutriments":null}]"""
        }

        val result = repo.micronutrients(milkFood.copy(baseUnit = "g")).getOrThrow()

        assertEquals(listOf(MicronutrientKind.Fiber), result.items.map { it.kind })
    }

    @Test
    fun micronutrients_unexpectedNutrimentsShape_isEmptyNotAFailure() = runTest {
        val repo = signedIn { """[{"source":"off","micronutrients":null,"nutriments":[1,2]}]""" }

        assertTrue(repo.micronutrients(milkFood).getOrThrow().isEmpty)
    }

    @Test
    fun listAndSearchRequests_stayLean() = runTest {
        val repo = signedIn { request ->
            if (request.url.encodedPath.endsWith("/food_log_entries")) """[{"food_id":"f1"}]""" else "[$EGG]"
        }

        repo.recent().getOrThrow()

        val select = requests.last().url.parameters["select"].orEmpty()
        assertTrue(!select.contains("off_data") && !select.contains("micronutrients"), select)
    }

    @Test
    fun createFood_sendsOnlyTheTypedMicronutrients() = runTest {
        val repo = signedIn { request ->
            if (request.url.encodedPath.endsWith("/rpc/create_personal_food")) "\"f1\"" else "[$EGG]"
        }

        repo.createFood(
            NewFood(
                "x",
                null,
                "g",
                NutritionPer100(1.0, 0.0, 0.0, 0.0),
                null,
                emptyList(),
                NewMicronutrients(sugars = 4.5, fiber = 2.0),
            ),
        ).getOrThrow()

        val rpcBody = bodies.first { it.contains("p_name") }
        assertTrue(rpcBody.contains("\"p_micronutrients\":{\"sugars_100g\":4.5,\"fiber_100g\":2.0}"), rpcBody)
    }

    @Test
    fun createFood_withoutTypedMicronutrients_sendsNone() = runTest {
        val repo = signedIn { request ->
            if (request.url.encodedPath.endsWith("/rpc/create_personal_food")) "\"f1\"" else "[$EGG]"
        }

        repo.createFood(NewFood("x", null, "g", NutritionPer100(1.0, 0.0, 0.0, 0.0), null, emptyList())).getOrThrow()

        val rpcBody = bodies.first { it.contains("p_name") }
        assertTrue(!rpcBody.contains("p_micronutrients\":{"), rpcBody)
    }

    @Test
    fun createFood_failure_propagatesAndRemembersNothing() = runTest {
        var fail = true
        val repo = signedIn { request ->
            if (fail && request.url.encodedPath.endsWith("/rpc/create_personal_food")) throw IOException("offline")
            if (request.url.encodedPath.endsWith("/rpc/create_personal_food")) "\"f1\"" else "[$EGG]"
        }
        val food = NewFood("x", null, "g", NutritionPer100(1.0, 0.0, 0.0, 0.0), null, emptyList())

        assertEquals(AppError.Network, repo.createFood(food).appErrorOrNull())

        fail = false
        val before = requests.size
        repo.getById("f1").getOrThrow()
        assertTrue(requests.size > before, "the food was not cached by the failed call")
    }

    @Test
    fun networkErrors_areMapped() = runTest {
        var fail = false
        val repo = signedIn { if (fail) throw IOException("offline") else "[]" }
        fail = true

        assertEquals(AppError.Network, repo.recent().appErrorOrNull())
        assertEquals(AppError.Network, repo.search("x").appErrorOrNull())
    }

    @Test
    fun signedOut_isUnauthorized() = runTest {
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, MockEngine { respond("[]") }, MemorySessionManager())
        val repo = SupabaseFoodRepository(client.auth, client.postgrest, client.functions, FixedClock())

        assertEquals(AppError.Unauthorized, repo.search("x").appErrorOrNull())
        assertEquals(AppError.Unauthorized, repo.setFavourite("f1", true).appErrorOrNull())
    }
}
