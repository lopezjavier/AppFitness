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

    override suspend fun list(planId: String?, statuses: Set<CoachChangeStatus>): Result<List<CoachChange>> =
        Result.success(listOf(change("c2")))

    override suspend fun undo(id: String): Result<UndoResult> = Result.success(UndoResult(2, 1))

    override suspend fun decide(id: String, decision: ProgressionFlagDecision): Result<FlagDecisionResult> =
        Result.success(FlagDecisionResult(change(id, CoachChangeStatus.Decided), null))
}

class CoachChangeUseCasesTest {
    private val repository = RecordingCoachChangesRepository()

    @Test
    fun apply_passesTheSessionAndReturnsTheChanges() = runTest {
        val result = ApplyCoachProgressionUseCase(repository)("s1").getOrThrow()

        assertEquals(listOf("apply:s1"), repository.calls)
        assertEquals(listOf("c1"), result.map { it.id })
    }
}
