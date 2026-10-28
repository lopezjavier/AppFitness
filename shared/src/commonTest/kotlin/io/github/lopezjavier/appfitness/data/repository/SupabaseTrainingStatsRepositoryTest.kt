package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ExerciseSetCount
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.domain.model.WeekRecord
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

class SupabaseTrainingStatsRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var body = "[]"
    private var status = HttpStatusCode.OK
    private var offline = false

    private suspend fun repo(signIn: Boolean = true): SupabaseTrainingStatsRepository {
        val engine =
            MockEngine { request ->
                requests += request
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.url.encodedPath.contains("/auth/") -> respond(SESSION, HttpStatusCode.OK, headers)
                    offline -> throw IOException("offline")
                    else -> respond(body, status, headers)
                }
            }
        val client = SupabaseClientFactory.create(
            AppConfig("http://localhost:54321", "sb_publishable_test", "", isLocal = true),
            engine,
            MemorySessionManager(),
        )
        if (signIn) SupabaseAuthRepository(client.auth).signInWithPassword(TestLoginCredentials("a@b.c", "pw"))
        return SupabaseTrainingStatsRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun payload() = (rest().single().body as TextContent).text

    private fun path() = rest().single().url.encodedPath

    @Test
    fun summary_callsTheRpcWithBothBoundsAndMapsTheRow() = runTest {
        val repository = repo()
        body = """[{"workouts":4,"sets":52,"volume":12345.5,"duration_s":9000}]"""

        val summary = repository.summary(LocalDate(2026, 9, 25), LocalDate(2026, 10, 25)).getOrThrow()

        assertTrue(path().endsWith("/rest/v1/rpc/training_summary"), path())
        assertEquals("""{"p_from":"2026-09-25","p_to":"2026-10-25"}""", payload())
        assertEquals(TrainingSummary(4, 52, 12345.5, 9000), summary)
    }

    @Test
    fun summary_sendsNullForOpenBounds() = runTest {
        val repository = repo()

        repository.summary(null, null).getOrThrow()

        assertEquals("""{"p_from":null,"p_to":null}""", payload())
    }

    @Test
    fun summary_emptyResultIsAZeroSummary() = runTest {
        val repository = repo()

        assertEquals(TrainingSummary(0, 0, 0.0, 0), repository.summary(null, null).getOrThrow())
    }

    @Test
    fun weeklySetCounts_callsTheRpcAndMapsTheRows() = runTest {
        val repository = repo()
        body = """[{"week_start":"2026-10-12","sets":18},{"week_start":"2026-10-19","sets":7}]"""

        val rows = repository.weeklySetCounts(LocalDate(2026, 10, 1), null).getOrThrow()

        assertTrue(path().endsWith("/rest/v1/rpc/weekly_set_counts"), path())
        assertEquals("""{"p_from":"2026-10-01","p_to":null}""", payload())
        assertEquals(
            listOf(SetCountPoint(LocalDate(2026, 10, 12), 18), SetCountPoint(LocalDate(2026, 10, 19), 7)),
            rows,
        )
    }

    @Test
    fun muscleGroupSets_callsTheRpcAndMapsFractionalSets() = runTest {
        val repository = repo()
        body = """[{"muscle_group":"chest","sets":3},{"muscle_group":"triceps","sets":1.5}]"""

        val rows = repository.muscleGroupSets(null, LocalDate(2026, 10, 25)).getOrThrow()

        assertTrue(path().endsWith("/rest/v1/rpc/muscle_group_sets"), path())
        assertEquals("""{"p_from":null,"p_to":"2026-10-25"}""", payload())
        assertEquals(listOf(MuscleGroupSets(MuscleGroup.Chest, 3.0), MuscleGroupSets(MuscleGroup.Triceps, 1.5)), rows)
    }

    @Test
    fun exerciseSetCounts_withoutGroupSendsNullGroupAndMapsTheRows() = runTest {
        val repository = repo()
        body = """[{"exercise_id":"e1","sets":6,"sessions":2}]"""

        val rows = repository.exerciseSetCounts(null, null).getOrThrow()

        assertTrue(path().endsWith("/rest/v1/rpc/exercise_set_counts"), path())
        assertEquals("""{"p_from":null,"p_to":null,"p_muscle_group":null}""", payload())
        assertEquals(listOf(ExerciseSetCount("e1", 6.0, 2)), rows)
    }

    @Test
    fun exerciseSetCounts_withGroupSendsItsWireName() = runTest {
        val repository = repo()
        body = """[{"exercise_id":"bench","sets":1.5,"sessions":1}]"""

        val rows = repository.exerciseSetCounts(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30), MuscleGroup.Triceps)
            .getOrThrow()

        assertEquals(
            """{"p_from":"2026-09-01","p_to":"2026-09-30","p_muscle_group":"triceps"}""",
            payload(),
        )
        assertEquals(listOf(ExerciseSetCount("bench", 1.5, 1)), rows)
    }

    @Test
    fun weeklyRecords_callsTheRpcAndMapsKnownTypes() = runTest {
        val repository = repo()
        body = """[{"exercise_id":"e1","record_type":"best_1rm","value":102.5,"session_id":"s1",""" +
            """"session_date":"2026-11-11"},""" +
            """{"exercise_id":"e2","record_type":"unknown_type","value":1,"session_id":"s2",""" +
            """"session_date":"2026-11-12"}]"""

        val rows = repository.weeklyRecords(LocalDate(2026, 11, 9), LocalDate(2026, 11, 15)).getOrThrow()

        assertTrue(path().endsWith("/rest/v1/rpc/weekly_records"), path())
        assertEquals("""{"p_from":"2026-11-09","p_to":"2026-11-15"}""", payload())
        assertEquals(listOf(WeekRecord("e1", PersonalRecordType.Best1Rm, 102.5, LocalDate(2026, 11, 11))), rows)
    }

    @Test
    fun weeklyRecords_emptyIsAnEmptyList_andOfflineIsNetwork() = runTest {
        val repository = repo()

        assertTrue(repository.weeklyRecords(LocalDate(2026, 11, 9), LocalDate(2026, 11, 15)).getOrThrow().isEmpty())
        offline = true
        assertEquals(
            AppError.Network,
            repository.weeklyRecords(LocalDate(2026, 11, 9), LocalDate(2026, 11, 15)).appErrorOrNull(),
        )
    }

    @Test
    fun emptyResults_mapToEmptyLists() = runTest {
        val repository = repo()

        assertTrue(repository.weeklySetCounts(null, null).getOrThrow().isEmpty())
        assertTrue(repository.muscleGroupSets(null, null).getOrThrow().isEmpty())
        assertTrue(repository.exerciseSetCounts(null, null).getOrThrow().isEmpty())
    }

    @Test
    fun signedOut_isUnauthorizedWithoutRequests() = runTest {
        val repository = repo(signIn = false)

        assertEquals(AppError.Unauthorized, repository.summary(null, null).appErrorOrNull())
        assertEquals(AppError.Unauthorized, repository.weeklySetCounts(null, null).appErrorOrNull())
        assertEquals(AppError.Unauthorized, repository.muscleGroupSets(null, null).appErrorOrNull())
        assertEquals(AppError.Unauthorized, repository.exerciseSetCounts(null, null).appErrorOrNull())
        assertTrue(rest().isEmpty())
    }

    @Test
    fun offline_isNetworkErrorForEveryCall() = runTest {
        val repository = repo()
        offline = true

        assertEquals(AppError.Network, repository.summary(null, null).appErrorOrNull())
        assertEquals(AppError.Network, repository.weeklySetCounts(null, null).appErrorOrNull())
        assertEquals(AppError.Network, repository.muscleGroupSets(null, null).appErrorOrNull())
        assertEquals(AppError.Network, repository.exerciseSetCounts(null, null).appErrorOrNull())
    }

    @Test
    fun httpError_isMappedToAnAppError() = runTest {
        val repository = repo()
        status = HttpStatusCode.InternalServerError
        body = """{"message":"boom"}"""

        val error = repository.summary(null, null).appErrorOrNull()

        assertTrue(error != null, "expected an AppError")
    }
}
