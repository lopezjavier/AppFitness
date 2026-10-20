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

    private suspend fun signedIn(response: (HttpRequestData) -> String): SupabaseFoodRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(FOOD_SESSION, HttpStatusCode.OK, headers)
                } else {
                    bodies += request.body.toByteArray().decodeToString()
                    respond(response(request), HttpStatusCode.OK, headers)
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
