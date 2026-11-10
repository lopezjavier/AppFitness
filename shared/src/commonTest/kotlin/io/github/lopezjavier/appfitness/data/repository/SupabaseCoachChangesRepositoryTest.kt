package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.data.remote.SupabaseClientFactory
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachChangeDecision
import io.github.lopezjavier.appfitness.domain.model.CoachChangeException
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFailure
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.model.UndoResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SESSION =
    """{"access_token":"access","token_type":"bearer","expires_in":3600,"refresh_token":"refresh",""" +
        """"user":{"id":"user-1","aud":"authenticated","email":"a@b.c"}}"""

private const val RAISE_ROW =
    """{"id":"c1","kind":"load_raise","exercise_id":"e1","status":"applied","reason":"all_sets_at_max",""" +
        """"figures":{"weight_from_kg":60,"weight_to_kg":62.5,"reps_best":12,"rpe_max":8,"sessions":1,"new_key":1},""" +
        """"before_values":[{"routine_id":"r1","exercise_id":"e1","rep_min":8,"rep_max":12,"sets":""" +
        """[{"set_index":1,"set_type":"working","target_weight_kg":60,"target_reps":10}]}],""" +
        """"after_values":[{"routine_id":"r1","exercise_id":"e1","rep_min":8,"rep_max":12,"sets":""" +
        """[{"set_index":1,"target_weight_kg":62.5,"target_reps":8}]}],"decision":null}"""

class SupabaseCoachChangesRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var body = "[]"
    private var status = HttpStatusCode.OK
    private var offline = false

    private suspend fun repo(signIn: Boolean = true): SupabaseCoachChangesRepository {
        val engine = MockEngine { request ->
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
        return SupabaseCoachChangesRepository(client.auth, client.postgrest)
    }

    private fun rest() = requests.filter { !it.url.encodedPath.contains("/auth/") }

    private fun refuse(message: String, code: String = "P0001") {
        status = HttpStatusCode.BadRequest
        body = """{"code":"$code","message":"$message","details":null,"hint":null}"""
    }

    @Test
    fun apply_callsTheRpcAndMapsTheRowsTolerantly() = runTest {
        body = "[$RAISE_ROW]"

        val change = repo().apply("s1").getOrThrow().single()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/apply_coach_progression"))
        assertEquals("""{"p_session_id":"s1"}""", (request.body as TextContent).text)
        assertEquals(CoachChangeKind.LoadRaise, change.kind)
        assertEquals(CoachChangeStatus.Applied, change.status)
        assertEquals(60.0, change.figures.weightFromKg)
        assertEquals(62.5, change.figures.weightToKg)
        assertEquals(12, change.figures.repsBest)
        assertNull(change.decision)
        assertNull(change.planId)
        assertEquals("working", change.before?.single()?.sets?.single()?.setType)
        assertEquals(62.5, change.after?.single()?.sets?.single()?.targetWeightKg)
        assertNull(change.after?.single()?.sets?.single()?.setType)
    }

    @Test
    fun noRangeFlag_readsTheRoutineIdFromTheFiguresWithoutSnapshots() = runTest {
        body = """[{"id":"n1","kind":"no_range_flag","exercise_id":"e1","status":"pending","reason":"x",""" +
            """"figures":{"routine_id":"r9"}}]"""

        val change = repo().apply("s1").getOrThrow().single()

        assertEquals("r9", change.figures.routineId)
        assertNull(change.before)
    }

    @Test
    fun figures_withoutOrWithMistypedRoutineId_haveNullRoutineId() = runTest {
        body = """[{"id":"a","kind":"load_flag","status":"pending","figures":{"routine_id":5}},""" +
            """{"id":"b","kind":"load_flag","status":"pending","figures":{}}]"""

        val changes = repo().apply("s1").getOrThrow()

        assertEquals(listOf(null, null), changes.map { it.figures.routineId })
    }

    @Test
    fun apply_emptyArrayIsEmpty() = runTest {
        assertEquals(emptyList(), repo().apply("s1").getOrThrow())
    }

    @Test
    fun apply_decodesEveryKindStatusAndDecision() = runTest {
        val kinds = listOf("load_raise", "load_drop", "load_kept", "load_flag", "no_range_flag")
        val statuses = listOf("applied", "undone", "pending", "decided")
        val rows = kinds.mapIndexed { i, kind ->
            """{"id":"k$i","kind":"$kind","exercise_id":"e","status":"${statuses[i % 4]}","reason":"x"}"""
        }
        body = rows.joinToString(prefix = "[", postfix = "]")

        val changes = repo().apply("s1").getOrThrow()

        assertEquals(
            listOf(
                CoachChangeKind.LoadRaise,
                CoachChangeKind.LoadDrop,
                CoachChangeKind.LoadKept,
                CoachChangeKind.LoadFlag,
                CoachChangeKind.NoRangeFlag,
            ),
            changes.map { it.kind },
        )
        assertEquals(
            listOf(
                CoachChangeStatus.Applied,
                CoachChangeStatus.Undone,
                CoachChangeStatus.Pending,
                CoachChangeStatus.Decided,
                CoachChangeStatus.Applied,
            ),
            changes.map { it.status },
        )
    }

    @Test
    fun apply_unknownKindAndStatusFallBackToUnknown() = runTest {
        body =
            """[{"id":"c","kind":"deload_cut","exercise_id":"e","status":"parked","reason":"x","decision":"later"}]"""

        val change = repo().apply("s1").getOrThrow().single()

        assertEquals(CoachChangeKind.Unknown, change.kind)
        assertEquals(CoachChangeStatus.Unknown, change.status)
        assertEquals(CoachChangeDecision.Unknown, change.decision)
    }

    @Test
    fun apply_flagFiguresAndNullSnapshots() = runTest {
        body =
            """[{"id":"f","kind":"load_flag","exercise_id":"e","status":"pending","reason":"step_over_10_percent",""" +
            """"figures":{"current_kg":6,"step_kg":2,"next_kg":8,"percent":33.3},"before_values":null,""" +
            """"after_values":null,"decision":"range_added"}]"""

        val change = repo().apply("s1").getOrThrow().single()

        assertEquals(6.0, change.figures.currentKg)
        assertEquals(2.0, change.figures.stepKg)
        assertEquals(8.0, change.figures.nextKg)
        assertEquals(33.3, change.figures.percent)
        assertNull(change.before)
        assertNull(change.after)
        assertEquals(CoachChangeDecision.RangeAdded, change.decision)
    }

    @Test
    fun apply_anUnreadableRowIsDroppedAndTheRestKept() = runTest {
        body = """[{"nonsense":true},$RAISE_ROW]"""

        assertEquals(listOf("c1"), repo().apply("s1").getOrThrow().map { it.id })
    }

    @Test
    fun list_filtersByPlanAndStatusNewestFirst() = runTest {
        body = """[{"id":"c1","plan_id":"p1","kind":"load_flag","exercise_id":"e","status":"pending","reason":"x",""" +
            """"created_at":"2027-01-02T10:00:00+00:00","decided_at":null}]"""

        val result = repo().list("p1", setOf(CoachChangeStatus.Pending, CoachChangeStatus.Applied)).getOrThrow()

        val url = rest().single().url
        assertTrue(url.encodedPath.endsWith("/rest/v1/coach_changes"))
        assertEquals("eq.p1", url.parameters["plan_id"])
        assertEquals("in.(pending,applied)", url.parameters["status"])
        assertEquals("created_at.desc.nullslast", url.parameters["order"])
        assertEquals("p1", result.single().planId)
        assertNotNull(result.single().createdAt)
    }

    @Test
    fun list_withoutFiltersSendsNone() = runTest {
        repo().list().getOrThrow()

        val url = rest().single().url
        assertNull(url.parameters["plan_id"])
        assertNull(url.parameters["status"])
    }

    @Test
    fun undo_mapsTheResult() = runTest {
        body = """{"restored":2,"skipped":1}"""

        val result = repo().undo("c1").getOrThrow()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/undo_coach_change"))
        assertEquals("""{"p_id":"c1"}""", (request.body as TextContent).text)
        assertEquals(UndoResult(2, 1), result)
    }

    @Test
    fun undo_conflictIsATypedFailure() = runTest {
        refuse("coach_change_conflict")

        val failure = repo().undo("c1").exceptionOrNull()

        assertEquals(CoachChangeFailure.Conflict, assertIs<CoachChangeException>(failure).failure)
    }

    @Test
    fun undo_notUndoableIsATypedFailure() = runTest {
        refuse("coach_change_not_undoable")

        val failure = repo().undo("c1").exceptionOrNull()

        assertEquals(CoachChangeFailure.NotUndoable, assertIs<CoachChangeException>(failure).failure)
    }

    @Test
    fun undo_foreignOrUnknownIdIsNotFound() = runTest {
        refuse("coach change not found", code = "P0002")

        assertEquals(AppError.NotFound, repo().undo("c1").appErrorOrNull())
    }

    @Test
    fun decide_raiseSendsTheDecisionAndReturnsFlagAndChange() = runTest {
        body = """{"flag":{"id":"f","kind":"load_flag","exercise_id":"e","status":"decided","reason":"x",""" +
            """"decision":"raise"},"change":$RAISE_ROW}"""

        val result = repo().decide("f", ProgressionFlagDecision.Raise).getOrThrow()

        val request = rest().single()
        assertTrue(request.url.encodedPath.endsWith("/rest/v1/rpc/decide_progression_flag"))
        assertEquals("""{"p_id":"f","p_decision":"raise"}""", (request.body as TextContent).text)
        assertEquals(CoachChangeStatus.Decided, result.flag.status)
        assertEquals("c1", result.change?.id)
    }

    @Test
    fun decide_keepHasNoNewChange() = runTest {
        body = """{"flag":{"id":"f","kind":"load_flag","exercise_id":"e","status":"decided","reason":"x",""" +
            """"decision":"keep"},"change":null}"""

        val result = repo().decide("f", ProgressionFlagDecision.Keep).getOrThrow()

        assertEquals(CoachChangeDecision.Keep, result.flag.decision)
        assertNull(result.change)
    }

    @Test
    fun decide_staleFlagIsAConflictAndAnAlreadyDecidedOneIsNotPending() = runTest {
        refuse("coach_change_conflict")
        val conflict = repo().decide("f", ProgressionFlagDecision.Raise).exceptionOrNull()
        refuse("coach_change_not_pending")
        val pending = repo().decide("f", ProgressionFlagDecision.Raise).exceptionOrNull()

        assertEquals(CoachChangeFailure.Conflict, assertIs<CoachChangeException>(conflict).failure)
        assertEquals(CoachChangeFailure.NotPending, assertIs<CoachChangeException>(pending).failure)
    }

    @Test
    fun apply_signedOut_isUnauthorized() = runTest {
        assertEquals(AppError.Unauthorized, repo(signIn = false).apply("s1").appErrorOrNull())
    }

    @Test
    fun apply_offline_isNetworkError() = runTest {
        val repository = repo()
        offline = true

        assertEquals(AppError.Network, repository.apply("s1").appErrorOrNull())
        assertEquals(AppError.Network, repository.undo("c1").appErrorOrNull())
    }
}
