package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacroSliderTest {
    private val c = KineticDarkColors

    @Test
    fun steps_defaultRange_is84() = assertEquals(84, MacroSliderDefaults.steps(5..90))

    @Test
    fun steps_smallRange() = assertEquals(0, MacroSliderDefaults.steps(5..6))

    @Test
    fun snap_roundsToNearestInt() {
        assertEquals(30, MacroSliderDefaults.snap(30.4f, 5..90))
        assertEquals(31, MacroSliderDefaults.snap(30.5f, 5..90))
        assertEquals(31, MacroSliderDefaults.snap(30.6f, 5..90))
    }

    @Test
    fun snap_coercedIntoRange() {
        assertEquals(5, MacroSliderDefaults.snap(2f, 5..90))
        assertEquals(90, MacroSliderDefaults.snap(95f, 5..90))
    }

    @Test
    fun detailText_rulesExample() {
        assertEquals("180 g · 720 kcal", MacroSliderDefaults.detailText(180, 720))
        assertEquals("270 g · 1.080 kcal", MacroSliderDefaults.detailText(270, 1080))
        assertEquals("67 g · 600 kcal", MacroSliderDefaults.detailText(67, 600))
    }

    @Test
    fun percentText_hasSpaceBeforeSymbol() = assertEquals("30 %", MacroSliderDefaults.percentText(30))

    @Test
    fun sizes_touchAtLeast48_thumb24_track8() {
        assertTrue(MacroSliderDefaults.MinTouchHeight >= 48.dp)
        assertEquals(24.dp, MacroSliderDefaults.ThumbSize)
        assertEquals(8.dp, MacroSliderDefaults.TrackHeight)
        assertEquals(Spacing.lg, MacroSliderDefaults.SliderGap)
    }

    @Test
    fun colors_enabled_macroColour() {
        Macro.entries.forEach { macro ->
            val expected = MacroDefaults.color(macro, c)
            val colors = MacroSliderDefaults.colors(macro, enabled = true, appColors = c)
            assertEquals(expected, colors.thumb)
            assertEquals(expected, colors.activeTrack)
            assertEquals(expected, colors.value)
            assertEquals(c.surfaceOverlay, colors.inactiveTrack)
        }
    }

    @Test
    fun colors_disabled_allOpaque() {
        val colors = MacroSliderDefaults.colors(Macro.Protein, enabled = false, appColors = c)
        listOf(colors.thumb, colors.activeTrack, colors.inactiveTrack, colors.value).forEach {
            assertEquals(1f, it.alpha)
        }
    }

    @Test
    fun colors_disabled_isGhostOverRaised() {
        val colors = MacroSliderDefaults.colors(Macro.Protein, enabled = false, appColors = c)
        val expected = c.ghostData.compositeOver(c.surfaceRaised)
        assertEquals(expected, colors.thumb)
        assertEquals(expected, colors.activeTrack)
        assertEquals(expected, colors.value)
        assertEquals(c.surfaceOverlay, colors.inactiveTrack)
    }

    @Test
    fun coercePercent_belowRange_isFirst() = assertEquals(5, MacroSliderDefaults.coercePercent(2, 5..90))

    @Test
    fun coercePercent_aboveRange_isLast() = assertEquals(90, MacroSliderDefaults.coercePercent(95, 5..90))

    @Test
    fun coercePercent_inRange_unchanged() = assertEquals(30, MacroSliderDefaults.coercePercent(30, 5..90))

    @Test
    fun valueRange_matchesRules() = assertEquals(5..90, MacroSliderDefaults.ValueRange)

    @Test
    fun textStyles_haveTabularFigures() {
        val t = appTypography(FontFamily.Default)
        assertEquals(NUMERIC_FONT_FEATURES, MacroSliderDefaults.valueStyle(t).fontFeatureSettings)
        assertEquals(NUMERIC_FONT_FEATURES, MacroSliderDefaults.detailStyle(t).fontFeatureSettings)
        assertEquals(NUMERIC_FONT_FEATURES, MacroSliderDefaults.labelStyle(t).fontFeatureSettings)
    }
}
