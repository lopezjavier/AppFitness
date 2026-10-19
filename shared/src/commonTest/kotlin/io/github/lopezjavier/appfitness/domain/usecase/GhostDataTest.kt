package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GhostDataTest {
    private val last = listOf(PreviousSet(1, 80.0, 10), PreviousSet(2, 85.0, 8), PreviousSet(3, 82.5, 6))

    @Test
    fun setTwo_showsWeightTimesReps() = assertEquals("85 kg × 8", GhostData.previousText(GhostData.forSet(last, 2)))

    @Test
    fun moreSetsThanLastTime_hasNoPrevious() {
        assertNull(GhostData.forSet(last, 4))
        assertNull(GhostData.previousText(GhostData.forSet(last, 4)))
    }

    @Test
    fun neverDone_hasNoPrevious() = assertNull(GhostData.forSet(emptyList(), 1))

    @Test
    fun decimalWeight_usesACommaAndNoTrailingZero() =
        assertEquals("82,5 kg × 6", GhostData.previousText(GhostData.forSet(last, 3)))

    @Test
    fun thousands_areNotGrouped() = assertEquals("1000 kg × 5", GhostData.previousText(PreviousSet(1, 1000.0, 5)))

    @Test
    fun placeholders_arePreviousWeightAndReps() {
        assertEquals("82,5" to "6", GhostData.placeholders(GhostData.forSet(last, 3)))
        assertNull(GhostData.placeholders(null))
    }
}
