package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFigures
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.FlagDecisionResult
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import io.github.lopezjavier.appfitness.domain.model.UndoResult
import io.github.lopezjavier.appfitness.domain.repository.CoachChangesRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private fun change(id: String, status: CoachChangeStatus = CoachChangeStatus.Applied) = CoachChange(
    id, null, CoachChangeKind.LoadRaise, "e1", status, "all_sets_at_max", CoachChangeFigures(), null, null, null, null,
    null,
)

private class RecordingCoachChangesRepository : CoachChangesRepository {
    val calls = mutableListOf<String>()

    override suspend fun apply(sessionId: String): Result<List<CoachChange>> {
        calls += "apply:$sessionId"
        return Result.success(listOf(change("c1")))
    }

    override suspend fun list(planId: String?, statuses: Set<CoachChangeStatus>): Result<List<CoachChange>> {
        calls += "list:$planId:${statuses.sortedBy { it.name }.joinToString(",")}"
        return Result.success(listOf(change("c2")))
    }

    override suspend fun undo(id: String): Result<UndoResult> {
        calls += "undo:$id"
        return Result.success(UndoResult(2, 1))
    }

    override suspend fun decide(id: String, decision: ProgressionFlagDecision): Result<FlagDecisionResult> {
        calls += "decide:$id:${decision.code}"
        return Result.success(FlagDecisionResult(change(id, CoachChangeStatus.Decided), null))
    }
}

class CoachChangeUseCasesTest {
    private val repository = RecordingCoachChangesRepository()

    @Test
    fun apply_passesTheSessionAndReturnsTheChanges() = runTest {
        val result = ApplyCoachProgressionUseCase(repository)("s1").getOrThrow()

        assertEquals(listOf("apply:s1"), repository.calls)
        assertEquals(listOf("c1"), result.map { it.id })
    }

    @Test
    fun list_passesThePlanAndStatuses() = runTest {
        val result = ListCoachChangesUseCase(repository)("p1", setOf(CoachChangeStatus.Pending)).getOrThrow()

        assertEquals(listOf("list:p1:Pending"), repository.calls)
        assertEquals(listOf("c2"), result.map { it.id })
    }

    @Test
    fun undo_passesTheIdAndReturnsTheResult() = runTest {
        val result = UndoCoachChangeUseCase(repository)("c1").getOrThrow()

        assertEquals(listOf("undo:c1"), repository.calls)
        assertEquals(UndoResult(2, 1), result)
    }

    @Test
    fun decide_passesTheDecision() = runTest {
        val result = DecideProgressionFlagUseCase(repository)("f1", ProgressionFlagDecision.Keep).getOrThrow()

        assertEquals(listOf("decide:f1:keep"), repository.calls)
        assertEquals(CoachChangeStatus.Decided, result.flag.status)
    }
}
