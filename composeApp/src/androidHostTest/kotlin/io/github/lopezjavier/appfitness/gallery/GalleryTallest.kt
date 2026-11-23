package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the entries flagged `tallest` (a 4400 dp window, a long screen is not cut); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "es-rES-w360dp-h4400dp-xxhdpi")
class GalleryTallest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersEveryTallestEntry() = rule.renderGallery(GalleryRegistry.all().filter { it.tallest })
}
