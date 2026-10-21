package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Pixel guard (T-070): nothing is painted in the transparent touch-target margin above the chips. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class PortionSelectorPixelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun marginAboveTheChips_hasNoStrayLine() {
        var background = androidx.compose.ui.graphics.Color.Unspecified
        rule.setContent {
            AppTheme {
                background = AppTheme.colors.surfaceBase
                Column(Modifier.background(AppTheme.colors.surfaceBase).testTag("root")) {
                    PortionSelector(
                        "100",
                        {},
                        "Cantidad (g)",
                        "Gramos",
                        listOf(PortionOption("a", "100 g")),
                        null,
                        {},
                    )
                }
            }
        }
        rule.waitForIdle()
        val map = rule.onNodeWithTag("root").captureToImage().toPixelMap()
        val chipTopPx = (
            rule.onNodeWithTag(
                "portion_portion_option_base",
            ).getUnclippedBoundsInRoot().top.value * 3
            ).toInt()
        // The visual chip starts 8 dp (24 px at xxhdpi) below its 48 dp touch target.
        for (y in chipTopPx until chipTopPx + 20) {
            for (x in 0 until map.width step 7) assertEquals("pixel ($x,$y)", background, map[x, y])
        }
    }
}
