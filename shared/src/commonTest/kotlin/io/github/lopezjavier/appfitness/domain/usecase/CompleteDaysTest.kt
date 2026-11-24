package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompleteDaysTest {
    private val last = LocalDate(2026, 10, 28)

    private fun day(offsetBack: Int, kcal: Double) = DiaryDaySummary(
        LocalDate.fromEpochDays(last.toEpochDays() - offsetBack),
        MealTotals(kcal, 0.0, 0.0, 0.0),
    )

    @Test
    fun twoPartialDaysOfSeven_leaveFiveComplete() {
        val days = listOf(2000.0, 2100.0, 1900.0, 2000.0, 2200.0, 300.0, 500.0).mapIndexed { i, k -> day(i, k) }

        val result = CompleteDays.window(days, last)

        assertEquals(7, result.logged.size)
        assertEquals(5, result.complete.size)
        assertEquals(2000.0, result.median)
    }

    @Test
    fun oneLoggedDay_isItsOwnMedianAndComplete() {
        val result = CompleteDays.window(listOf(day(0, 100.0)), last)

        assertEquals(1, result.complete.size)
        assertEquals(100.0, result.median)
    }

    @Test
    fun noLoggedDay_hasNoMedian() {
        val result = CompleteDays.window(emptyList(), last)

        assertNull(result.median)
        assertEquals(0, result.logged.size)
    }

    @Test
    fun exactly40PercentIsComplete_justUnderIsPartial() {
        val exact = CompleteDays.window(listOf(day(0, 1000.0), day(1, 1000.0), day(2, 400.0)), last)
        val under = CompleteDays.window(listOf(day(0, 1000.0), day(1, 1000.0), day(2, 399.0)), last)

        assertEquals(3, exact.complete.size)
        assertEquals(2, under.complete.size)
    }

    @Test
    fun evenCount_medianIsMeanOfTheTwoMiddleValues() {
        val result = CompleteDays.window(listOf(day(0, 1000.0), day(1, 2000.0), day(2, 3000.0), day(3, 4000.0)), last)

        assertEquals(2500.0, result.median)
    }

    @Test
    fun daysBeforeThePeriodRaiseTheMedian() {
        val older = (7..13).map { day(it, 3000.0) }
        val period = (0..2).map { day(it, 1000.0) }

        val result = CompleteDays.window(older + period, last, periodFrom = day(2, 0.0).date)

        assertEquals(3000.0, result.median)
        assertEquals(3, result.logged.size)
        assertEquals(0, result.complete.size)
    }

    @Test
    fun dayTwentyNineBack_isOutsideTheWindow() {
        val result = CompleteDays.window(listOf(day(0, 1000.0), day(28, 9000.0), day(27, 1000.0)), last)

        assertEquals(1000.0, result.median)
        assertEquals(2, result.logged.size)
    }

    @Test
    fun periodDaysBeforeTheWindow_areClassifiedButDoNotEnterTheMedian() {
        val result = CompleteDays.window(
            listOf(day(0, 2000.0), day(29, 100.0), day(30, 2000.0)),
            last,
            periodFrom = day(30, 0.0).date,
        )

        assertEquals(2000.0, result.median)
        assertEquals(3, result.logged.size)
        assertEquals(2, result.complete.size)
    }
}
