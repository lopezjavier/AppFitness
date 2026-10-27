package io.github.lopezjavier.appfitness.feature.analytics

import io.github.lopezjavier.appfitness.designsystem.TimeRange
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProgressRangeUiTest {
    @Test
    fun everyProgressRangeSurvivesTheRoundTrip() {
        ProgressRange.entries.forEach { assertEquals(it, it.toTimeRange().toProgressRange()) }
    }

    @Test
    fun theSelectorShowsFiveDistinctRangesInDisplayOrder() {
        assertEquals(5, ProgressRanges.toSet().size)
        assertEquals(ProgressRange.entries.map { it.toTimeRange() }, ProgressRanges)
        assertEquals(listOf("1M", "3M", "6M", "1A", "Todo"), ProgressRanges.map { it.label })
    }

    @Test
    fun theRangesFormAValidSelectorForEverySelection() {
        ProgressRange.entries.forEach { TimeRange.requireValid(ProgressRanges, it.toTimeRange()) }
    }

    @Test
    fun aWeekIsNotAProgressRange() {
        assertFailsWith<IllegalArgumentException> { TimeRange.Week.toProgressRange() }
    }
}
