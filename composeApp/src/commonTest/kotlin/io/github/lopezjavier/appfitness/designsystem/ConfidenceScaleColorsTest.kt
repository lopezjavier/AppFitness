package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfidenceScaleColorsTest {
    private val c = KineticDarkColors

    @Test
    fun theScaleRunsFromRedAtOneToGreenAtTen() {
        assertColor(0xDC2626, c.confidenceScale(1), name = "confidence 1")
        assertColor(0x22C55E, c.confidenceScale(10), name = "confidence 10")
    }

    @Test
    fun outOfRangeValues_clampToTheEnds() {
        assertEquals(c.confidenceScale(1), c.confidenceScale(0))
        assertEquals(c.confidenceScale(10), c.confidenceScale(11))
    }

    @Test
    fun everyValue_hasItsOwnColour_andGreenGrowsMonotonically() {
        val colours = (1..10).map { c.confidenceScale(it) }
        assertEquals(10, colours.toSet().size)
        colours.zipWithNext().forEach { (low, high) -> assertTrue(high.green >= low.green || high.red <= low.red) }
        assertTrue(colours.first().red > colours.first().green)
        assertTrue(colours.last().green > colours.last().red)
    }

    @Test
    fun everyValue_hasAtLeast3To1AgainstTheOverlaySurface() {
        (1..10).forEach {
            val ratio = contrastRatio(c.confidenceScale(it), c.surfaceOverlay)
            assertTrue(ratio >= 3f, "confidence $it: contrast $ratio < 3")
        }
    }
}
