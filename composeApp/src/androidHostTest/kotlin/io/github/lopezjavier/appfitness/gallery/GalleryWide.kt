package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the entries flagged `wide` (840 dp window, the layout from 600 dp up); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w840dp-h640dp-xxhdpi")
class GalleryWide {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersEveryWideEntry() = rule.renderGallery(GalleryRegistry.all().filter { it.wide })
}
