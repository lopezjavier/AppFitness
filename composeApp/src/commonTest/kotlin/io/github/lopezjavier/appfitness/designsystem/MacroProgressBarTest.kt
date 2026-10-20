package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MacroProgressBarTest {
    @Test
    fun percent_decimalInput_halfUp() {
        assertEquals(50, MacroProgressBarDefaults.percent(100.9, 200))
        assertEquals(79, MacroProgressBarDefaults.percent(142.0, 180))
        assertEquals(13, MacroProgressBarDefaults.percent(1.0, 8))
        assertEquals(0, MacroProgressBarDefaults.percent(-1.0, 8))
        assertEquals(111, MacroProgressBarDefaults.percent(200.0, 180))
    }

    @Test
    fun fillFraction_decimalInput_capped() {
        assertEquals(0.5f, MacroProgressBarDefaults.fillFraction(90.0, 180), 0.0001f)
        assertEquals(1f, MacroProgressBarDefaults.fillFraction(200.5, 180))
    }

    @Test
    fun text_decimalInput_showsWholeGramsHalfUp() {
        val text = MacroProgressBarDefaults.text(141.5, 180)
        assertEquals("142 g", text.consumed)
        assertEquals("79 %", text.percent)
        assertEquals("100 g", MacroProgressBarDefaults.text(99.9, 200).consumed)
    }

    @Test
    fun decimalInput_nonPositiveTarget_throws() {
        assertFailsWith<IllegalArgumentException> { MacroProgressBarDefaults.percent(10.0, 0) }
        assertFailsWith<IllegalArgumentException> { MacroProgressBarDefaults.fillFraction(10.0, 0) }
    }

    @Test
    fun percent_rulesExample() {
        assertEquals(79, MacroProgressBarDefaults.percent(142, 180))
        assertEquals(78, MacroProgressBarDefaults.percent(210, 270))
        assertEquals(72, MacroProgressBarDefaults.percent(48, 67))
    }

    @Test
    fun percent_zero() = assertEquals(0, MacroProgressBarDefaults.percent(0, 180))

    @Test
    fun percent_exact() = assertEquals(100, MacroProgressBarDefaults.percent(180, 180))

    @Test
    fun percent_halfRoundsUp() {
        assertEquals(13, MacroProgressBarDefaults.percent(1, 8))
        assertEquals(1, MacroProgressBarDefaults.percent(1, 200))
        assertEquals(38, MacroProgressBarDefaults.percent(3, 8))
    }

    @Test
    fun percent_overTarget_realValue() = assertEquals(111, MacroProgressBarDefaults.percent(200, 180))

    @Test
    fun percent_negativeConsumed_isZero() = assertEquals(0, MacroProgressBarDefaults.percent(-5, 180))

    @Test
    fun percent_nonPositiveTarget_throws() {
        assertFailsWith<IllegalArgumentException> { MacroProgressBarDefaults.percent(10, 0) }
        assertFailsWith<IllegalArgumentException> { MacroProgressBarDefaults.fillFraction(10, -1) }
    }

    @Test
    fun fillFraction_cappedAtOne() = assertEquals(1f, MacroProgressBarDefaults.fillFraction(200, 180))

    @Test
    fun fillFraction_partial() = assertEquals(0.5f, MacroProgressBarDefaults.fillFraction(90, 180))

    @Test
    fun fillFraction_negative_isZero() = assertEquals(0f, MacroProgressBarDefaults.fillFraction(-10, 180))

    @Test
    fun text_rulesExample() {
        val text = MacroProgressBarDefaults.text(142, 180)
        assertEquals(MacroBarText("142 g", "180 g", "79 %"), text)
        assertEquals("142 g / 180 g · 79 %", text.full)
    }

    @Test
    fun text_groupsThousands() {
        assertEquals(MacroBarText("1.250 g", "1.500 g", "83 %"), MacroProgressBarDefaults.text(1250, 1500))
    }

    @Test
    fun barHeight8_shapeFull() {
        assertEquals(8.dp, MacroProgressBarDefaults.BarHeight)
        assertEquals(AppShapes.full, MacroProgressBarDefaults.Shape)
    }

    @Test
    fun trackColor_isOverlay() {
        assertEquals(KineticDarkColors.surfaceOverlay, MacroProgressBarDefaults.trackColor(KineticDarkColors))
    }

    @Test
    fun textStyles_haveTabularFigures() {
        val t = appTypography(FontFamily.Default)
        assertEquals(NUMERIC_FONT_FEATURES, MacroProgressBarDefaults.valueStyle(t).fontFeatureSettings)
        assertEquals(NUMERIC_FONT_FEATURES, MacroProgressBarDefaults.labelStyle(t).fontFeatureSettings)
    }

    @Test
    fun text_negativeConsumed_showsZero() {
        val text = MacroProgressBarDefaults.text(-5, 180)
        assertEquals("0 g / 180 g · 0 %", text.full)
    }

    @Test
    fun dotSize_onSubGrid() {
        assertEquals(Spacing.sm, MacroProgressBarDefaults.DotSize)
        assertEquals(0f, MacroProgressBarDefaults.DotSize.value % 4f)
    }
}
