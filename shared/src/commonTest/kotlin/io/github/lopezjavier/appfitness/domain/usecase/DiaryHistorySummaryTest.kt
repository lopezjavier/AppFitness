package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiaryHistorySummaryTest {
    private val today = LocalDate(2026, 10, 21)

    private fun day(date: LocalDate, kcal: Double, p: Double = 0.0, c: Double = 0.0, f: Double = 0.0) =
        DiaryDaySummary(date, MealTotals(kcal, p, c, f))

    @Test
    fun day_returnsTheSummaryOfThatDate() {
        val oct18 = LocalDate(2026, 10, 18)
        val summaries = listOf(day(LocalDate(2026, 10, 17), 900.0), day(oct18, 1840.0, 142.0, 210.0, 48.0))

        assertEquals(MealTotals(1840.0, 142.0, 210.0, 48.0), DiaryHistorySummary.day(summaries, oct18)?.totals)
    }

    @Test
    fun day_withoutEntries_isNull() {
        assertNull(DiaryHistorySummary.day(listOf(day(LocalDate(2026, 10, 17), 900.0)), LocalDate(2026, 10, 18)))
    }

    @Test
    fun day_percentsAgainstTheTargets() {
        val totals = MealTotals(1840.0, 142.0, 210.0, 48.0)
        val goals = DiaryGoals(2400, 180, 270, 67)

        assertEquals(79, DiaryTotals.macroPercent(totals.proteinG, goals.proteinG))
        assertEquals(78, DiaryTotals.macroPercent(totals.carbsG, goals.carbsG))
        assertEquals(72, DiaryTotals.macroPercent(totals.fatG, goals.fatG))
    }

    @Test
    fun week_averagesOnlyTheLoggedDays() {
        val summaries = listOf(
            day(LocalDate(2026, 10, 15), 1800.0, 100.0, 200.0, 50.0),
            day(LocalDate(2026, 10, 18), 2000.0, 120.0, 220.0, 60.0),
            day(LocalDate(2026, 10, 20), 2200.0, 140.0, 240.0, 70.0),
        )

        val week = DiaryHistorySummary.week(summaries, today)

        assertEquals(PeriodAverage(3, 2000.0, 120.0, 220.0, 60.0), week)
    }

    @Test
    fun week_includesTodayMinus6AndExcludesTodayMinus7() {
        val summaries = listOf(
            day(LocalDate(2026, 10, 14), 5000.0),
            day(LocalDate(2026, 10, 15), 1000.0),
            day(LocalDate(2026, 10, 21), 2000.0),
            day(LocalDate(2026, 10, 22), 9000.0),
        )

        val week = DiaryHistorySummary.week(summaries, today)

        assertEquals(2, week?.loggedDays)
        assertEquals(1500.0, week?.kcal)
    }

    @Test
    fun week_withoutData_isNull() {
        assertNull(DiaryHistorySummary.week(listOf(day(LocalDate(2026, 9, 1), 1000.0)), today))
        assertNull(DiaryHistorySummary.week(emptyList(), today))
    }

    @Test
    fun week_withOneLoggedDay_isThatDay() {
        val week = DiaryHistorySummary.week(listOf(day(LocalDate(2026, 10, 20), 1700.0, 90.0, 150.0, 40.0)), today)

        assertEquals(PeriodAverage(1, 1700.0, 90.0, 150.0, 40.0), week)
    }

    @Test
    fun month_totalsAndAveragesPerLoggedDay() {
        val summaries = listOf(
            day(LocalDate(2026, 10, 1), 2000.0, 100.0, 200.0, 50.0),
            day(LocalDate(2026, 10, 2), 1000.0, 50.0, 100.0, 25.0),
            day(LocalDate(2026, 10, 31), 3000.0, 150.0, 300.0, 75.0),
        )

        val month = DiaryHistorySummary.month(summaries, YearMonth(2026, 10))

        assertEquals(MonthSummary(3, 6000.0, 2000.0, 100.0, 200.0, 50.0), month)
    }

    @Test
    fun month_ignoresDaysOutsideTheMonth() {
        val summaries = listOf(
            day(LocalDate(2026, 9, 30), 4000.0),
            day(LocalDate(2026, 10, 5), 1500.0),
            day(LocalDate(2026, 11, 1), 4000.0),
        )

        val month = DiaryHistorySummary.month(summaries, YearMonth(2026, 10))

        assertEquals(1, month?.loggedDays)
        assertEquals(1500.0, month?.totalKcal)
    }

    @Test
    fun month_withoutData_isNull() {
        assertNull(DiaryHistorySummary.month(listOf(day(LocalDate(2026, 9, 30), 4000.0)), YearMonth(2026, 10)))
    }

    @Test
    fun month_sumsTenthsWithoutDrift() {
        val summaries = (1..10).map { day(LocalDate(2026, 10, it), 0.1, 0.1, 0.1, 0.1) }

        val month = DiaryHistorySummary.month(summaries, YearMonth(2026, 10))

        assertEquals(1.0, month?.totalKcal)
        assertEquals(0.1, month?.avgKcalPerLoggedDay)
    }
}
