package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the entries flagged `tall` (a 2200 dp window, so a long section is not cut); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h2200dp-xxhdpi")
class GalleryTall {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersEveryTallEntry() = rule.renderGallery(GalleryRegistry.all().filter { it.tall })
}
