package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetCountBucketsTest {
    private val today = LocalDate(2026, 10, 25) // Sunday; its week starts 2026-10-19

    private fun week(y: Int, m: Int, d: Int, sets: Int) = SetCountPoint(LocalDate(y, m, d), sets)

    @Test
    fun weekly_fillsMissingWeeksWithZeroFromTheWindowStartWeek() {
        val window = StatsWindow(LocalDate(2026, 10, 7), today) // Wednesday; week of 2026-10-05

        val result = SetCountBuckets.weekly(listOf(week(2026, 10, 12, 9)), window, today)

        assertEquals(
            listOf(week(2026, 10, 5, 0), week(2026, 10, 12, 9), week(2026, 10, 19, 0)),
            result,
        )
    }

    @Test
    fun weekly_emptyInputForAFiniteWindowIsAllZeros() {
        val window = StatsWindow(LocalDate(2026, 10, 12), today)

        val result = SetCountBuckets.weekly(emptyList(), window, today)

        assertEquals(listOf(week(2026, 10, 12, 0), week(2026, 10, 19, 0)), result)
    }

    @Test
    fun weekly_allTimeStartsAtTheFirstPointAndEmptyIsEmpty() {
        val window = StatsWindow(null, today)

        assertEquals(
            listOf(week(2026, 10, 5, 4), week(2026, 10, 12, 0), week(2026, 10, 19, 2)),
            SetCountBuckets.weekly(listOf(week(2026, 10, 5, 4), week(2026, 10, 19, 2)), window, today),
        )
        assertTrue(SetCountBuckets.weekly(emptyList(), window, today).isEmpty())
    }

    @Test
    fun monthly_groupsWeeksByTheMondayAcrossAYearBoundary() {
        val result = SetCountBuckets.monthly(
            listOf(week(2026, 12, 28, 10), week(2027, 1, 4, 6), week(2027, 1, 11, 5)),
        )

        assertEquals(listOf(week(2026, 12, 1, 10), week(2027, 1, 1, 11)), result)
    }

    @Test
    fun monthly_weekSpanningTwoMonthsBelongsToTheMondaysMonth() {
        // Monday 2026-09-28 covers days of October too.
        val result = SetCountBuckets.monthly(listOf(week(2026, 9, 28, 7), week(2026, 10, 5, 3)))

        assertEquals(listOf(week(2026, 9, 1, 7), week(2026, 10, 1, 3)), result)
    }

    @Test
    fun monthly_fillsEmptyMonthsAndOrdersThem() {
        val result = SetCountBuckets.monthly(listOf(week(2026, 10, 5, 3), week(2026, 7, 6, 8)))

        assertEquals(
            listOf(week(2026, 7, 1, 8), week(2026, 8, 1, 0), week(2026, 9, 1, 0), week(2026, 10, 1, 3)),
            result,
        )
    }

    @Test
    fun bucketsFor_sixMonthsIsWeeklyAndOneYearIsMonthly() {
        val points = listOf(week(2026, 10, 12, 5))

        val sixMonths = SetCountBuckets.bucketsFor(
            ProgressRange.SixMonths,
            points,
            StatsWindow.forRange(ProgressRange.SixMonths, today),
            today,
        )
        val oneYear = SetCountBuckets.bucketsFor(
            ProgressRange.OneYear,
            points,
            StatsWindow.forRange(ProgressRange.OneYear, today),
            today,
        )

        assertTrue(sixMonths.all { it.weekStart.dayOfWeek.ordinal == 0 })
        assertEquals(week(2026, 10, 12, 5), sixMonths.single { it.sets > 0 })
        assertTrue(oneYear.all { it.weekStart.day == 1 })
        assertEquals(13, oneYear.size) // 2025-10 .. 2026-10
        assertEquals(week(2026, 10, 1, 5), oneYear.last())
    }

    @Test
    fun bucketsFor_oneYearFirstBucketIsTheMonthOfTheWindowStartMondayAndKeepsAPartialMonth() {
        // The window starts 2025-10-25 (Saturday); its Monday is 2025-10-20, so October 2025 is the first bucket
        // although the server only counted the sets from the 25th on.
        val window = StatsWindow.forRange(ProgressRange.OneYear, today)
        val result = SetCountBuckets.bucketsFor(ProgressRange.OneYear, listOf(week(2025, 10, 20, 3)), window, today)

        assertEquals(week(2025, 10, 1, 3), result.first())
        assertEquals(13, result.size)
    }

    @Test
    fun bucketsFor_weeklyRangesStayWeekly() {
        val points = listOf(week(2026, 10, 12, 5))

        listOf(ProgressRange.OneMonth, ProgressRange.ThreeMonths, ProgressRange.SixMonths).forEach { range ->
            val result = SetCountBuckets.bucketsFor(range, points, StatsWindow.forRange(range, today), today)
            assertTrue(result.all { it.weekStart.dayOfWeek.ordinal == 0 }, "$range")
            assertEquals(week(2026, 10, 12, 5), result.single { it.sets > 0 })
        }
    }

    @Test
    fun bucketsFor_allTimeIsMonthlyAndEmptyWithoutData() {
        val window = StatsWindow(null, today)

        assertTrue(SetCountBuckets.bucketsFor(ProgressRange.AllTime, emptyList(), window, today).isEmpty())
        assertEquals(
            listOf(week(2026, 9, 1, 2), week(2026, 10, 1, 0)),
            SetCountBuckets.bucketsFor(ProgressRange.AllTime, listOf(week(2026, 9, 7, 2)), window, today),
        )
    }
}
