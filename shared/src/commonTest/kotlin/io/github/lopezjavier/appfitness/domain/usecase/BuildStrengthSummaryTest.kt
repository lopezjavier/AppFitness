package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BuildStrengthSummaryTest {
    private val today = LocalDate(2026, 10, 19)

    private fun point(date: LocalDate, best: Double?) = ExerciseHistoryPoint("s-$date", date, best, 0.0)

    @Test
    fun recordIsTheBestOneRepMaxOfTheWholeHistory() {
        val summary = BuildStrengthSummary(
            listOf(point(LocalDate(2026, 3, 1), 113.5), point(LocalDate(2026, 10, 1), 100.0)),
            today,
        )

        assertEquals(113.5, summary.recordOneRepMax)
    }

    @Test
    fun deltaIsTheRecordMinusTheBestOfThirtyDaysAgo() {
        val summary = BuildStrengthSummary(
            listOf(point(LocalDate(2026, 9, 1), 108.5), point(LocalDate(2026, 10, 15), 113.5)),
            today,
        )

        assertEquals(5.0, summary.monthlyDelta)
    }

    @Test
    fun pointOnTheThirtyDayCutOffCountsAsPrior() {
        val summary = BuildStrengthSummary(
            listOf(point(LocalDate(2026, 9, 19), 100.0), point(LocalDate(2026, 10, 15), 110.0)),
            today,
        )

        assertEquals(10.0, summary.monthlyDelta)
    }

    @Test
    fun deltaIsZeroWhenTheRecordIsOlderThanThirtyDays() {
        val summary = BuildStrengthSummary(
            listOf(point(LocalDate(2026, 8, 1), 120.0), point(LocalDate(2026, 10, 15), 100.0)),
            today,
        )

        assertEquals(0.0, summary.monthlyDelta)
    }

    @Test
    fun deltaIsNullWithoutDataFromBeforeTheLastThirtyDays() {
        val summary = BuildStrengthSummary(listOf(point(LocalDate(2026, 10, 15), 110.0)), today)

        assertNull(summary.monthlyDelta)
        assertEquals(110.0, summary.recordOneRepMax)
    }

    @Test
    fun pointsWithoutOneRepMaxAreIgnored() {
        val summary = BuildStrengthSummary(listOf(point(LocalDate(2026, 10, 15), null)), today)

        assertNull(summary.recordOneRepMax)
        assertNull(summary.monthlyDelta)
    }
}
