package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppChipTest {
    private val c = KineticDarkColors

    @Test
    fun height32_targetAtLeast48() {
        assertEquals(32.dp, AppChipDefaults.Height)
        assertTrue(AppChipDefaults.MinTouchTarget >= 48.dp)
    }

    @Test
    fun colors_selected_cyanTextAndBorderOnOverlay() {
        val colors = AppChipDefaults.colors(selected = true, appColors = c)
        assertEquals(c.surfaceOverlay, colors.container)
        assertEquals(c.athleticCyan, colors.content)
        assertEquals(c.athleticCyan, colors.border)
    }

    @Test
    fun colors_unselected_secondaryTextOnRaised() {
        val colors = AppChipDefaults.colors(selected = false, appColors = c)
        assertEquals(c.surfaceRaised, colors.container)
        assertEquals(c.textSecondary, colors.content)
        assertEquals(c.borderSubtle, colors.border)
    }

    @Test
    fun selected_differsInContainerAndContent_notOnlyBorder() {
        val on = AppChipDefaults.colors(selected = true, appColors = c)
        val off = AppChipDefaults.colors(selected = false, appColors = c)
        assertNotEquals(on.container, off.container)
        assertNotEquals(on.content, off.content)
    }

    @Test
    fun labelStyle_selectedIsBold_unselectedKeepsBase() {
        val base = appTypography(FontFamily.Default).bodyMedium
        assertEquals(FontWeight.W700, AppChipDefaults.labelStyle(base, selected = true).fontWeight)
        assertEquals(base, AppChipDefaults.labelStyle(base, selected = false))
        assertEquals(base.fontSize, AppChipDefaults.labelStyle(base, selected = true).fontSize)
    }

    @Test
    fun shapeAndBorder_areFullAndConstantWidth() {
        assertEquals(AppShapes.full, AppChipDefaults.Shape)
        assertEquals(AppBorder.thin, AppChipDefaults.BorderWidth)
        assertEquals(Spacing.md, AppChipDefaults.HorizontalPadding)
    }
}
