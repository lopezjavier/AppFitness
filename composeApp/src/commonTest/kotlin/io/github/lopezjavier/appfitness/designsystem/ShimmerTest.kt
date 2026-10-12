package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class ShimmerTest {
    @Test
    fun startOfCycle_isOneWidthBeforeTheBox() {
        assertEquals(-100f, shimmerOffsetX(0f, 100f))
    }

    @Test
    fun middleOfCycle_isAtTheBoxStart() {
        assertEquals(0f, shimmerOffsetX(0.5f, 100f))
    }

    @Test
    fun endOfCycle_isOneWidthAfterTheBoxStart() {
        assertEquals(100f, shimmerOffsetX(1f, 100f))
    }

    @Test
    fun outOfRangeProgress_isClamped() {
        assertEquals(-100f, shimmerOffsetX(-0.5f, 100f))
        assertEquals(100f, shimmerOffsetX(1.5f, 100f))
    }
}
