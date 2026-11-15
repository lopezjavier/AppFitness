package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import io.github.lopezjavier.appfitness.domain.model.PlanOutcome
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeightPaceFitTest {
    // A Monday: the last window week is the week of this day.
    private val today = LocalDate(2026, 11, 23)
    private val tolerance = 1e-9

    private fun windowStartOf(weeks: Int) = today.minus(DatePeriod(days = 7 * (weeks - 1)))

    /**
     * One entry of [weeklyKg] per window week, ending the week of [today]: three weigh-ins at the week's value, except
     * the current week (one, on Monday) and the weeks whose count is overridden in [days].
     */
    private fun series(vararg weeklyKg: Double, days: Map<Int, Int> = emptyMap()): List<DailyWeight> {
        val start = windowStartOf(weeklyKg.size)
        return weeklyKg.flatMapIndexed { index, kg ->
            val count = days[index] ?: if (index == weeklyKg.lastIndex) 1 else 3
            (0 until count).map { DailyWeight(start.plus(DatePeriod(days = index * 7 + it)), kg) }
        }
    }

    private fun line(weeks: Int, from: Double, perWeek: Double) = DoubleArray(weeks) { from + perWeek * it }

    private fun compute(
        daily: List<DailyWeight>,
        rate: Double,
        phase: Phase = Phase.Cut,
        weeksInPhase: PhaseWeeks? = null,
        planStart: LocalDate = LocalDate(2026, 1, 1),
        outcome: PlanOutcome? = null,
    ) = WeightPaceFit.compute(daily, today, rate, phase, weeksInPhase, planStart, outcome)

    private fun five(rate: Double, vararg kg: Double, phase: Phase = Phase.Cut) =
        compute(series(*kg), rate, phase, PhaseWeeks(phase, kg.size))

    // States

    @Test
    fun noWeighIns_isNoData() {
        val pace = compute(emptyList(), -0.5, weeksInPhase = PhaseWeeks(Phase.Cut, 5))

        assertEquals(WeightPaceState.NoData, pace.state)
        assertNull(pace.slopeKgWeek)
    }

    @Test
    fun oneWeeklyAverage_isNoData() {
        val pace = five(-0.5, 80.0)

        assertEquals(WeightPaceState.NoData, pace.state)
        assertNull(pace.slopeKgWeek)
    }

    @Test
    fun twoWeeklyAverages_giveASlopeWithoutIntervalAndAreSoon() {
        val pace = compute(series(80.0, 79.5), -0.5, weeksInPhase = PhaseWeeks(Phase.Cut, 2))

        assertEquals(WeightPaceState.Soon, pace.state)
        assertEquals(-0.5, assertNotNull(pace.slopeKgWeek), tolerance)
        assertNull(pace.slopeLowKgWeek)
        assertNull(pace.slopeHighKgWeek)
    }

    @Test
    fun threeWeeklyAverages_areStillSoonButHaveAnInterval() {
        val pace = compute(series(80.0, 79.4, 79.1), -0.5, weeksInPhase = PhaseWeeks(Phase.Cut, 3))

        assertEquals(WeightPaceState.Soon, pace.state)
        assertNotNull(pace.slopeLowKgWeek)
        assertNotNull(pace.slopeHighKgWeek)
    }

    @Test
    fun fourAverages_butUnderThreeDaysInTheLatestCompleteWeek_areSoon() {
        val kg = line(5, 80.0, -0.5)

        val pace = compute(series(*kg, days = mapOf(3 to 2)), -0.5, weeksInPhase = PhaseWeeks(Phase.Cut, 5))

        assertEquals(WeightPaceState.Soon, pace.state)
    }

    @Test
    fun cut_intervalContainingTheRateAndNotZero_isOnPace() {
        assertEquals(WeightPaceState.OnPace, five(-0.5, *line(5, 80.0, -0.5)).state)
    }

    @Test
    fun interval_edgeCountsAsInside() {
        // A perfect line gives a zero-width interval exactly on the rate.
        val pace = five(-0.5, 80.0, 79.5, 79.0, 78.5, 78.0)

        assertEquals(pace.slopeLowKgWeek, pace.slopeHighKgWeek)
        assertEquals(-0.5, assertNotNull(pace.slopeLowKgWeek), 0.0)
        assertEquals(WeightPaceState.OnPace, pace.state)
    }

    @Test
    fun cut_intervalContainingTheRateAndZero_isSoon() {
        assertEquals(WeightPaceState.Soon, five(-0.5, 80.0, 78.0, 80.0, 78.0, 79.0).state)
    }

    @Test
    fun cut_intervalAboveTheRate_isSlower() {
        assertEquals(WeightPaceState.Slower, five(-0.5, *line(5, 80.0, -0.2)).state)
    }

    @Test
    fun cut_intervalBelowTheRate_isFaster() {
        assertEquals(WeightPaceState.Faster, five(-0.5, *line(5, 80.0, -1.0)).state)
    }

    @Test
    fun gain_intervalBelowTheRate_isSlower() {
        assertEquals(WeightPaceState.Slower, five(0.5, *line(5, 80.0, 0.2), phase = Phase.Gain).state)
    }

    @Test
    fun gain_intervalAboveTheRate_isFaster() {
        assertEquals(WeightPaceState.Faster, five(0.5, *line(5, 80.0, 1.0), phase = Phase.Gain).state)
    }

    @Test
    fun rateZero_intervalContainingZero_isStable() {
        assertEquals(WeightPaceState.Stable, five(0.0, 80.0, 78.0, 80.0, 78.0, 79.0, phase = Phase.Maintenance).state)
    }

    @Test
    fun rateZero_intervalAboveZero_isRising() {
        assertEquals(WeightPaceState.Rising, five(0.0, *line(5, 80.0, 0.2), phase = Phase.Maintenance).state)
    }

    @Test
    fun rateZero_intervalBelowZero_isFalling() {
        assertEquals(WeightPaceState.Falling, five(0.0, *line(5, 80.0, -0.2), phase = Phase.Maintenance).state)
    }

    // Planning/04 section 7.4 example

    @Test
    fun weeklyAverages_80_79_79_78_giveTheDocumentedSlopeAndInterval() {
        val pace = compute(series(80.0, 79.0, 79.0, 78.0), -0.6, weeksInPhase = PhaseWeeks(Phase.Cut, 4))

        assertEquals(-0.6, assertNotNull(pace.slopeKgWeek), 1e-6)
        assertEquals(-1.209, assertNotNull(pace.slopeLowKgWeek), 1e-3)
        assertEquals(0.009, assertNotNull(pace.slopeHighKgWeek), 1e-3)
        // The interval contains the rate and zero: no claim yet.
        assertEquals(WeightPaceState.Soon, pace.state)
    }

    // Early-cut line

    @Test
    fun earlyCutNote_isSetForAFasterCutWithinSixWeeksOfThePhase() {
        val pace = five(-0.5, *line(5, 80.0, -1.0))

        assertEquals(WeightPaceState.Faster, pace.state)
        assertTrue(pace.earlyCutNote)
    }

    @Test
    fun earlyCutNote_isNotSetAfterSixWeeksOfTheCut() {
        val pace = compute(series(*line(8, 80.0, -1.0)), -0.5, weeksInPhase = PhaseWeeks(Phase.Cut, 8))

        assertEquals(WeightPaceState.Faster, pace.state)
        assertFalse(pace.earlyCutNote)
    }

    @Test
    fun earlyCutNote_isNotSetWhenTheWeeksBelongToAnotherPhase() {
        val pace = compute(series(*line(8, 80.0, -1.0)), -0.5, weeksInPhase = PhaseWeeks(Phase.Gain, 3))

        assertEquals(WeightPaceState.Faster, pace.state)
        assertFalse(pace.earlyCutNote)
        assertNull(pace.weeksInPhase)
    }

    @Test
    fun earlyCutNote_isNotSetForAGainThatIsFaster() {
        assertFalse(five(0.5, *line(5, 80.0, 1.0), phase = Phase.Gain).earlyCutNote)
    }

    @Test
    fun earlyCutNote_isNotSetWhenTheCutIsNotFaster() {
        assertFalse(five(-0.5, *line(5, 80.0, -0.5)).earlyCutNote)
    }

    // Window

    @Test
    fun window_isClippedToTheWeeksInThePhase() {
        val pace = compute(series(*line(8, 80.0, -0.5)), -0.5, weeksInPhase = PhaseWeeks(Phase.Cut, 3))

        assertEquals(3, pace.windowWeeks)
        assertEquals(windowStartOf(3), pace.windowStart)
        assertEquals(3, pace.weekly.size)
        assertEquals(3, pace.weeksInPhase)
    }

    @Test
    fun window_isCappedAtEightWeeks() {
        val pace = compute(series(*line(8, 80.0, -0.5)), -0.5, weeksInPhase = PhaseWeeks(Phase.Cut, 12))

        assertEquals(8, pace.windowWeeks)
        assertEquals(windowStartOf(8), pace.windowStart)
    }

    @Test
    fun window_isEightWeeksWithoutKnownWeeksInPhase() {
        val pace = compute(series(*line(8, 80.0, -0.5)), -0.5)

        assertEquals(8, pace.windowWeeks)
        assertNull(pace.weeksInPhase)
    }

    @Test
    fun window_weeksOfAnotherPhaseAreIgnored() {
        val pace = compute(series(*line(8, 80.0, -0.5)), -0.5, Phase.Cut, PhaseWeeks(Phase.Maintenance, 2))

        assertEquals(8, pace.windowWeeks)
    }

    @Test
    fun weeklyAverages_markOnlyTheCurrentWeekAsPartial() {
        val pace = compute(series(80.0, 79.0, 78.0), -0.5, weeksInPhase = PhaseWeeks(Phase.Cut, 3))

        assertEquals(listOf(false, false, true), pace.weekly.map { it.partial })
        assertEquals(listOf(3, 3, 1), pace.weekly.map { it.days })
    }

    @Test
    fun dailyPoints_areInFractionalWeeksFromTheWindowStart() {
        val start = windowStartOf(2)
        val daily = listOf(DailyWeight(start.plus(DatePeriod(days = 3)), 80.0), DailyWeight(today, 79.0))

        val pace = compute(daily, -0.5, weeksInPhase = PhaseWeeks(Phase.Cut, 2))

        assertEquals(listOf(3.0 / 7.0, 1.0), pace.daily.map { it.weeks })
    }

    @Test
    fun weighIns_outsideTheWindowAreIgnored() {
        val daily = series(80.0, 79.0) + DailyWeight(windowStartOf(2).minus(DatePeriod(days = 1)), 90.0) +
            DailyWeight(today.plus(DatePeriod(days = 1)), 90.0)

        val pace = compute(daily, -0.5, weeksInPhase = PhaseWeeks(Phase.Cut, 2))

        assertEquals(2, pace.weekly.size)
        assertEquals(4, pace.daily.size)
    }

    // Plan line

    private val deadline = LocalDate(2026, 12, 21)

    private fun outcome(kg: Double = 75.0, on: LocalDate = deadline) =
        PlanOutcome(OutcomeKind.Weight, "weight", kg, "reason", on)

    @Test
    fun planLine_isNullWithoutAnOutcome() {
        assertNull(five(-0.5, *line(5, 80.0, -0.5)).planLine)
    }

    @Test
    fun planLine_startIsClippedToTheWindowStart_andEndsAtTheOutcome() {
        val start = windowStartOf(5)

        val planLine = assertNotNull(
            compute(
                series(*line(5, 80.0, -0.5)),
                -0.5,
                weeksInPhase = PhaseWeeks(Phase.Cut, 5),
                planStart = start.minus(DatePeriod(days = 30)),
                outcome = outcome(),
            ).planLine,
        )

        assertEquals(0.0, planLine.startWeeks, tolerance)
        assertEquals(8.0, planLine.endWeeks, tolerance)
        assertEquals(75.0, planLine.endKg, tolerance)
        assertEquals(79.0, planLine.startKg, tolerance)
    }

    @Test
    fun planLine_slopeEqualsThePlanRate() {
        val start = windowStartOf(5)

        val planLine = assertNotNull(
            compute(
                series(*line(5, 80.0, -0.5)),
                -0.5,
                weeksInPhase = PhaseWeeks(Phase.Cut, 5),
                planStart = start.plus(DatePeriod(days = 7)),
                outcome = outcome(),
            ).planLine,
        )

        assertEquals(1.0, planLine.startWeeks, tolerance)
        assertEquals(-0.5, (planLine.endKg - planLine.startKg) / (planLine.endWeeks - planLine.startWeeks), tolerance)
    }

    @Test
    fun planLine_isNullWhenTheDeadlineIsNotAfterThePlanStart() {
        val start = windowStartOf(5)

        val pace = compute(
            series(*line(5, 80.0, -0.5)),
            -0.5,
            weeksInPhase = PhaseWeeks(Phase.Cut, 5),
            planStart = start.plus(DatePeriod(days = 14)),
            outcome = outcome(on = start.plus(DatePeriod(days = 7))),
        )

        assertNull(pace.planLine)
    }
}
