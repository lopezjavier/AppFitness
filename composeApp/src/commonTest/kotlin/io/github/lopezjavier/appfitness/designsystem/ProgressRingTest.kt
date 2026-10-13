package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressRingTest {
    private val c = KineticDarkColors

    @Test
    fun state_zeroConsumed_sweep0_notOver() {
        val s = ProgressRingDefaults.state(0, 2400)
        assertEquals(0f, s.sweepAngle)
        assertFalse(s.isOverTarget)
        assertEquals(2400, s.remaining)
    }

    @Test
    fun state_partial_sweepProportional() {
        val s = ProgressRingDefaults.state(1840, 2400)
        assertEquals(276f, s.sweepAngle, 0.001f)
        assertFalse(s.isOverTarget)
        assertEquals(560, s.remaining)
        assertEquals(180f, ProgressRingDefaults.state(1200, 2400).sweepAngle, 0.001f)
    }

    @Test
    fun state_exactlyTarget_sweep360_notOver() {
        val s = ProgressRingDefaults.state(2400, 2400)
        assertEquals(360f, s.sweepAngle)
        assertFalse(s.isOverTarget)
        assertEquals(0, s.remaining)
    }

    @Test
    fun state_overTarget_sweepCappedAt360_over() {
        val s = ProgressRingDefaults.state(2520, 2400)
        assertEquals(360f, s.sweepAngle)
        assertTrue(s.isOverTarget)
        assertEquals(-120, s.remaining)
        assertEquals(360f, ProgressRingDefaults.state(4800, 2400).sweepAngle)
    }

    @Test
    fun state_oneOver_isOver() {
        assertTrue(ProgressRingDefaults.state(2401, 2400).isOverTarget)
    }

    @Test
    fun state_negativeConsumed_treatedAsZero() {
        val s = ProgressRingDefaults.state(-50, 2400)
        assertEquals(0f, s.sweepAngle)
        assertFalse(s.isOverTarget)
    }

    @Test
    fun state_nonPositiveTarget_throws() {
        assertFailsWith<IllegalArgumentException> { ProgressRingDefaults.state(100, 0) }
        assertFailsWith<IllegalArgumentException> { ProgressRingDefaults.state(100, -1) }
    }

    @Test
    fun colors_normal_limeOnOverlayTrack() {
        val colors = ProgressRingDefaults.colors(isOverTarget = false, appColors = c)
        assertEquals(c.surfaceOverlay, colors.track)
        assertEquals(c.neonLime, colors.progress)
        assertEquals(c.neonLime, colors.status)
    }

    @Test
    fun colors_over_amber() {
        val colors = ProgressRingDefaults.colors(isOverTarget = true, appColors = c)
        assertEquals(c.surfaceOverlay, colors.track)
        assertEquals(c.energyAmber, colors.progress)
        assertEquals(c.energyAmber, colors.status)
    }

    @Test
    fun size_innerDiameterFitsHero() {
        assertTrue(ProgressRingDefaults.Size - ProgressRingDefaults.StrokeWidth * 2 >= 152.dp)
        assertEquals(176.dp, ProgressRingDefaults.Size)
    }

    @Test
    fun textStyles_haveTabularFigures() {
        val t = appTypography(FontFamily.Default)
        assertEquals(NUMERIC_FONT_FEATURES, ProgressRingDefaults.valueStyle(t).fontFeatureSettings)
        assertEquals(NUMERIC_FONT_FEATURES, ProgressRingDefaults.captionStyle(t).fontFeatureSettings)
        assertEquals(t.displayHero, ProgressRingDefaults.valueStyle(t))
    }
}
