package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class UniversalFabTest {
    @Test
    fun height_is52() {
        assertEquals(52.dp, UniversalFabDefaults.Height)
    }

    @Test
    fun scale_pressed097_released1() {
        assertEquals(0.97f, UniversalFabDefaults.scale(pressed = true))
        assertEquals(1f, UniversalFabDefaults.scale(pressed = false))
    }

    @Test
    fun colors_areNeonLimeOnNeonLime() {
        val colors = UniversalFabDefaults.colors(KineticDarkColors)
        assertEquals(KineticDarkColors.neonLime, colors.container)
        assertEquals(KineticDarkColors.onNeonLime, colors.content)
    }

    @Test
    fun contentClearance_isHeightPlusTwoMargins() {
        val expected = UniversalFabDefaults.Height + UniversalFabDefaults.EdgeMargin * 2
        assertEquals(expected, UniversalFabDefaults.ContentClearance)
        assertEquals(84.dp, UniversalFabDefaults.ContentClearance)
    }
}
