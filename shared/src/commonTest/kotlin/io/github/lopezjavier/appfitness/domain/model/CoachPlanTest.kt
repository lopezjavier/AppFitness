package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CoachPlanTest {
    private val storedDeadline = LocalDate(2026, 12, 4)
    private val blockEnd = LocalDate(2026, 12, 8)

    private fun outcome(kind: OutcomeKind, key: String, target: Double) =
        PlanOutcome(kind, key, target, "reason-$key", storedDeadline)

    private val plan = CoachPlan(
        id = "p1",
        blockStart = LocalDate(2026, 11, 10),
        blockWeeks = 4,
        intention = "fat_loss",
        status = PlanStatus.Accepted,
        nutrition = PlanNutrition(
            targetKcal = 2500, floorKcal = null, limitedBy = null, expenditureKcal = null, ratePctWeek = 0.0,
            rateKgWeek = 0.0, strategy = GoalStrategy.Maintenance, proteinG = 150, carbsG = 300, fatG = 70,
            proteinPct = 25, carbsPct = 50, fatPct = 25, distribution = null, editedByUser = false,
        ),
        outcomes = listOf(
            outcome(OutcomeKind.Weight, "weight", 72.5),
            outcome(OutcomeKind.E1rm, "bench", 100.0),
        ),
        acceptedAt = null,
    )

    @Test
    fun endingOn_movesEveryOutcomeDeadlineToTheBlockEnd() {
        val moved = plan.endingOn(blockEnd)

        assertEquals(listOf(blockEnd, blockEnd), moved.outcomes.map { it.deadline })
    }

    @Test
    fun endingOn_keepsTargetsKindsKeysAndReasons() {
        val moved = plan.endingOn(blockEnd)

        assertEquals(
            plan.outcomes.map { listOf(it.kind, it.key, it.targetValue, it.reason) },
            moved.outcomes.map { listOf(it.kind, it.key, it.targetValue, it.reason) },
        )
    }

    @Test
    fun endingOn_leavesTheRestOfThePlanUntouched() {
        val moved = plan.endingOn(blockEnd)

        assertEquals(plan.copy(outcomes = moved.outcomes), moved)
    }

    @Test
    fun endingOn_withoutABlockEnd_isTheSamePlan() {
        assertSame(plan, plan.endingOn(null))
    }

    @Test
    fun endingOn_aPlanWithoutOutcomes_staysWithoutOutcomes() {
        assertEquals(emptyList(), plan.copy(outcomes = emptyList()).endingOn(blockEnd).outcomes)
    }
}
