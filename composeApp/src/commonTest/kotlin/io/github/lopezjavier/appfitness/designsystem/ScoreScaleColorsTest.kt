package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreScaleColorsTest {
    private val c = KineticDarkColors

    @Test
    fun theScaleRunsFromRedAtZeroToGreenAtTen() {
        assertColor(0xDC2626, c.scoreScale(0.0), name = "score 0")
        assertColor(0x22C55E, c.scoreScale(10.0), name = "score 10")
    }

    @Test
    fun outOfRangeScores_clampToTheEnds() {
        assertEquals(c.scoreScale(0.0), c.scoreScale(-3.0))
        assertEquals(c.scoreScale(10.0), c.scoreScale(12.5))
    }

    @Test
    fun aMiddleScore_isNeitherEnd() {
        val middle = c.scoreScale(5.0)
        assertTrue(middle != c.scoreScale(0.0) && middle != c.scoreScale(10.0))
    }

    @Test
    fun everyWholeAndHalfScore_hasAtLeast3To1AgainstTheRaisedSurface() {
        (0..20).map { it / 2.0 }.forEach {
            val ratio = contrastRatio(c.scoreScale(it), c.surfaceRaised)
            assertTrue(ratio >= 3f, "score $it: contrast $ratio < 3")
        }
    }
}
