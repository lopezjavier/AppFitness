package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.ManualWeightSave
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
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
import kotlin.time.Instant

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""
private const val ROW =
    """[{"id":"row-1","measured_at":"2026-10-14T10:00:00+00:00","weight_kg":74.2,"source":"manual"}]"""
private val MEASURED_AT = Instant.parse("2026-10-14T12:30:00Z")

class SupabaseBodyMeasurementRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    /** [rows] is the body of every GET; writes answer an empty success. [failing] makes every data call fail. */
    private suspend fun signedIn(
        rows: () -> String = { "[]" },
        failing: () -> Boolean = { false },
        rejectWritesWith: String? = null,
    ) = MockEngine { request ->
        requests += request
        val headers = headersOf(HttpHeaders.ContentType, "application/json")
        when {
            request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)

            failing() -> throw IOException("offline")

            rejectWritesWith != null && request.method != HttpMethod.Get -> respond(
                """{"code":"$rejectWritesWith","message":"boom","details":null,"hint":null}""",
                HttpStatusCode.BadRequest,
                headers,
            )

            request.method == HttpMethod.Get -> respond(rows(), HttpStatusCode.OK, headers)

            request.url.encodedPath.endsWith("/rpc/save_body_measurement") ->
                respond("\"row-1\"", HttpStatusCode.OK, headers)

            else -> respond("", HttpStatusCode.Created, headers)
        }
    }.let { engine ->
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, engine, MemorySessionManager())
        SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        SupabaseBodyMeasurementRepository(client.auth, client.postgrest)
    }

    private fun signedOut(): SupabaseBodyMeasurementRepository {
        val config = AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true)
        val client = SupabaseClientFactory.create(config, MockEngine { respond("[]") }, MemorySessionManager())
        return SupabaseBodyMeasurementRepository(client.auth, client.postgrest)
    }

    private val newSave = ManualWeightSave(
        id = null,
        weightKg = 74.2,
        bodyFatPct = null,
        measuredAt = MEASURED_AT,
        dayStart = Instant.parse("2026-10-13T22:00:00Z"),
        dayEnd = Instant.parse("2026-10-14T22:00:00Z"),
    )

    private fun writes() = requests.filter { it.method != HttpMethod.Get && !it.url.encodedPath.contains("/auth/") }

    @Test
    fun onboardingRowQuery_isTheEarliestManualRow() = runTest {
        val repo = signedIn(rows = { ROW })

        val row = repo.getOnboardingWeight().getOrThrow()

        assertEquals(
            BodyMeasurement("row-1", Instant.parse("2026-10-14T10:00:00Z"), 74.2, MeasurementSource.Manual),
            row,
        )
        val url = requests.last().url.toString()
        assertTrue(url.contains("source=eq.manual"), url)
        assertTrue(url.contains("order=created_at.asc,id.asc") || url.contains("order=created_at.asc"), url)
        assertTrue(url.contains("limit=1"), url)
    }

    @Test
    fun insertThenUpdate_theSecondSaveFindsTheRowAndPatchesIt() = runTest {
        var stored = "[]"
        val repo = signedIn(rows = { stored })

        assertTrue(repo.saveOnboardingWeight(74.2, MEASURED_AT).isSuccess)
        val post = writes().single()
        assertEquals(HttpMethod.Post, post.method)
        assertEquals(
            """[{"weight_kg":74.2,"measured_at":"2026-10-14T12:30:00Z","source":"manual"}]""",
            (post.body as TextContent).text,
        )

        stored = ROW
        assertTrue(repo.saveOnboardingWeight(75.0, MEASURED_AT).isSuccess)
        val all = writes()
        assertEquals(2, all.size)
        assertEquals(1, all.count { it.method == HttpMethod.Post })
        val patch = all.last()
        assertEquals(HttpMethod.Patch, patch.method)
        assertTrue(patch.url.toString().contains("id=eq.row-1"), patch.url.toString())
        assertEquals(
            """{"weight_kg":75.0,"measured_at":"2026-10-14T12:30:00Z"}""",
            (patch.body as TextContent).text,
        )
    }

    @Test
    fun latestWeight_ordersByMeasuredAtDescending() = runTest {
        val repo = signedIn(rows = { ROW })

        assertEquals(74.2, repo.getLatestWeight().getOrThrow()?.weightKg)
        val url = requests.last().url.toString()
        assertTrue(url.contains("order=measured_at.desc"), url)
        assertTrue(url.contains("limit=1"), url)
    }

    @Test
    fun emptyResults_giveNull() = runTest {
        val repo = signedIn()

        assertNull(repo.getOnboardingWeight().getOrThrow())
        assertNull(repo.getLatestWeight().getOrThrow())
    }

    @Test
    fun networkError_isAppErrorNetwork() = runTest {
        var fail = false
        val repo = signedIn(failing = { fail })
        fail = true

        assertEquals(AppError.Network, repo.getOnboardingWeight().appErrorOrNull())
        assertEquals(AppError.Network, repo.saveOnboardingWeight(70.0, MEASURED_AT).appErrorOrNull())
        assertEquals(AppError.Network, repo.getLatestWeight().appErrorOrNull())
    }

    @Test
    fun saveManualWeight_newSave_sendsTheRpcWithExplicitNulls() = runTest {
        val repo = signedIn()

        assertEquals("row-1", repo.saveManualWeight(newSave).getOrThrow())

        val rpc = writes().single()
        assertEquals(HttpMethod.Post, rpc.method)
        assertTrue(rpc.url.encodedPath.endsWith("/rpc/save_body_measurement"), rpc.url.toString())
        assertEquals(
            """{"p_id":null,"p_weight_kg":74.2,"p_body_fat_pct":null,"p_measured_at":"2026-10-14T12:30:00Z",""" +
                """"p_day_start":"2026-10-13T22:00:00Z","p_day_end":"2026-10-14T22:00:00Z"}""",
            (rpc.body as TextContent).text,
        )
    }

    @Test
    fun saveManualWeight_edit_sendsIdAndBodyFat() = runTest {
        val repo = signedIn()

        assertTrue(repo.saveManualWeight(newSave.copy(id = "row-1", bodyFatPct = 18.5)).isSuccess)

        val body = (writes().single().body as TextContent).text
        assertTrue(body.contains(""""p_id":"row-1""""), body)
        assertTrue(body.contains(""""p_body_fat_pct":18.5"""), body)
    }

    @Test
    fun saveManualWeight_rejectedInput_isValidation() = runTest {
        assertEquals(
            AppError.Validation,
            signedIn(rejectWritesWith = "22023").saveManualWeight(newSave).appErrorOrNull(),
        )
        assertEquals(
            AppError.Validation,
            signedIn(rejectWritesWith = "23514").saveManualWeight(newSave).appErrorOrNull(),
        )
    }

    @Test
    fun saveManualWeight_foreignOrHealthConnectRow_isNotFound() = runTest {
        val repo = signedIn(rejectWritesWith = "P0002")

        assertEquals(AppError.NotFound, repo.saveManualWeight(newSave.copy(id = "other")).appErrorOrNull())
    }

    @Test
    fun getMeasurements_queriesTheRangeNewestFirstAndReadsBodyFat() = runTest {
        val repo = signedIn(
            rows = {
                """[{"id":"row-1","measured_at":"2026-10-14T10:00:00+00:00","weight_kg":74.2,""" +
                    """"source":"manual","body_fat_pct":18.5}]"""
            },
        )

        val rows = repo.getMeasurements(
            Instant.parse("2026-09-30T22:00:00Z"),
            Instant.parse("2026-10-31T23:00:00Z"),
        ).getOrThrow()

        assertEquals(18.5, rows.single().bodyFatPct)
        val url = requests.last().url
        assertEquals(
            """(measured_at.gte."2026-09-30T22:00:00Z",measured_at.lt."2026-10-31T23:00:00Z")""",
            url.parameters["and"],
        )
        assertTrue(url.toString().contains("order=measured_at.desc"), url.toString())
        assertTrue(url.parameters["select"].orEmpty().contains("body_fat_pct"), url.toString())
    }

    @Test
    fun deleteMeasurement_callsTheDeleteRpc() = runTest {
        val repo = signedIn()

        assertTrue(repo.deleteMeasurement("row-1").isSuccess)

        val rpc = writes().single()
        assertEquals(HttpMethod.Post, rpc.method)
        assertTrue(rpc.url.encodedPath.endsWith("/rpc/delete_body_measurement"), rpc.url.toString())
        assertEquals("""{"p_id":"row-1"}""", (rpc.body as TextContent).text)
    }

    @Test
    fun setHcRecordId_patchesTheMarkerAndCanClearIt() = runTest {
        val repo = signedIn()

        assertTrue(repo.setHcRecordId("row-1", "row-1").isSuccess)
        assertTrue(repo.setHcRecordId("row-1", null).isSuccess)

        val (set, clear) = writes()
        assertEquals(HttpMethod.Patch, set.method)
        assertTrue(set.url.toString().contains("id=eq.row-1"), set.url.toString())
        assertEquals("""{"hc_record_id":"row-1"}""", (set.body as TextContent).text)
        assertEquals("""{"hc_record_id":null}""", (clear.body as TextContent).text)
    }

    @Test
    fun getMeasurements_readsTheCompositionColumnsAndTheCopyMarker() = runTest {
        val repo = signedIn(
            rows = {
                """[{"id":"row-1","measured_at":"2026-10-14T10:00:00+00:00","weight_kg":73.9,""" +
                    """"source":"health_connect","body_fat_pct":15.1,"lean_mass_kg":62.4,"water_kg":45.0,""" +
                    """"bone_kg":3.1,"bmr_kcal":1720,"hc_record_id":null}]"""
            },
        )

        val row = repo.getMeasurements(MEASURED_AT, MEASURED_AT).getOrThrow().single()

        assertEquals(62.4, row.leanMassKg)
        assertEquals(45.0, row.waterKg)
        assertEquals(3.1, row.boneKg)
        assertEquals(1720, row.bmrKcal)
        assertNull(row.hcRecordId)
        assertTrue(requests.last().url.parameters["select"].orEmpty().contains("hc_record_id"))
    }

    @Test
    fun getLatestComposition_filtersRowsWithAnyCompositionNewestFirstAndPicksPerField() = runTest {
        val repo = signedIn(
            rows = {
                """[{"id":"a","measured_at":"2026-10-14T10:00:00+00:00","weight_kg":73.9,"source":"health_connect",""" +
                    """"body_fat_pct":15.1},""" +
                    """{"id":"b","measured_at":"2026-10-10T10:00:00+00:00","weight_kg":74.5,""" +
                    """"source":"health_connect","body_fat_pct":16.0,"bmr_kcal":1720}]"""
            },
        )

        val latest = repo.getLatestComposition().getOrThrow()

        assertEquals(15.1, latest.bodyFat?.value)
        assertEquals(1720, latest.bmr?.value)
        assertEquals(Instant.parse("2026-10-10T10:00:00Z"), latest.bmr?.measuredAt)
        assertNull(latest.water)
        val url = requests.last().url
        assertEquals(
            "(body_fat_pct.not.is.null,lean_mass_kg.not.is.null,water_kg.not.is.null," +
                "bone_kg.not.is.null,bmr_kcal.not.is.null)",
            url.parameters["or"],
        )
        assertTrue(url.toString().contains("order=measured_at.desc"), url.toString())
        assertTrue(url.toString().contains("limit=50"), url.toString())
    }

    @Test
    fun getLatestComposition_signedOutIsUnauthorizedAndNetworkIsNetwork() = runTest {
        assertEquals(AppError.Unauthorized, signedOut().getLatestComposition().appErrorOrNull())
        var fail = false
        val repo = signedIn(failing = { fail })
        fail = true
        assertEquals(AppError.Network, repo.getLatestComposition().appErrorOrNull())
    }

    @Test
    fun newMethods_signedOut_areUnauthorized() = runTest {
        val repo = signedOut()

        assertEquals(AppError.Unauthorized, repo.getMeasurements(MEASURED_AT, MEASURED_AT).appErrorOrNull())
        assertEquals(AppError.Unauthorized, repo.saveManualWeight(newSave).appErrorOrNull())
        assertEquals(AppError.Unauthorized, repo.deleteMeasurement("row-1").appErrorOrNull())
        assertEquals(AppError.Unauthorized, repo.setHcRecordId("row-1", null).appErrorOrNull())
    }
}
