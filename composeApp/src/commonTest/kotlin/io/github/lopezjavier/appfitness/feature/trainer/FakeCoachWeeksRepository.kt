package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.DeloadProposal
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
)

internal fun deloadProposal(id: String = "deload-1", week: Int = 4) = DeloadProposal(id, "plan-1", week)

/** Test double of [CoachWeeksRepository]; each answer is a settable result and every call is recorded. */
internal class FakeCoachWeeksRepository : CoachWeeksRepository {
    var weeksResult: Result<List<PlanWeek>> = Result.success(emptyList())
    var pending: Result<DeloadProposal?> = Result.success(null)
    var decideResult: Result<Unit> = Result.success(Unit)

    /** When set, [decideDeload] suspends until completed. */
    var decideGate: CompletableDeferred<Unit>? = null

    val weeksReads = mutableListOf<Pair<String, LocalDate>>()
    var pendingReads = 0
    val decisions = mutableListOf<Pair<String, Boolean>>()

    override suspend fun weeks(planId: String, today: LocalDate): Result<List<PlanWeek>> {
        weeksReads += planId to today
        return weeksResult
    }

    override suspend fun pendingDeload(today: LocalDate): Result<DeloadProposal?> {
        pendingReads++
        return pending
    }

    override suspend fun decideDeload(id: String, accept: Boolean): Result<Unit> {
        decisions += id to accept
        decideGate?.await()
        return decideResult
    }
}
