package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricTileTest {
    private val c = KineticDarkColors

    @Test
    fun colors_overlayContainer_strongBorder_textTokens() {
        val colors = MetricTileDefaults.colors(c)
        assertEquals(c.surfaceOverlay, colors.container)
        assertEquals(c.borderStrong, colors.border)
        assertEquals(c.textSecondary, colors.label)
        assertEquals(c.textPrimary, colors.value)
        assertEquals(c.textSecondary, colors.unit)
    }

    @Test
    fun shapeMd_padding12() {
        assertEquals(AppShapes.md, MetricTileDefaults.Shape)
        assertEquals(Spacing.md, MetricTileDefaults.Padding)
        assertEquals(AppBorder.thin, MetricTileDefaults.BorderWidth)
    }

    @Test
    fun textStyles_haveTabularFigures() {
        val t = appTypography(FontFamily.Default)
        assertEquals(t.headlineMetric, MetricTileDefaults.valueStyle(t))
        assertEquals(NUMERIC_FONT_FEATURES, MetricTileDefaults.valueStyle(t).fontFeatureSettings)
        assertEquals(NUMERIC_FONT_FEATURES, MetricTileDefaults.labelStyle(t).fontFeatureSettings)
        assertEquals(NUMERIC_FONT_FEATURES, MetricTileDefaults.unitStyle(t).fontFeatureSettings)
    }
}
