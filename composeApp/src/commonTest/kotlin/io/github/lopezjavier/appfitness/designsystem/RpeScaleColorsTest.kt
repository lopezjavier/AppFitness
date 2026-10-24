package io.github.lopezjavier.appfitness.designsystem

import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RpeScaleColorsTest {
    private val c = KineticDarkColors

    @Test
    fun everyWholeValue_hasItsOwnColour() {
        assertEquals(10, RpeScale.values.size)
        assertEquals(10, RpeScale.values.map { c.rpeScale(it) }.toSet().size)
    }

    @Test
    fun theRampRunsFromGreenAtOneToRedAtTen() {
        assertColor(0x22C55E, c.rpeScale(1.0), name = "rpe 1")
        assertColor(0xDC2626, c.rpeScale(10.0), name = "rpe 10")
        assertTrue(c.rpeScale(1.0).green > c.rpeScale(1.0).red)
        assertTrue(c.rpeScale(10.0).red > c.rpeScale(10.0).green)
    }

    @Test
    fun everyMarkerColour_hasAtLeast3To1AgainstTheOverlaySurface() {
        RpeScale.values.forEach {
            val ratio = contrastRatio(c.rpeScale(it), c.surfaceOverlay)
            assertTrue(ratio >= 3f, "rpe $it: contrast $ratio < 3")
        }
    }
}
