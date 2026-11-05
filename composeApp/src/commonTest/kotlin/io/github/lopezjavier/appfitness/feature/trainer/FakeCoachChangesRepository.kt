package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.CoachChangeDecision
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFigures
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.FlagDecisionResult
import io.github.lopezjavier.appfitness.domain.model.PlannedRoutineValues
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import io.github.lopezjavier.appfitness.domain.model.UndoResult
import io.github.lopezjavier.appfitness.domain.repository.CoachChangesRepository
import kotlinx.coroutines.CompletableDeferred

/** A coach change fixture; the routine snapshot carries [routineId] so routine filters can be tested. */
internal fun coachChange(
    id: String = "change-1",
    kind: CoachChangeKind = CoachChangeKind.LoadRaise,
    status: CoachChangeStatus = CoachChangeStatus.Applied,
    exerciseId: String = "bench",
    routineId: String = "routine-1",
    figures: CoachChangeFigures = CoachChangeFigures(currentKg = 60.0, nextKg = 62.5),
    decision: CoachChangeDecision? = null,
) = CoachChange(
    id = id,
    planId = "plan-1",
    kind = kind,
    exerciseId = exerciseId,
    status = status,
    reason = "reason_$id",
    figures = figures,
    before = listOf(PlannedRoutineValues(routineId, exerciseId, 8, 12, emptyList())),
    after = listOf(PlannedRoutineValues(routineId, exerciseId, 8, 12, emptyList())),
    createdAt = null,
    decidedAt = null,
    decision = decision,
)

internal class FakeCoachChangesRepository(var changes: Result<List<CoachChange>> = Result.success(emptyList())) :
    CoachChangesRepository {
    var undoResult: Result<UndoResult> = Result.success(UndoResult(restored = 1, skipped = 0))
    var decideResult: Result<FlagDecisionResult>? = null

    /** When set, [undo] and [decide] suspend until completed. */
    var gate: CompletableDeferred<Unit>? = null

    /** Applied by a successful undo/decide before the next list: mimics the server. */
    var onSuccess: () -> Unit = {}

    val listCalls = mutableListOf<Pair<String?, Set<CoachChangeStatus>>>()
    val undone = mutableListOf<String>()
    val decided = mutableListOf<Pair<String, ProgressionFlagDecision>>()

    override suspend fun apply(sessionId: String): Result<List<CoachChange>> = Result.success(emptyList())

    override suspend fun list(planId: String?, statuses: Set<CoachChangeStatus>): Result<List<CoachChange>> {
        listCalls += planId to statuses
        return changes
    }

    override suspend fun undo(id: String): Result<UndoResult> {
        undone += id
        gate?.await()
        if (undoResult.isSuccess) onSuccess()
        return undoResult
    }

    override suspend fun decide(id: String, decision: ProgressionFlagDecision): Result<FlagDecisionResult> {
        decided += id to decision
        gate?.await()
        val result = decideResult ?: Result.success(FlagDecisionResult(coachChange(id = id), null))
        if (result.isSuccess) onSuccess()
        return result
    }
}
