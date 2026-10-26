package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AppBottomSheetDefaultsTest {
    private val colors = AppBottomSheetDefaults.colors(KineticDarkColors)

    @Test
    fun handle_is36By4() {
        assertEquals(36.dp, AppBottomSheetDefaults.HandleWidth)
        assertEquals(4.dp, AppBottomSheetDefaults.HandleHeight)
    }

    @Test
    fun shape_isSheetTopWithRadius24() {
        assertEquals(AppShapes.sheetTop, AppBottomSheetDefaults.Shape)
        val shape = AppBottomSheetDefaults.Shape as RoundedCornerShape
        assertEquals(CornerSize(AppRadius.xl), shape.topStart)
        assertEquals(CornerSize(AppRadius.xl), shape.topEnd)
        assertEquals(24.dp, AppRadius.xl)
    }

    @Test
    fun scrim_is65PercentBlack_notTheM3Default() {
        assertEquals(KineticDarkColors.scrim, colors.scrim)
        assertNotEquals(Color.Black.copy(alpha = 0.32f), colors.scrim)
    }

    @Test
    fun colors_mapTokens() {
        assertEquals(KineticDarkColors.surfaceOverlay, colors.container)
        assertEquals(KineticDarkColors.textPrimary, colors.content)
        assertEquals(KineticDarkColors.sheetHandle, colors.handle)
        assertEquals(KineticDarkColors.borderStrong, colors.border)
    }

    @Test
    fun hairline_isClippedAtTheTopCornerRadius() {
        val shape = AppBottomSheetDefaults.Shape as RoundedCornerShape
        assertEquals(shape.topStart, CornerSize(AppBottomSheetDefaults.HairlineClipHeight))
        assertEquals(AppRadius.xl, AppBottomSheetDefaults.HairlineClipHeight)
    }
}
