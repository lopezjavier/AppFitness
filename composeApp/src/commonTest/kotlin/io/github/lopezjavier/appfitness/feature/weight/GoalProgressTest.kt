package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.designsystem.TimeRange
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoalProgressTest {
    @Test
    fun halfwayToTheTarget_isOneHalf() {
        assertEquals(0.5, goalProgress(startKg = 90.0, currentKg = 85.0, targetKg = 80.0))
    }

    @Test
    fun atOrPastTheTarget_isFull() {
        assertEquals(1.0, goalProgress(90.0, 80.0, 80.0))
        assertEquals(1.0, goalProgress(90.0, 78.0, 80.0))
    }

    @Test
    fun movingAwayFromTheTarget_isZero() {
        assertEquals(0.0, goalProgress(90.0, 93.0, 80.0))
    }

    @Test
    fun gainGoal_progressesUpwards() {
        assertEquals(0.5, goalProgress(60.0, 65.0, 70.0))
    }

    @Test
    fun startEqualToTarget_hasNoProgress() {
        assertNull(goalProgress(80.0, 80.0, 80.0))
    }

    @Test
    fun anyMissingValue_hasNoProgress() {
        assertNull(goalProgress(null, 85.0, 80.0))
        assertNull(goalProgress(90.0, null, 80.0))
        assertNull(goalProgress(90.0, 85.0, null))
    }

    @Test
    fun chartRanges_mapToTheTimeRangeSelectorInOrder() {
        assertEquals(
            listOf(TimeRange.Week, TimeRange.Month, TimeRange.ThreeMonths, TimeRange.SixMonths, TimeRange.Year),
            WeightChartRange.entries.map { it.toTimeRange() },
        )
        assertEquals(
            listOf("1S", "1M", "3M", "6M", "1A"),
            WeightChartRange.entries.map { it.toTimeRange().label },
        )
    }

    @Test
    fun timeRange_mapsBackToTheChartRange() {
        WeightChartRange.entries.forEach { assertEquals(it, it.toTimeRange().toWeightChartRange()) }
    }
}
