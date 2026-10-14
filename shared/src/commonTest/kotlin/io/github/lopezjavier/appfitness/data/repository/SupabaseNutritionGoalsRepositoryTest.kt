package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val GOALS_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseNutritionGoalsRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun signedInRepository(response: () -> String): SupabaseNutritionGoalsRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(GOALS_SESSION, HttpStatusCode.OK, headers)
                } else {
                    respond(response(), HttpStatusCode.OK, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseNutritionGoalsRepository(client.auth, client.postgrest)
    }

    @Test
    fun getOwn_withoutARow_isNull() = runTest {
        val repo = signedInRepository { "[]" }

        assertNull(repo.getOwn().getOrThrow())
        val url = requests.last().url.toString()
        assertTrue(url.contains("user_id=eq.user-1"), url)
        assertTrue(url.contains("select=strategy"), url)
    }

    @Test
    fun getOwn_mapsTheRow() = runTest {
        val repo =
            signedInRepository {
                """[{"strategy":"deficit","calorie_adjustment":-400,"target_kcal":2281,""" +
                    """"protein_pct":30,"carbs_pct":45,"fat_pct":25}]"""
            }

        assertEquals(
            NutritionGoals(GoalStrategy.Deficit, -400, 2281, MacroSplit(30, 45, 25)),
            repo.getOwn().getOrThrow(),
        )
    }

    @Test
    fun upsert_postsTheRowWithOnConflictOnUserId() = runTest {
        val repo = signedInRepository { "[]" }

        val result = repo.upsert(NutritionGoals(GoalStrategy.Deficit, -400, 2281))

        assertTrue(result.isSuccess)
        val post = requests.last()
        assertEquals(HttpMethod.Post, post.method)
        assertTrue(post.url.toString().contains("on_conflict=user_id"), post.url.toString())
        val prefer = post.headers["Prefer"].orEmpty()
        assertTrue(prefer.contains("resolution=merge-duplicates"), prefer)
        val body = (post.body as TextContent).text
        assertEquals(
            """{"user_id":"user-1","strategy":"deficit","calorie_adjustment":-400,"target_kcal":2281,""" +
                """"protein_pct":30,"carbs_pct":45,"fat_pct":25}""",
            body.removePrefix("[").removeSuffix("]"),
        )
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

        assertEquals(AppError.Network, repo.getOwn().appErrorOrNull())
        assertEquals(
            AppError.Network,
            repo.upsert(NutritionGoals(GoalStrategy.Maintenance, 0, 2673)).appErrorOrNull(),
        )
    }

    @Test
    fun signedOut_isUnauthorized() = runTest {
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, MockEngine { respond("[]") }, MemorySessionManager())
        val repo = SupabaseNutritionGoalsRepository(client.auth, client.postgrest)

        assertEquals(AppError.Unauthorized, repo.getOwn().appErrorOrNull())
    }
}
