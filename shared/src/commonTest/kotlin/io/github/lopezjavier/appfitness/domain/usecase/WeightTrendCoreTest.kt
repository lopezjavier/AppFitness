package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun row(at: String, kg: Double, id: String = at) =
    BodyMeasurement(id = id, measuredAt = Instant.parse(at), weightKg = kg, source = MeasurementSource.Manual)

private fun day(d: Int, kg: Double) = DailyWeight(LocalDate(2026, 10, d), kg)

class DailyWeightsTest {
    private val dailyWeights = DailyWeights()

    @Test
    fun severalMeasurementsOfADay_areAveraged() {
        val result = dailyWeights(
            listOf(row("2026-10-20T07:00:00Z", 74.0), row("2026-10-20T20:00:00Z", 75.0)),
            TimeZone.UTC,
        )

        assertEquals(listOf(DailyWeight(LocalDate(2026, 10, 20), 74.5)), result)
    }

    @Test
    fun localDayBoundary_splitsMeasurementsAroundMidnight() {
        val zone = TimeZone.of("Europe/Madrid") // UTC+2 in October
        val result = dailyWeights(
            listOf(row("2026-10-20T21:30:00Z", 74.0), row("2026-10-20T22:30:00Z", 76.0)),
            zone,
        )

        assertEquals(listOf(LocalDate(2026, 10, 20), LocalDate(2026, 10, 21)), result.map { it.date })
        assertEquals(listOf(74.0, 76.0), result.map { it.kg })
    }

    @Test
    fun yesterdayMorning_landsOnYesterday() {
        val result = dailyWeights(listOf(row("2026-10-20T07:30:00Z", 74.0)), TimeZone.UTC)

        assertEquals(LocalDate(2026, 10, 20), result.single().date)
    }

    @Test
    fun result_isSortedOldestFirst() {
        val result = dailyWeights(
            listOf(row("2026-10-21T07:00:00Z", 1.0), row("2026-10-19T07:00:00Z", 2.0)),
            TimeZone.UTC,
        )

        assertEquals(listOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 21)), result.map { it.date })
    }
}

class MovingAverageTest {
    private val ma = MovingAverage()

    @Test
    fun window_coversSixDaysBackAndTheDayItself() {
        val daily = listOf(day(1, 100.0), day(2, 80.0), day(8, 70.0))

        // [d-6, d] for d = 8 is 2..8: the day 1 value is out.
        assertEquals(75.0, ma.at(daily, LocalDate(2026, 10, 8)))
    }

    @Test
    fun singleDayInWindow_hasNoPoint() {
        assertNull(ma.at(listOf(day(1, 80.0)), LocalDate(2026, 10, 3)))
    }

    @Test
    fun gapsAreSkipped_averageUsesAvailableDays() {
        val daily = listOf(day(1, 80.0), day(4, 82.0), day(7, 84.0))

        assertEquals(82.0, ma.at(daily, LocalDate(2026, 10, 7)))
    }

    @Test
    fun dayWithoutData_stillHasMaFromTheWindow() {
        val daily = listOf(day(1, 80.0), day(2, 82.0))

        assertEquals(81.0, ma.at(daily, LocalDate(2026, 10, 5)))
    }

    @Test
    fun noDataInWindow_hasNoPoint() {
        assertNull(ma.at(listOf(day(1, 80.0), day(2, 82.0)), LocalDate(2026, 10, 20)))
    }
}

class WeeklyRateTest {
    private val rate = CalculateWeeklyRate()

    /** Two days around the previous MA7 date (day 8) and two around the last one (day 15). */
    private fun series(previous: Double, current: Double) = listOf(
        day(7, previous),
        day(8, previous),
        day(14, current),
        day(15, current),
    )

    @Test
    fun deficitLosingWeight_isFavorable() {
        val result = rate(series(80.0, 79.55), GoalStrategy.Deficit)

        assertEquals(-0.45, result.value)
        assertEquals(RateTone.Favorable, result.tone)
    }

    @Test
    fun deficitGainingWeight_isUnfavorable() {
        val result = rate(series(80.0, 80.3), GoalStrategy.Deficit)

        assertEquals(0.30, result.value)
        assertEquals(RateTone.Unfavorable, result.tone)
    }

    @Test
    fun deficitWithZeroRate_isUnfavorable() {
        val result = rate(series(80.0, 80.0), GoalStrategy.Deficit)

        assertEquals(0.0, result.value)
        assertEquals(RateTone.Unfavorable, result.tone)
    }

    @Test
    fun surplusIsMirrored() {
        assertEquals(RateTone.Favorable, rate(series(80.0, 80.3), GoalStrategy.Surplus).tone)
        assertEquals(RateTone.Unfavorable, rate(series(80.0, 79.7), GoalStrategy.Surplus).tone)
        assertEquals(RateTone.Unfavorable, rate(series(80.0, 80.0), GoalStrategy.Surplus).tone)
    }

    @Test
    fun maintenanceWithinBand_isFavorable() {
        assertEquals(RateTone.Favorable, rate(series(80.0, 80.15), GoalStrategy.Maintenance).tone)
        assertEquals(RateTone.Favorable, rate(series(80.0, 79.85), GoalStrategy.Maintenance).tone)
    }

    @Test
    fun maintenanceBeyondBand_isCaution() {
        assertEquals(RateTone.Caution, rate(series(80.0, 80.25), GoalStrategy.Maintenance).tone)
    }

    @Test
    fun maintenanceExactlyAtBand_isFavorable() {
        val result = rate(series(80.0, 80.2), GoalStrategy.Maintenance)

        assertEquals(0.2, result.value)
        assertEquals(RateTone.Favorable, result.tone)
    }

    @Test
    fun missingPreviousMa_isNotEnoughData() {
        val result = rate(listOf(day(14, 80.0), day(15, 80.0)), GoalStrategy.Deficit)

        assertNull(result.value)
        assertEquals(RateTone.Neutral, result.tone)
    }

    @Test
    fun noData_isNotEnoughData() {
        assertNull(rate(emptyList(), GoalStrategy.Deficit).value)
    }

    @Test
    fun rateIsRoundedToTwoDecimals() {
        val result = rate(series(80.0, 79.5449), GoalStrategy.Deficit)

        assertEquals(-0.46, result.value)
    }
}

class WeightChartRangeTest {
    private val today = LocalDate(2026, 10, 21)

    @Test
    fun days_matchTheFilters() {
        assertEquals(
            listOf(7, 30, 90, 180, 365),
            WeightChartRange.entries.map { it.days },
        )
    }

    @Test
    fun default_isThreeMonths() {
        assertEquals(WeightChartRange.Quarter, WeightChartRange.Default)
    }

    @Test
    fun month_windowHasSixDayLeadInAndExclusiveEnd() {
        val window = WeightChartRange.Month.window(today)

        assertEquals(LocalDate(2026, 9, 21), window.visibleStart)
        assertEquals(LocalDate(2026, 9, 15), window.fetchStart)
        assertEquals(LocalDate(2026, 10, 22), window.endExclusive)
    }

    @Test
    fun week_fetchesAtLeastFourteenDaysForTheRate() {
        val window = WeightChartRange.Week.window(today)

        assertEquals(LocalDate(2026, 10, 14), window.visibleStart)
        assertEquals(LocalDate(2026, 10, 1), window.fetchStart) // 14 days + 6 lead-in back
    }

    @Test
    fun year_visibleStartIs365DaysBack() {
        assertEquals(LocalDate(2025, 10, 21), WeightChartRange.Year.window(today).visibleStart)
    }
}

class WeightTrendTest {
    private val trend = WeightTrend()
    private val today = LocalDate(2026, 10, 21)

    @Test
    fun ninetyDayFixture_hasPointsMaAndRate() {
        val rows = (0..95).map { back ->
            val date = LocalDate.fromEpochDays(today.toEpochDays() - back)
            row("${date}T08:00:00Z", 80.0 - (95 - back) * 0.05, id = "m$back")
        }

        val data = trend(rows, WeightChartRange.Quarter, GoalStrategy.Deficit, TimeZone.UTC, today)

        assertTrue(data.hasEnoughData)
        assertEquals(91, data.points.size) // today and the 90 days back
        assertEquals(today, data.points.last().date)
        assertNotNull(data.points.first().ma7Kg)
        assertEquals(RateTone.Favorable, data.rate.tone) // weight falls over the fixture
        assertEquals(-0.35, data.rate.value)
    }

    @Test
    fun singleMeasurement_isNotEnoughData() {
        val data = trend(
            listOf(row("2026-10-21T08:00:00Z", 74.2)),
            WeightChartRange.Quarter,
            GoalStrategy.Deficit,
            TimeZone.UTC,
            today,
        )

        assertFalse(data.hasEnoughData)
        assertNull(data.rate.value)
        assertEquals(1, data.points.size)
        assertNull(data.points.single().ma7Kg)
    }

    @Test
    fun leadInData_feedsMaOfFirstVisibleDayButIsNotDrawn() {
        val rows = listOf(
            row("2026-10-12T08:00:00Z", 80.0), // before the 1S window (starts on the 14th)
            row("2026-10-14T08:00:00Z", 82.0),
        )

        val data = trend(rows, WeightChartRange.Week, GoalStrategy.Maintenance, TimeZone.UTC, today)

        assertEquals(listOf(LocalDate(2026, 10, 14)), data.points.map { it.date })
        assertEquals(81.0, data.points.single().ma7Kg)
    }
}
