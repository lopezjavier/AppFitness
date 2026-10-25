package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatsWindowTest {
    private val today = LocalDate(2026, 10, 25)

    @Test
    fun forRange_oneMonthEndsTodayAndStartsOneCalendarMonthBack() {
        assertEquals(StatsWindow(LocalDate(2026, 9, 25), today), StatsWindow.forRange(ProgressRange.OneMonth, today))
    }

    @Test
    fun forRange_threeSixAndTwelveMonths() {
        assertEquals(LocalDate(2026, 7, 25), StatsWindow.forRange(ProgressRange.ThreeMonths, today).from)
        assertEquals(LocalDate(2026, 4, 25), StatsWindow.forRange(ProgressRange.SixMonths, today).from)
        assertEquals(LocalDate(2025, 10, 25), StatsWindow.forRange(ProgressRange.OneYear, today).from)
    }

    @Test
    fun forRange_allTimeHasNoLowerBoundAndNoPrevious() {
        val window = StatsWindow.forRange(ProgressRange.AllTime, today)

        assertNull(window.from)
        assertEquals(today, window.to)
        assertNull(window.previous())
    }

    @Test
    fun forRange_monthEndClampsToTheShorterMonth() {
        val window = StatsWindow.forRange(ProgressRange.OneMonth, LocalDate(2026, 3, 31))

        assertEquals(LocalDate(2026, 2, 28), window.from)
    }

    @Test
    fun forRange_leapYearClampsToFebruary29() {
        val window = StatsWindow.forRange(ProgressRange.OneMonth, LocalDate(2028, 3, 31))

        assertEquals(LocalDate(2028, 2, 29), window.from)
    }

    @Test
    fun previous_oneMonthIsTheEqualLengthWindowRightBefore() {
        val current = StatsWindow.forRange(ProgressRange.OneMonth, today)

        val previous = current.previous()!!

        assertEquals(LocalDate(2026, 8, 25), previous.from)
        assertEquals(LocalDate(2026, 9, 24), previous.to)
    }

    @Test
    fun previous_hasTheSameLengthAndIsAdjacentForEveryFiniteRange() {
        val ranges = listOf(
            ProgressRange.OneMonth,
            ProgressRange.ThreeMonths,
            ProgressRange.SixMonths,
            ProgressRange.OneYear,
        )
        for (range in ranges) {
            val current = StatsWindow.forRange(range, LocalDate(2028, 3, 31))
            val previous = current.previous()!!

            assertEquals(current.from!!.daysUntil(current.to), previous.from!!.daysUntil(previous.to), "$range")
            assertEquals(current.from!!.minus(1, DateTimeUnit.DAY), previous.to, "$range")
        }
    }

    @Test
    fun lastSevenDays_isSevenInclusiveDays() {
        val window = StatsWindow.lastSevenDays(today)

        assertEquals(StatsWindow(LocalDate(2026, 10, 19), today), window)
    }

    @Test
    fun lastSevenDays_crossesMonthAndYearBoundaries() {
        assertEquals(LocalDate(2026, 12, 29), StatsWindow.lastSevenDays(LocalDate(2027, 1, 4)).from)
        assertEquals(LocalDate(2026, 2, 23), StatsWindow.lastSevenDays(LocalDate(2026, 3, 1)).from)
    }
}
