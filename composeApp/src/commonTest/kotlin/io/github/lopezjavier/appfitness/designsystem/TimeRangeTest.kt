package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimeRangeTest {
    @Test
    fun labels_areTheShortSymbolsInOrder() {
        assertEquals(
            listOf("1S", "1M", "3M", "6M", "1A", "Todo"),
            TimeRange.entries.map { it.label },
        )
    }

    @Test
    fun aSubset_keepsTheCallersOrder() {
        val ranges = listOf(TimeRange.Month, TimeRange.Year, TimeRange.All)

        TimeRange.requireValid(ranges, TimeRange.Year)

        assertEquals(listOf("1M", "1A", "Todo"), ranges.map { it.label })
    }

    @Test
    fun moreThanFiveRanges_isRejected() {
        assertFailsWith<IllegalArgumentException> {
            TimeRange.requireValid(TimeRange.entries.toList(), TimeRange.Month)
        }
    }

    @Test
    fun fewerThanTwoRanges_isRejected() {
        assertFailsWith<IllegalArgumentException> {
            TimeRange.requireValid(listOf(TimeRange.Month), TimeRange.Month)
        }
    }

    @Test
    fun aSelectionOutsideTheRanges_isRejected() {
        assertFailsWith<IllegalArgumentException> {
            TimeRange.requireValid(listOf(TimeRange.Week, TimeRange.Month), TimeRange.Year)
        }
    }
}
