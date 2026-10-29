package io.github.lopezjavier.appfitness.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RepRangeTest {
    @Test
    fun validRange_isAccepted() {
        assertEquals(6, RepRange(6, 8).min)
        assertEquals(RepRange(1, 50), RepRange.ofOrNull(1, 50))
        assertEquals(RepRange(8, 8), RepRange.ofOrNull(8, 8))
    }

    @Test
    fun minAboveMax_isRejected() {
        assertFailsWith<IllegalArgumentException> { RepRange(9, 8) }
        assertNull(RepRange.ofOrNull(9, 8))
    }

    @Test
    fun outOfBounds_isRejected() {
        assertNull(RepRange.ofOrNull(0, 8))
        assertNull(RepRange.ofOrNull(8, 51))
    }

    @Test
    fun missingBound_givesNoRange() {
        assertNull(RepRange.ofOrNull(null, 8))
        assertNull(RepRange.ofOrNull(8, null))
    }
}
