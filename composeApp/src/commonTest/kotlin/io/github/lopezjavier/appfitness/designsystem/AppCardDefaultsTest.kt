package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class AppCardDefaultsTest {
    @Test
    fun colors_areRaisedSurfaceWithSubtleBorder() {
        val colors = AppCardDefaults.colors(KineticDarkColors)
        assertColor(0x121820, colors.container, name = "container")
        assertColor(0xFFFFFF, colors.border, alpha = 0.08f, name = "border")
        assertEquals(KineticDarkColors.surfaceRaised, colors.container)
        assertEquals(KineticDarkColors.borderSubtle, colors.border)
    }

    @Test
    fun shapeAndSizes_matchTheTokens() {
        assertEquals(AppShapes.lg, AppCardDefaults.Shape)
        assertEquals(16.dp, AppRadius.lg)
        assertEquals(16.dp, AppCardDefaults.Padding)
        assertEquals(1.dp, AppCardDefaults.BorderWidth)
    }
}
