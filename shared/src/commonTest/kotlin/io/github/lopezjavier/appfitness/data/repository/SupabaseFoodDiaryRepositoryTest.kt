package io.github.lopezjavier.appfitness.data.repository

import app.cash.turbine.test
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.NewFoodEntry
import io.github.lopezjavier.appfitness.domain.model.NutritionSnapshot
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
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

private const val DIARY_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val ROW_ONE =
    """{"id":"e1","log_date":"2026-10-19","meal_slot_id":"s1","food_id":"f1","food_name":"Avena",""" +
        """"amount":40.5,"portion_label":null,"kcal":151.2,"protein_g":5.3,"carbs_g":26.8,"fat_g":2.7,""" +
        """"foods":{"base_unit":"g"}}"""

class SupabaseFoodDiaryRepositoryTest {
    private val day = LocalDate(2026, 10, 19)
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun signedInRepository(response: () -> String): SupabaseFoodDiaryRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(DIARY_SESSION, HttpStatusCode.OK, headers)
                } else {
                    respond(response(), HttpStatusCode.OK, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseFoodDiaryRepository(client.auth, client.postgrest)
    }

    @Test
    fun refresh_filtersByDayOrdersByCreationAndEmbedsTheBaseUnit() = runTest {
        val repo = signedInRepository { "[]" }

        repo.refresh(day).getOrThrow()

        val url = requests.last().url.toString()
        assertTrue(url.contains("log_date=eq.2026-10-19"), url)
        assertTrue(url.contains("order=created_at.asc"), url)
        assertTrue(requests.last().url.parameters["select"].orEmpty().contains("foods(base_unit)"), url)
    }

    @Test
    fun refresh_mapsDecimalSnapshotsAndTheBaseUnit() = runTest {
        val repo = signedInRepository { "[$ROW_ONE]" }

        repo.refresh(day).getOrThrow()

        repo.entries(day).test {
            assertEquals(
                listOf(
                    FoodLogEntry(
                        id = "e1",
                        logDate = day,
                        mealSlotId = "s1",
                        foodId = "f1",
                        foodName = "Avena",
                        amount = 40.5,
                        portionLabel = null,
                        baseUnit = "g",
                        kcal = 151.2,
                        proteinG = 5.3,
                        carbsG = 26.8,
                        fatG = 2.7,
                    ),
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun entries_emitNothingUntilTheDayIsLoadedAndThenEmitEachRefresh() = runTest {
        var body = "[]"
        val repo = signedInRepository { body }

        repo.entries(day).test {
            expectNoEvents()
            repo.refresh(day).getOrThrow()
            assertEquals(emptyList(), awaitItem())
            body = "[$ROW_ONE]"
            repo.refresh(day).getOrThrow()
            assertEquals(listOf("e1"), awaitItem().map { it.id })
        }
    }

    @Test
    fun entries_ofAnotherDay_areNotAffected() = runTest {
        val repo = signedInRepository { "[$ROW_ONE]" }

        repo.entries(LocalDate(2026, 10, 18)).test {
            repo.refresh(day).getOrThrow()
            expectNoEvents()
        }
    }

    private fun newEntry(date: LocalDate = day) = NewFoodEntry(
        date = date,
        mealSlotId = "s1",
        foodId = "f1",
        foodName = "Avena",
        amount = 40.5,
        portionLabel = null,
        snapshot = NutritionSnapshot(151.2, 5.3, 26.8, 2.7),
    )

    @Test
    fun addEntry_postsTheSnapshotAndReturnsTheCreatedRow() = runTest {
        val repo = signedInRepository { "[$ROW_ONE]" }

        val created = repo.addEntry(newEntry()).getOrThrow()

        assertEquals("e1", created.id)
        assertEquals("g", created.baseUnit)
        val post = requests.last()
        assertEquals(HttpMethod.Post, post.method)
        assertTrue(post.url.encodedPath.endsWith("/food_log_entries"))
        assertTrue(post.url.parameters["select"].orEmpty().contains("foods(base_unit)"))
        val body = post.body.toByteArray().decodeToString()
        assertTrue(body.contains("\"log_date\":\"2026-10-19\""), body)
        assertTrue(body.contains("\"meal_slot_id\":\"s1\"") && body.contains("\"amount\":40.5"), body)
        assertTrue(body.contains("\"kcal\":151.2") && body.contains("\"fat_g\":2.7"), body)
    }

    @Test
    fun addEntry_reEmitsTheLoadedDayWithoutARefresh() = runTest {
        var body = "[]"
        val repo = signedInRepository { body }
        repo.refresh(day).getOrThrow()

        repo.entries(day).test {
            assertEquals(emptyList(), awaitItem())
            body = "[$ROW_ONE]"
            repo.addEntry(newEntry()).getOrThrow()
            assertEquals(listOf("e1"), awaitItem().map { it.id })
        }
    }

    @Test
    fun addEntry_toADayNeverLoaded_doesNotFabricateAPartialDay() = runTest {
        val repo = signedInRepository { "[$ROW_ONE]" }

        repo.entries(day).test {
            repo.addEntry(newEntry()).getOrThrow()
            expectNoEvents()
        }
    }

    @Test
    fun addEntry_networkError_isMapped() = runTest {
        var fail = false
        val repo =
            signedInRepository {
                if (fail) throw IOException("offline")
                "[]"
            }
        fail = true

        assertEquals(AppError.Network, repo.addEntry(newEntry()).appErrorOrNull())
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

        assertEquals(AppError.Network, repo.refresh(day).appErrorOrNull())
    }

    @Test
    fun signedOut_isUnauthorized() = runTest {
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, MockEngine { respond("[]") }, MemorySessionManager())
        val repo = SupabaseFoodDiaryRepository(client.auth, client.postgrest)

        assertEquals(AppError.Unauthorized, repo.refresh(day).appErrorOrNull())
    }
}
