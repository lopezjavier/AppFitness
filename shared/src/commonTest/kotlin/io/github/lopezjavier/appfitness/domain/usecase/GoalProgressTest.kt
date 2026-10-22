package io.github.lopezjavier.appfitness.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoalProgressTest {
    private val progress = GoalProgress()

    @Test
    fun planExample_is48Percent() {
        assertEquals(48, progress(78.0, 74.2, 70.0))
    }

    @Test
    fun currentAboveStart_is0() {
        assertEquals(0, progress(78.0, 79.0, 70.0))
    }

    @Test
    fun pastTarget_is100() {
        assertEquals(100, progress(78.0, 69.0, 70.0))
        assertEquals(100, progress(78.0, 70.0, 70.0))
    }

    @Test
    fun startEqualsTarget_hasNoBar() {
        assertNull(progress(70.0, 71.0, 70.0))
    }

    @Test
    fun surplus_usesTheSameFormula() {
        assertEquals(50, progress(60.0, 65.0, 70.0))
        assertEquals(100, progress(60.0, 71.0, 70.0))
        assertEquals(0, progress(60.0, 59.0, 70.0))
    }
}
