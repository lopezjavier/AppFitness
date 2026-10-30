package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CalorieChange
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.PlanLimit
import io.github.lopezjavier.appfitness.domain.model.PlanNutrition
import io.github.lopezjavier.appfitness.domain.model.PlanOutcome
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.ProteinDistribution
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate

/** A fat-loss plan fixture; [limitedBy] Floor reproduces the `1100 -> 1200` example of planning/04 section 8. */
internal fun coachPlan(
    id: String = "plan-1",
    status: PlanStatus = PlanStatus.Proposed,
    weightTarget: Double? = 72.5,
    limitedBy: PlanLimit? = null,
    editedByUser: Boolean = false,
) = CoachPlan(
    id = id,
    blockStart = LocalDate(2026, 10, 19),
    blockWeeks = 4,
    intention = "fat_loss",
    status = status,
    nutrition = PlanNutrition(
        targetKcal = if (limitedBy == PlanLimit.Floor) 1200 else 2000,
        floorKcal = 1200,
        limitedBy = limitedBy,
        expenditureKcal = 2400.0,
        ratePctWeek = 0.5,
        rateKgWeek = -0.375,
        strategy = GoalStrategy.Deficit,
        proteinG = 150,
        carbsG = 200,
        fatG = 60,
        proteinPct = 30,
        carbsPct = 40,
        fatPct = 30,
        distribution = ProteinDistribution(perMealG = 30, minMeals = 4, gPerKg = 0.4),
        editedByUser = editedByUser,
    ),
    outcomes = listOfNotNull(
        weightTarget?.let {
            PlanOutcome(OutcomeKind.Weight, "weight", it, "Bajar de forma gradual.", LocalDate(2026, 11, 16))
        },
    ),
    acceptedAt = null,
)

internal fun calorieChange(id: String = "change-1") = CalorieChange(
    id = id,
    planId = "plan-1",
    fromKcal = 2000,
    toKcal = 2100,
    changePct = 5.0,
    reasonCode = "rate_outside_interval",
    slopeKgWeek = -0.1,
    intervalLow = -0.2,
    intervalHigh = 0.0,
)

/** Test double of [CoachPlanRepository]; every call is recorded and each answer is a settable result. */
internal class FakeCoachPlanRepository : CoachPlanRepository {
    var request: Result<CoachPlan> = Result.success(coachPlan())
    var current: Result<CoachPlan?> = Result.success(null)
    var accepted: Result<CoachPlan?> = Result.success(null)
    var acceptResult: Result<CoachPlan> = Result.success(coachPlan(status = PlanStatus.Accepted))
    var discardResult: Result<Unit> = Result.success(Unit)
    var pending: Result<CalorieChange?> = Result.success(null)
    var decideResult: Result<Unit> = Result.success(Unit)

    /** When set, [requestPlan] suspends until completed (a plan being prepared). */
    var requestGate: CompletableDeferred<Unit>? = null

    val requests = mutableListOf<LocalDate>()
    val acceptCalls = mutableListOf<Pair<String, LocalDate>>()
    val discardCalls = mutableListOf<String>()
    val decisions = mutableListOf<Pair<String, Boolean>>()
    var currentReads = 0
    var pendingReads = 0

    override suspend fun requestPlan(today: LocalDate): Result<CoachPlan> {
        requests += today
        requestGate?.await()
        return request
    }

    override suspend fun currentPlan(): Result<CoachPlan?> {
        currentReads++
        return current
    }

    override suspend fun acceptedPlan(): Result<CoachPlan?> = accepted

    override suspend fun acceptPlan(planId: String, today: LocalDate): Result<CoachPlan> {
        acceptCalls += planId to today
        return acceptResult
    }

    override suspend fun discardPlan(planId: String): Result<Unit> {
        discardCalls += planId
        return discardResult
    }

    override suspend fun pendingCalorieChange(today: LocalDate): Result<CalorieChange?> {
        pendingReads++
        return pending
    }

    override suspend fun decideCalorieChange(id: String, accept: Boolean, today: LocalDate): Result<Unit> {
        decisions += id to accept
        return decideResult
    }
}
