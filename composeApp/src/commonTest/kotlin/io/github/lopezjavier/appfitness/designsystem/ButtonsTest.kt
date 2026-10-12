package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ButtonsTest {
    private val c = KineticDarkColors

    private fun colors(variant: AppButtonVariant, enabled: Boolean = true) =
        AppButtonDefaults.colors(variant, enabled, c)

    @Test
    fun minHeight_primary52_secondaryAndGhost48() {
        assertEquals(52.dp, AppButtonDefaults.minHeight(AppButtonVariant.Primary))
        assertEquals(48.dp, AppButtonDefaults.minHeight(AppButtonVariant.Secondary))
        assertEquals(48.dp, AppButtonDefaults.minHeight(AppButtonVariant.Ghost))
    }

    @Test
    fun colors_enabled_matchTable() {
        colors(AppButtonVariant.Primary).let {
            assertEquals(c.neonLime, it.container)
            assertEquals(c.onNeonLime, it.content)
            assertNull(it.border)
        }
        colors(AppButtonVariant.Secondary).let {
            assertEquals(c.surfaceOverlay, it.container)
            assertEquals(c.textPrimary, it.content)
            assertEquals(c.borderStrong, it.border)
        }
        colors(AppButtonVariant.Ghost).let {
            assertEquals(Color.Transparent, it.container)
            assertEquals(c.athleticCyan, it.content)
            assertEquals(c.athleticCyan, it.border)
        }
    }

    @Test
    fun colors_disabled_useGhostDataContent_andDifferFromEnabled() {
        AppButtonVariant.entries.forEach { variant ->
            val disabled = colors(variant, enabled = false)
            assertEquals(c.ghostData, disabled.content, variant.name)
            assertNotEquals(colors(variant).content, disabled.content, variant.name)
        }
        assertEquals(c.surfaceOverlay, colors(AppButtonVariant.Primary, false).container)
        assertNull(colors(AppButtonVariant.Primary, false).border)
        assertEquals(c.borderSubtle, colors(AppButtonVariant.Secondary, false).border)
        assertEquals(Color.Transparent, colors(AppButtonVariant.Ghost, false).container)
        assertEquals(c.borderSubtle, colors(AppButtonVariant.Ghost, false).border)
    }

    @Test
    fun scale_pressed097_released1() {
        assertEquals(0.97f, AppButtonDefaults.scale(pressed = true))
        assertEquals(1f, AppButtonDefaults.scale(pressed = false))
    }

    @Test
    fun shapeAndPadding_matchTokens() {
        assertEquals(AppShapes.md, AppButtonDefaults.Shape)
        assertEquals(Spacing.xl, AppButtonDefaults.HorizontalPadding)
    }

    @Test
    fun ghostAndSecondary_neverUseOutlineOnlyAsCue() {
        listOf(AppButtonVariant.Secondary, AppButtonVariant.Ghost).forEach { variant ->
            val enabled = colors(variant)
            assertNotEquals(c.textSecondary, enabled.content, variant.name)
            assertNotEquals(c.ghostData, enabled.content, variant.name)
        }
        // The label colour differs from the disabled one in every variant: state is not border-only.
        AppButtonVariant.entries.forEach {
            assertNotEquals(colors(it).content, colors(it, enabled = false).content)
        }
    }
}
