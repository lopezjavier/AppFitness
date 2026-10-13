package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineBannerDefaultsTest {
    private val c = KineticDarkColors

    @Test
    fun colors_mapToRaisedBorderAmberAndPrimary() {
        val colors = OfflineBannerDefaults.colors(c)
        assertEquals(c.surfaceRaised, colors.background)
        assertEquals(c.borderSubtle, colors.border)
        assertEquals(c.energyAmber, colors.icon)
        assertEquals(c.textPrimary, colors.text)
    }

    @Test
    fun metrics_comeFromSpacingTokens() {
        assertEquals(Spacing.screenMargin, OfflineBannerDefaults.HorizontalPadding)
        assertEquals(Spacing.sm, OfflineBannerDefaults.VerticalPadding)
        assertEquals(Spacing.sm, OfflineBannerDefaults.IconTextGap)
        assertEquals(20.dp, OfflineBannerDefaults.IconSize)
    }

    @Test
    fun text_onRaised_isAa() {
        val ratio = contrastRatio(OfflineBannerDefaults.colors(c).text, c.surfaceRaised)
        assertTrue(ratio >= 4.5f, "text contrast $ratio < 4.5")
    }

    @Test
    fun icon_onRaised_meetsNonTextContrast() {
        val ratio = contrastRatio(OfflineBannerDefaults.colors(c).icon, c.surfaceRaised)
        assertTrue(ratio >= 3f, "icon contrast $ratio < 3")
    }
}
