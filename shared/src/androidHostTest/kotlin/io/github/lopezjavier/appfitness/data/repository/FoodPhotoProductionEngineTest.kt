package io.github.lopezjavier.appfitness.data.repository

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.AmountEstimate
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.NutritionPer100
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val WEIGHT = """{"estimate":{"estimated_amount":180,"base_unit":"g","confidence":7}}"""
private const val SLOW_ANSWER_MS = 12_000L
private const val HTTP_OK = 200

/**
 * Runs the repository on the production OkHttp engine against a real local server, because MockEngine ignores socket
 * timeouts: the 10 s default read timeout only shows up here (T-133).
 */
class FoodPhotoProductionEngineTest {
    private var server: HttpServer? = null

    @AfterTest
    fun stopServer() {
        server?.stop(0)
    }

    private suspend fun signedIn(delayMs: Long, requestTimeout: Duration): SupabaseFoodPhotoRepository {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/") { exchange ->
            val isFunction = exchange.requestURI.path.contains("/functions/")
            exchange.requestBody.readBytes()
            if (isFunction) Thread.sleep(delayMs)
            exchange.respondJson(if (isFunction) WEIGHT else SESSION)
        }
        http.start()
        server = http
        val config = AppConfig("http://127.0.0.1:${http.address.port}", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, OkHttp.create(), MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseFoodPhotoRepository(client.auth, client.functions, requestTimeout)
    }

    @Test
    fun estimateAmount_succeedsWhenTheServerAnswersAfterTwelveSecondsOnTheOkHttpEngine() = runBlocking {
        val repo = withContext(Dispatchers.IO) { signedIn(SLOW_ANSWER_MS, FOOD_PHOTO_TIMEOUT) }

        val result = withContext(Dispatchers.IO) { repo.estimateAmount(chicken, "QUJD") }

        assertEquals(AmountEstimate(180.0, "g", 7), result.getOrNull(), result.exceptionOrNull()?.toString())
    }

    @Test
    fun estimateAmount_failsWithServiceUnavailableNotNetworkWhenTheServerIsSlowerThanTheTimeout() = runBlocking {
        val repo = withContext(Dispatchers.IO) { signedIn(4_000L, 2.seconds) }

        val result = withContext(Dispatchers.IO) { repo.estimateAmount(chicken, "QUJD") }

        assertEquals(AppError.ServiceUnavailable, result.appErrorOrNull())
    }

    private fun HttpExchange.respondJson(body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(HTTP_OK, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private val chicken = Food(
        id = "f1",
        name = "Pechuga de pollo",
        brand = null,
        baseUnit = "g",
        nutrition = NutritionPer100(kcal = 165.0, proteinG = 31.0, carbsG = 0.0, fatG = 3.6),
        servingLabel = null,
        servingAmount = null,
        portions = emptyList(),
        imageUrl = null,
    )
}
