package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ButtonsTest {
    private val c = KineticDarkColors

    private fun colors(variant: AppButtonVariant, enabled: Boolean = true) =
        AppButtonDefaults.colors(variant, enabled, c)

    @Test
    fun minHeight_primaryAndSecondary56_ghost48() {
        assertEquals(56.dp, AppButtonDefaults.minHeight(AppButtonVariant.Primary))
        assertEquals(56.dp, AppButtonDefaults.minHeight(AppButtonVariant.Secondary))
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
            assertEquals(c.surfaceControl, it.container)
            assertEquals(c.textPrimary, it.content)
            assertNull(it.border)
        }
        colors(AppButtonVariant.Ghost).let {
            assertEquals(Color.Transparent, it.container)
            assertEquals(c.textPrimary, it.content)
            assertEquals(c.borderControl, it.border)
        }
    }

    @Test
    fun colors_disabled_useGhostDataContent_andDifferFromEnabled() {
        AppButtonVariant.entries.forEach { variant ->
            val disabled = colors(variant, enabled = false)
            if (variant != AppButtonVariant.Primary) assertEquals(c.ghostData, disabled.content, variant.name)
            assertNotEquals(colors(variant).content, disabled.content, variant.name)
        }
        assertEquals(
            c.neonLime.copy(alpha = 0.2f).compositeOver(c.surfaceBase),
            colors(AppButtonVariant.Primary, false).container,
        )
        val disabledContainer = colors(AppButtonVariant.Primary, false).container
        assertEquals(
            c.neonLime.copy(alpha = 0.5f).compositeOver(disabledContainer),
            colors(AppButtonVariant.Primary, false).content,
        )
        assertTrue(contrastRatio(colors(AppButtonVariant.Primary, false).content, disabledContainer) >= 3f)
        assertNull(colors(AppButtonVariant.Primary, false).border)
        assertEquals(c.surfaceControl, colors(AppButtonVariant.Secondary, false).container)
        assertNull(colors(AppButtonVariant.Secondary, false).border)
        assertEquals(Color.Transparent, colors(AppButtonVariant.Ghost, false).container)
        assertEquals(c.borderSubtle, colors(AppButtonVariant.Ghost, false).border)
    }

    @Test
    fun ghostBorder_whenFocused_is2dpLime_otherwiseThinNeutral() {
        val ghost = colors(AppButtonVariant.Ghost)
        val focused = AppButtonDefaults.borderStroke(AppButtonVariant.Ghost, true, true, ghost, c)
        assertEquals(AppBorder.focus, focused?.width)
        assertEquals(SolidColor(c.neonLime), focused?.brush)
        val resting = AppButtonDefaults.borderStroke(AppButtonVariant.Ghost, true, false, ghost, c)
        assertEquals(AppBorder.thin, resting?.width)
        assertEquals(SolidColor(c.borderControl), resting?.brush)
        val primary = colors(AppButtonVariant.Primary)
        assertNull(AppButtonDefaults.borderStroke(AppButtonVariant.Primary, true, true, primary, c))
    }

    @Test
    fun scale_pressed097_released1() {
        assertEquals(0.97f, AppButtonDefaults.scale(pressed = true))
        assertEquals(1f, AppButtonDefaults.scale(pressed = false))
    }

    @Test
    fun shapeAndPadding_matchTokens() {
        assertEquals(AppShapes.lg, AppButtonDefaults.Shape)
        assertEquals(Spacing.xl, AppButtonDefaults.HorizontalPadding)
    }

    @Test
    fun secondary_isFilledAndNeverOutlined_sameShapeAsPrimary() {
        assertNull(colors(AppButtonVariant.Secondary).border)
        assertNotEquals(Color.Transparent, colors(AppButtonVariant.Secondary).container)
        assertEquals(AppButtonDefaults.PrimaryMinHeight, AppButtonDefaults.minHeight(AppButtonVariant.Secondary))
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

    @Test
    fun ghostTones_mapToTheirColours() {
        fun ghost(tone: GhostTone) = AppButtonDefaults.colors(AppButtonVariant.Ghost, true, c, tone)
        assertEquals(c.textPrimary, ghost(GhostTone.Action).content)
        assertEquals(c.borderControl, ghost(GhostTone.Action).border)
        assertEquals(c.error, ghost(GhostTone.Destructive).content)
        assertEquals(c.error, ghost(GhostTone.Destructive).border)
    }

    @Test
    fun ghostTones_disabledIgnoresTheTone() {
        GhostTone.entries.forEach {
            assertEquals(c.ghostData, AppButtonDefaults.colors(AppButtonVariant.Ghost, false, c, it).content)
        }
    }
}
