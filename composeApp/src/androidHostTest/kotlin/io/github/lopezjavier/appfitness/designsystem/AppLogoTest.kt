package io.github.lopezjavier.appfitness.designsystem

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

/** The real logo (gradients, strokes, nested groups) must parse and draw; a bad vector would crash here. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppLogoTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun logo_loadsAndDraws_withoutCrashing() {
        rule.setContent { AppTheme { AppLogo(Modifier.testTag("logo"), contentDescription = "Logo") } }
        rule.waitForIdle()
        val image = rule.onNodeWithTag("logo").captureToImage()
        assertTrue("logo drawn at the expected size", image.width > 0 && image.height > 0)
        // The gradient badges must actually be painted: cyan (blue-dominant) and lime/amber (red+green) pixels exist.
        val map = image.toPixelMap()
        val pixels = (0 until image.width).flatMap { x -> (0 until image.height).map { y -> map[x, y] } }
        assertTrue("cyan gradient painted", pixels.any { it.alpha > 0.9f && it.blue > it.red + 0.3f })
        assertTrue(
            "lime or amber gradient painted",
            pixels.any {
                it.alpha > 0.9f && it.red > it.blue + 0.4f &&
                    it.green > 0.4f
            },
        )
    }
}
