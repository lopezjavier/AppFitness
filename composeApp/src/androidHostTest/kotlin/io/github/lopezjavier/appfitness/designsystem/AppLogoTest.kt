package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real logo (white silhouette, transparent background) must parse and draw; a bad vector crashes here. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppLogoTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun logo_loadsAndDraws_withoutCrashing() {
        rule.setContent {
            AppTheme {
                // A dark plate behind the mark so the white silhouette is distinguishable from the capture.
                Box(
                    Modifier
                        .width(AppLogoDefaults.Width)
                        .height(AppLogoDefaults.height(AppLogoDefaults.Width))
                        .background(AppTheme.colors.surfaceBase)
                        .testTag("logo"),
                ) {
                    AppLogo(contentDescription = "Logo")
                }
            }
        }
        rule.waitForIdle()
        val image = rule.onNodeWithTag("logo").captureToImage()
        assertTrue("logo drawn at the expected size", image.width > 0 && image.height > 0)
        val map = image.toPixelMap()
        val pixels = (0 until image.width).flatMap { x -> (0 until image.height).map { y -> map[x, y] } }
        // The mark is painted white over the dark plate: both tones must be present.
        assertTrue("white silhouette painted", pixels.any { it.red > 0.9f && it.green > 0.9f && it.blue > 0.9f })
        assertTrue(
            "dark plate visible around the mark",
            pixels.any { it.red < 0.2f && it.green < 0.2f && it.blue < 0.3f },
        )
    }
}
