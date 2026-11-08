package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFigures
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.domain.repository.CoachWeeksRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate

internal fun planWeek(
    week: Int = 1,
    phase: WeekPhase = WeekPhase.Current,
    deload: Boolean = false,
    planned: Int = 4,
    completed: Int = 3,
    adherencePct: Int = 75,
    cardioMinDone: Int = 0,
    cardioMinTarget: Int? = null,
    stepsAvg: Int? = null,
    stepsTarget: Int? = null,
) = PlanWeek(
    week = week,
    startDate = LocalDate(2026, 10, 19).let { LocalDate.fromEpochDays(it.toEpochDays() + 7 * (week - 1)) },
    endDate = LocalDate(2026, 10, 25).let { LocalDate.fromEpochDays(it.toEpochDays() + 7 * (week - 1)) },
    phase = phase,
    deload = deload,
    planned = planned,
    completed = completed,
    adherencePct = adherencePct,
    sessions = listOf(PlanWeekSession(0, "Push", "routine-0", done = completed > 0)),
    cardioMinDone = cardioMinDone,
    cardioMinTarget = cardioMinTarget,
    stepsAvg = stepsAvg,
    stepsTarget = stepsTarget,
)

/** A plan-level deload change (no exercise) of plan week [week]. */
internal fun deloadChange(
    id: String = "deload-1",
    kind: CoachChangeKind = CoachChangeKind.DeloadTriggered,
    status: CoachChangeStatus = CoachChangeStatus.Applied,
    week: Int = 4,
) = CoachChange(
    id = id,
    planId = "plan-1",
    kind = kind,
    exerciseId = null,
    status = status,
    reason = "reason_$id",
    figures = CoachChangeFigures(week = week),
    before = null,
    after = null,
    createdAt = null,
    decidedAt = null,
    decision = null,
)

/** Test double of [CoachWeeksRepository]; each answer is a settable result and every call is recorded. */
internal class FakeCoachWeeksRepository : CoachWeeksRepository {
    var weeksResult: Result<List<PlanWeek>> = Result.success(emptyList())
    var evaluation: Result<CoachChange?> = Result.success(null)
    var requestResult: Result<CoachChange> = Result.success(deloadChange(kind = CoachChangeKind.DeloadUser, week = 2))
    var cancelResult: Result<CoachChange> = Result.success(deloadChange(status = CoachChangeStatus.Undone))

    /** When set, [requestDeload] and [cancelDeload] suspend until completed. */
    var gate: CompletableDeferred<Unit>? = null

    /** Run by a successful request or cancel before the next read: mimics the server. */
    var onSuccess: () -> Unit = {}

    /** Run when the rule is evaluated: mimics the server writing the deload row. */
    var onEvaluate: () -> Unit = {}

    var pauseResult: Result<BlockPause> = Result.success(BlockPause(paused = false, pausedWeeks = 0, blockEnd = null))

    val pauseReads = mutableListOf<Pair<String, LocalDate>>()
    val weeksReads = mutableListOf<Pair<String, LocalDate>>()
    var evaluations = 0
    var requests = 0
    val cancellations = mutableListOf<String>()

    override suspend fun weeks(planId: String, today: LocalDate): Result<List<PlanWeek>> {
        weeksReads += planId to today
        return weeksResult
    }

    override suspend fun blockPause(planId: String, today: LocalDate): Result<BlockPause> {
        pauseReads += planId to today
        return pauseResult
    }

    override suspend fun evaluateDeload(today: LocalDate): Result<CoachChange?> {
        evaluations++
        onEvaluate()
        return evaluation
    }

    override suspend fun requestDeload(): Result<CoachChange> {
        requests++
        gate?.await()
        if (requestResult.isSuccess) onSuccess()
        return requestResult
    }

    override suspend fun cancelDeload(changeId: String): Result<CoachChange> {
        cancellations += changeId
        gate?.await()
        if (cancelResult.isSuccess) onSuccess()
        return cancelResult
    }
}
