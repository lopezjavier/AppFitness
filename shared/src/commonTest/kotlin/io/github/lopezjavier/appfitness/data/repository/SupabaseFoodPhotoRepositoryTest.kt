package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoConfidence
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoDraft
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
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
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val PHOTO_SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val TORTILLA =
    """{"draft":{"name":"Tortilla de patatas","brand":null,"base_unit":"g","nutrition":{"kcal":180,""" +
        """"protein_g":6,"carbs_g":15,"fat_g":10},"micronutrients":{"fiber_100g":1.5},"estimated_grams":250,""" +
        """"confidence":"medium"}}"""

private const val ERROR_BODY = """{"error":{"code":"X","message":"generic"}}"""

class SupabaseFoodPhotoRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()

    private suspend fun signedIn(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = TORTILLA,
        fail: Boolean = false,
    ): SupabaseFoodPhotoRepository {
        val engine =
            MockEngine { request ->
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (request.url.encodedPath.contains("/auth/")) {
                    respond(PHOTO_SESSION, HttpStatusCode.OK, headers)
                } else {
                    requests += request
                    bodies += request.body.toByteArray().decodeToString()
                    if (fail) throw IOException("offline")
                    respond(body, status, headers)
                }
            }
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseFoodPhotoRepository(client.auth, client.functions)
    }

    @Test
    fun estimate_postsImageModeAndCommentAndDecodesTheDraft() = runTest {
        val repo = signedIn()

        val draft = repo.estimate(FoodPhotoMode.Dish, "QUJD", "  sin sal  ").getOrThrow()

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertTrue(request.url.encodedPath.endsWith("/functions/v1/food-photo"), request.url.toString())
        assertEquals("""{"image_base64":"QUJD","mode":"dish","comment":"sin sal"}""", bodies.single())
        assertEquals(
            FoodPhotoDraft(
                name = "Tortilla de patatas",
                brand = null,
                baseUnit = "g",
                kcal = 180.0,
                protein = 6.0,
                carbs = 15.0,
                fat = 10.0,
                micronutrients = mapOf("fiber_100g" to 1.5),
                estimatedGrams = 250.0,
                confidence = FoodPhotoConfidence.Medium,
            ),
            draft,
        )
    }

    @Test
    fun estimate_omitsTheCommentWhenNull() = runTest {
        val repo = signedIn()

        repo.estimate(FoodPhotoMode.Dish, "QUJD", null).getOrThrow()

        assertEquals("""{"image_base64":"QUJD","mode":"dish"}""", bodies.single())
    }

    @Test
    fun estimate_neverSendsTheCommentForLabelOrBarcode() = runTest {
        val repo = signedIn()

        repo.estimate(FoodPhotoMode.Label, "QUJD", "nota").getOrThrow()
        repo.estimate(FoodPhotoMode.Barcode, "QUJD", "nota").getOrThrow()

        assertEquals("""{"image_base64":"QUJD","mode":"label"}""", bodies[0])
        assertEquals("""{"image_base64":"QUJD","mode":"barcode"}""", bodies[1])
        assertFalse(bodies.any { it.contains("comment") })
    }

    @Test
    fun estimate_keepsNullsOfALabelWithAbsentValues() = runTest {
        val body = """{"draft":{"name":"Yogur","base_unit":"ml","nutrition":{"kcal":62,"protein_g":null},""" +
            """"micronutrients":{},"estimated_grams":null,"confidence":null}}"""
        val repo = signedIn(body = body)

        val draft = repo.estimate(FoodPhotoMode.Label, "QUJD", null).getOrThrow()

        assertEquals(62.0, draft.kcal)
        assertEquals(null, draft.protein)
        assertEquals(null, draft.carbs)
        assertEquals("ml", draft.baseUnit)
        assertEquals(null, draft.confidence)
    }

    @Test
    fun estimate_mapsEachErrorStatusToItsAppError() = runTest {
        val expected = mapOf(
            HttpStatusCode.BadRequest to AppError.Validation,
            HttpStatusCode.PayloadTooLarge to AppError.Validation,
            HttpStatusCode.Unauthorized to AppError.Unauthorized,
            HttpStatusCode.UnprocessableEntity to AppError.PhotoNotRecognized,
            HttpStatusCode.TooManyRequests to AppError.QuotaExceeded,
            HttpStatusCode.InternalServerError to AppError.ServiceUnavailable,
            HttpStatusCode.BadGateway to AppError.ServiceUnavailable,
            HttpStatusCode.ServiceUnavailable to AppError.ServiceUnavailable,
            HttpStatusCode.Forbidden to AppError.Unknown,
        )

        expected.forEach { (status, error) ->
            val result = signedIn(status = status, body = ERROR_BODY).estimate(FoodPhotoMode.Dish, "QUJD", null)

            assertEquals(error, result.appErrorOrNull(), "status ${status.value}")
        }
    }

    @Test
    fun estimate_mapsATransportFailureToNetwork() = runTest {
        val result = signedIn(fail = true).estimate(FoodPhotoMode.Dish, "QUJD", null)

        assertEquals(AppError.Network, result.appErrorOrNull())
    }

    @Test
    fun estimate_mapsAnUndecodableSuccessBodyToUnknown() = runTest {
        val result = signedIn(body = """{"unexpected":true}""").estimate(FoodPhotoMode.Dish, "QUJD", null)

        assertEquals(AppError.Unknown, result.appErrorOrNull())
    }
}
