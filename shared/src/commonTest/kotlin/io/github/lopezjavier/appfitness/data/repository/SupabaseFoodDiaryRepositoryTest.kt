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
import io.github.lopezjavier.appfitness.domain.model.UpdatedFoodEntry
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

private const val ROW_TWO =
    """{"id":"e2","log_date":"2026-10-19","meal_slot_id":"s1","food_id":"f1","food_name":"Avena",""" +
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

    private val editedRow =
        """{"id":"e1","log_date":"2026-10-19","meal_slot_id":"s2","food_id":"f1","food_name":"Avena",""" +
            """"amount":20.0,"portion_label":null,"kcal":74.0,"protein_g":2.6,"carbs_g":13.2,"fat_g":1.3,""" +
            """"foods":{"base_unit":"g"}}"""

    private val update = UpdatedFoodEntry("s2", 20.0, null, NutritionSnapshot(74.0, 2.6, 13.2, 1.3))

    @Test
    fun updateEntry_patchesTheRowByIdWithTheNewValuesAndReturnsIt() = runTest {
        val repo = signedInRepository { "[$editedRow]" }

        val updated = repo.updateEntry("e1", update).getOrThrow()

        assertEquals("s2", updated.mealSlotId)
        assertEquals(20.0, updated.amount)
        val patch = requests.last()
        assertEquals(HttpMethod.Patch, patch.method)
        assertTrue(patch.url.encodedPath.endsWith("/food_log_entries"))
        assertEquals("eq.e1", patch.url.parameters["id"])
        assertTrue(patch.url.parameters["select"].orEmpty().contains("foods(base_unit)"))
        val body = patch.body.toByteArray().decodeToString()
        assertTrue(body.contains("\"meal_slot_id\":\"s2\"") && body.contains("\"amount\":20.0"), body)
        assertTrue(body.contains("\"kcal\":74.0") && body.contains("\"protein_g\":2.6"), body)
        assertTrue(body.contains("\"portion_label\":null"), body)
        assertTrue(!body.contains("log_date") && !body.contains("food_id"), body)
    }

    @Test
    fun updateEntry_replacesTheEntryInItsLoadedDay() = runTest {
        var body = "[$ROW_ONE]"
        val repo = signedInRepository { body }
        repo.refresh(day).getOrThrow()

        repo.entries(day).test {
            assertEquals(40.5, awaitItem().single().amount)
            body = "[$editedRow]"
            repo.updateEntry("e1", update).getOrThrow()
            val after = awaitItem().single()
            assertEquals("s2", after.mealSlotId)
            assertEquals(74.0, after.kcal)
        }
    }

    @Test
    fun updateEntry_networkError_isMappedAndKeepsTheCache() = runTest {
        var fail = false
        val repo =
            signedInRepository {
                if (fail) throw IOException("offline")
                "[$ROW_ONE]"
            }
        repo.refresh(day).getOrThrow()
        fail = true

        assertEquals(AppError.Network, repo.updateEntry("e1", update).appErrorOrNull())
        repo.entries(day).test { assertEquals(40.5, awaitItem().single().amount) }
    }

    @Test
    fun deleteEntry_deletesByIdAndRemovesItFromTheCache() = runTest {
        var body = "[$ROW_ONE]"
        val repo = signedInRepository { body }
        repo.refresh(day).getOrThrow()

        repo.entries(day).test {
            assertEquals(listOf("e1"), awaitItem().map { it.id })
            body = ""
            repo.deleteEntry("e1").getOrThrow()
            assertEquals(emptyList(), awaitItem())
        }
        val delete = requests.last()
        assertEquals(HttpMethod.Delete, delete.method)
        assertEquals("eq.e1", delete.url.parameters["id"])
    }

    @Test
    fun deleteEntry_networkError_isMappedAndKeepsTheCache() = runTest {
        var fail = false
        val repo =
            signedInRepository {
                if (fail) throw IOException("offline")
                "[$ROW_ONE]"
            }
        repo.refresh(day).getOrThrow()
        fail = true

        assertEquals(AppError.Network, repo.deleteEntry("e1").appErrorOrNull())
        repo.entries(day).test { assertEquals(listOf("e1"), awaitItem().map { it.id }) }
    }

    private val deleted = FoodLogEntry(
        id = "e1",
        logDate = day,
        mealSlotId = "s1",
        foodId = "f1",
        foodName = "Avena",
        amount = 40.5,
        portionLabel = "1 taza",
        baseUnit = "g",
        kcal = 151.2,
        proteinG = 5.3,
        carbsG = 26.8,
        fatG = 2.7,
    )

    @Test
    fun restoreEntry_postsTheSameIdAndValuesAndReAddsItToTheCache() = runTest {
        var body = "[]"
        val repo = signedInRepository { body }
        repo.refresh(day).getOrThrow()

        repo.entries(day).test {
            assertEquals(emptyList(), awaitItem())
            body = "[$ROW_ONE]"
            val restored = repo.restoreEntry(deleted).getOrThrow()
            assertEquals("e1", restored.id)
            assertEquals(listOf("e1"), awaitItem().map { it.id })
        }
        val post = requests.last()
        assertEquals(HttpMethod.Post, post.method)
        val sent = post.body.toByteArray().decodeToString()
        assertTrue(sent.contains("\"id\":\"e1\"") && sent.contains("\"food_id\":\"f1\""), sent)
        assertTrue(sent.contains("\"portion_label\":\"1 taza\"") && sent.contains("\"kcal\":151.2"), sent)
        assertTrue(sent.contains("\"log_date\":\"2026-10-19\""), sent)
    }

    @Test
    fun addEntry_doesNotSendAnId() = runTest {
        val repo = signedInRepository { "[$ROW_ONE]" }

        repo.addEntry(newEntry()).getOrThrow()

        assertTrue(!requests.last().body.toByteArray().decodeToString().contains("\"id\""))
    }

    @Test
    fun addEntries_isOneRequestWithAnArrayBodyAndReEmitsTheLoadedDay() = runTest {
        var body = "[]"
        val repo = signedInRepository { body }
        repo.refresh(day).getOrThrow()
        val before = requests.size

        repo.entries(day).test {
            assertEquals(emptyList(), awaitItem())
            body = "[$ROW_ONE,$ROW_TWO]"
            val created = repo.addEntries(listOf(newEntry(), newEntry())).getOrThrow()
            assertEquals(listOf("e1", "e2"), created.map { it.id })
            assertEquals(listOf("e1", "e2"), awaitItem().map { it.id })
        }
        val posts = requests.drop(before).filter { it.method == HttpMethod.Post }
        assertEquals(1, posts.size)
        val sent = posts.single().body.toByteArray().decodeToString()
        assertTrue(sent.startsWith("[") && sent.split("\"food_id\"").size == 3, sent)
        assertTrue(!sent.contains("\"id\""), sent)
    }

    @Test
    fun addEntries_toADayNeverLoaded_doesNotFabricateAPartialDay() = runTest {
        val repo = signedInRepository { "[$ROW_ONE]" }

        repo.entries(day).test {
            repo.addEntries(listOf(newEntry())).getOrThrow()
            expectNoEvents()
        }
    }

    @Test
    fun addEntries_ofNothing_sendsNoRequest() = runTest {
        val repo = signedInRepository { "[]" }
        val before = requests.size

        assertEquals(emptyList(), repo.addEntries(emptyList()).getOrThrow())
        assertEquals(before, requests.size)
    }

    @Test
    fun addEntries_networkError_isMapped() = runTest {
        var fail = false
        val repo =
            signedInRepository {
                if (fail) throw IOException("offline")
                "[]"
            }
        fail = true

        assertEquals(AppError.Network, repo.addEntries(listOf(newEntry())).appErrorOrNull())
    }

    @Test
    fun deleteEntries_isOneInFilterRequestAndRemovesThemFromTheCache() = runTest {
        var body = "[$ROW_ONE,$ROW_TWO]"
        val repo = signedInRepository { body }
        repo.refresh(day).getOrThrow()
        val before = requests.size

        repo.entries(day).test {
            assertEquals(listOf("e1", "e2"), awaitItem().map { it.id })
            body = ""
            repo.deleteEntries(listOf("e1", "e2")).getOrThrow()
            assertEquals(emptyList(), awaitItem())
        }
        val deletes = requests.drop(before).filter { it.method == HttpMethod.Delete }
        assertEquals(1, deletes.size)
        assertEquals("in.(e1,e2)", deletes.single().url.parameters["id"])
    }

    @Test
    fun deleteEntries_networkError_isMappedAndKeepsTheCache() = runTest {
        var fail = false
        val repo =
            signedInRepository {
                if (fail) throw IOException("offline")
                "[$ROW_ONE]"
            }
        repo.refresh(day).getOrThrow()
        fail = true

        assertEquals(AppError.Network, repo.deleteEntries(listOf("e1")).appErrorOrNull())
        repo.entries(day).test { assertEquals(listOf("e1"), awaitItem().map { it.id }) }
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
