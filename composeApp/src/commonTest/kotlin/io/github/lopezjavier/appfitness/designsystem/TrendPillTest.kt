package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TrendPillTest {
    private val c = KineticDarkColors

    @Test
    fun contentColor_favorable_isElectricGreen() =
        assertEquals(c.electricGreen, TrendPillDefaults.contentColor(TrendTone.Favorable, c))

    @Test
    fun contentColor_unfavorable_isErrorRed() =
        assertEquals(c.error, TrendPillDefaults.contentColor(TrendTone.Unfavorable, c))

    @Test
    fun contentColor_caution_isAmber() =
        assertEquals(c.energyAmber, TrendPillDefaults.contentColor(TrendTone.Caution, c))

    @Test
    fun contentColor_neutral_isTextSecondary() =
        assertEquals(c.textSecondary, TrendPillDefaults.contentColor(TrendTone.Neutral, c))

    @Test
    fun contentColor_allTonesDistinct() {
        val colors = TrendTone.entries.map { TrendPillDefaults.contentColor(it, c) }
        assertEquals(TrendTone.entries.size, colors.toSet().size)
    }

    @Test
    fun containerColor_default_isOverlay() = assertEquals(c.surfaceOverlay, TrendPillDefaults.containerColor(c))

    @Test
    fun containerColor_override_wins() =
        assertEquals(c.surfaceRaised, TrendPillDefaults.containerColor(c, c.surfaceRaised))

    @Test
    fun direction_positiveRate_up() = assertEquals(TrendDirection.Up, TrendPillDefaults.direction(0.30))

    @Test
    fun direction_negativeRate_down() = assertEquals(TrendDirection.Down, TrendPillDefaults.direction(-0.45))

    @Test
    fun direction_zero_flat() = assertEquals(TrendDirection.Flat, TrendPillDefaults.direction(0.0))

    @Test
    fun direction_null_none() = assertEquals(TrendDirection.None, TrendPillDefaults.direction(null))

    @Test
    fun toneIcon_favorable_caution_unfavorable_allDistinct() {
        val icons =
            listOf(TrendTone.Favorable, TrendTone.Caution, TrendTone.Unfavorable)
                .map { TrendPillDefaults.toneIcon(it) }
        icons.forEach { assertNotNull(it) }
        assertEquals(3, icons.toSet().size)
    }

    @Test
    fun toneIcon_neutral_isNull() = assertNull(TrendPillDefaults.toneIcon(TrendTone.Neutral))

    @Test
    fun directionIcon_none_isNull_othersDistinct() {
        assertNull(TrendPillDefaults.directionIcon(TrendDirection.None))
        val icons =
            listOf(TrendDirection.Up, TrendDirection.Down, TrendDirection.Flat)
                .map { TrendPillDefaults.directionIcon(it) }
        assertEquals(3, icons.toSet().size)
    }

    @Test
    fun containerColor_isOverlay() = assertEquals(c.surfaceOverlay, TrendPillDefaults.containerColor(c))

    @Test
    fun shapeAndSizes() {
        assertEquals(AppShapes.full, TrendPillDefaults.Shape)
        assertEquals(16.dp, TrendPillDefaults.IconSize)
        assertEquals(32.dp, TrendPillDefaults.MinHeight)
        assertEquals(Spacing.xs, TrendPillDefaults.ItemSpacing)
    }

    @Test
    fun textStyle_hasTabularFigures() {
        val t = appTypography(FontFamily.Default)
        assertEquals(NUMERIC_FONT_FEATURES, TrendPillDefaults.textStyle(t).fontFeatureSettings)
    }
}
