package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockOutcome
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.usecase.WeightPaceState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val TODAY = LocalDate(2026, 11, 22)
private val PAST = LocalDate(2026, 11, 15)
private val FUTURE = LocalDate(2027, 2, 15)

class BlockReportMapperTest {
    private fun report(review: BlockReview = blockReview(progress = blockProgress()), live: Boolean = true) =
        blockReportOf(review, blockNames, calorieProposal = null, live = live, today = TODAY)

    private fun outcome(
        kind: OutcomeKind = OutcomeKind.E1rm,
        key: String = "id-bench",
        deadline: LocalDate? = PAST,
        reached: Boolean? = false,
        target: Double? = 100.0,
    ) = BlockOutcome(kind, key, target, "kg", deadline, 90.0, reached)

    private fun chipOf(outcome: BlockOutcome) = report(blockReview(outcomes = listOf(outcome))).outcomes.single().chip

    @Test
    fun exercisesWithoutAName_areDroppedBeforeAnythingIsCounted() {
        val progress = assertNotNull(report().progress)

        val exercises = progress.strength.groups.flatMap { it.exercises }
        assertEquals(listOf("id-bench", "id-fly", "id-row"), exercises.map { it.exerciseId })
        assertEquals(listOf("id-bench", "id-row"), progress.lifts.map { it.exerciseId })
        assertEquals(2, assertNotNull(report().coach.sentence).ups)
    }

    @Test
    fun records_dropUnnamedOnesFromTheItemsAndFromTheTotal() {
        val progress = blockProgress(
            records = listOf(recordBadge("id-bench"), recordBadge("id-ghost")),
            recordsTotal = 5,
        )

        val ui = assertNotNull(report(blockReview(progress = progress)).progress)

        assertEquals(listOf("id-bench"), ui.records.map { it.exerciseId })
        assertEquals(4, ui.recordsTotal)
    }

    @Test
    fun recordsTotal_keepsTheServerCountBeyondTheCappedItems() {
        val progress = blockProgress(records = listOf(recordBadge("id-bench")), recordsTotal = 7)

        assertEquals(7, report(blockReview(progress = progress)).progress?.recordsTotal)
    }

    @Test
    fun strength_carriesTheBlockWeekAndTheCutFlag() {
        val strength = assertNotNull(report().progress).strength

        assertEquals(4, strength.blockWeek)
        assertEquals(true, strength.cut)
    }

    @Test
    fun weight_usesTheLastWeekAsCurrentAndTheFirstAsPrevious() {
        val weight = assertNotNull(assertNotNull(report().progress).weight)

        assertEquals(79.8, weight.avgKg)
        assertEquals(4, weight.days)
        assertEquals(5, weight.prevDays)
        assertEquals(-1.8, weight.changeKg)
        assertEquals(4, weight.series.size)
        assertEquals(76.0, weight.goal?.kg)
    }

    @Test
    fun weight_underTheGateInEitherWeek_hasNoChange() {
        listOf(blockWeight(daysLast = 2), blockWeight(daysFirst = 1)).forEach {
            val review = blockReview(progress = blockProgress(weight = it))

            assertNull(assertNotNull(report(review).progress).weight?.changeKg)
        }
    }

    @Test
    fun weight_withoutAnyWeighIn_isNull() {
        val review = blockReview(progress = blockProgress(weight = null))

        assertNull(assertNotNull(report(review).progress).weight)
    }

    @Test
    fun pace_comesFromTheSharedVerdictWithoutWeeksInPhase() {
        val pace = assertNotNull(assertNotNull(report().progress).weight?.pace)

        assertEquals(WeightPaceState.Soon, pace.state) // The band of 80, 79, 79, 78 still holds zero (04 section 7.4).
        assertEquals(-0.6, pace.slopeKgWeek)
        assertEquals(-0.5, pace.planRateKgWeek)
        assertNull(pace.weeksInPhase)
    }

    @Test
    fun pace_isMissingWithoutAPlanRate() {
        val review = blockReview(progress = blockProgress(weight = blockWeight(rate = null)))

        assertNull(assertNotNull(report(review).progress).weight?.pace)
    }

    @Test
    fun v1Review_hasNoProgressAndNoSentenceButStillTheOutcomes() {
        val review = blockReview(outcomes = listOf(outcome()))

        val ui = report(review)

        assertNull(ui.progress)
        assertNull(ui.coach.sentence)
        assertEquals(1, ui.outcomes.size)
    }

    @Test
    fun outcome_reachedShowsReached() {
        assertEquals(OutcomeChip.Reached, chipOf(outcome(reached = true)))
    }

    @Test
    fun outcome_notReachedBeforeTheDeadline_hasNoChip() {
        assertEquals(OutcomeChip.None, chipOf(outcome(deadline = FUTURE)))
    }

    @Test
    fun outcome_notReachedAfterTheDeadline_showsNotReached() {
        assertEquals(OutcomeChip.NotReached, chipOf(outcome(deadline = PAST)))
    }

    @Test
    fun outcome_withNullReachedHasNoChipEvenAfterTheDeadline() {
        assertEquals(OutcomeChip.None, chipOf(outcome(kind = OutcomeKind.Weight, key = "weight", reached = null)))
    }

    @Test
    fun outcome_withoutDeadlineHasNoChip() {
        assertEquals(OutcomeChip.None, chipOf(outcome(deadline = null)))
    }

    @Test
    fun outcome_ofALiftWithoutAName_isNotDrawn() {
        val review = blockReview(outcomes = listOf(outcome(key = "id-ghost"), outcome(key = "id-bench")))

        assertEquals(listOf("id-bench"), report(review).outcomes.map { it.key })
    }

    @Test
    fun outcome_carriesTargetNowAndDeadline() {
        val ui = report(blockReview(outcomes = listOf(outcome()))).outcomes.single()

        assertEquals(100.0, ui.targetKg)
        assertEquals(90.0, ui.nowKg)
        assertEquals(PAST, ui.deadline)
    }
}

class OutcomeChipTest {
    private val deadline = LocalDate(2026, 11, 15)

    @Test
    fun reached_isReachedWhateverTheDate() {
        assertEquals(OutcomeChip.Reached, outcomeChip(true, deadline, deadline.plusDays(30)))
    }

    @Test
    fun notReached_beforeAndOnTheDeadline_hasNoChip() {
        assertEquals(OutcomeChip.None, outcomeChip(false, deadline, deadline.plusDays(-1)))
        assertEquals(OutcomeChip.None, outcomeChip(false, deadline, deadline))
    }

    @Test
    fun notReached_afterTheDeadline_isNotReached() {
        assertEquals(OutcomeChip.NotReached, outcomeChip(false, deadline, deadline.plusDays(1)))
    }

    @Test
    fun notReached_withoutDeadline_hasNoChip() {
        assertEquals(OutcomeChip.None, outcomeChip(false, null, deadline))
    }

    private fun LocalDate.plusDays(days: Int) = LocalDate.fromEpochDays(toEpochDays() + days)
}
