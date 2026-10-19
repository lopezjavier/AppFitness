package io.github.lopezjavier.appfitness.designsystem

import android.graphics.Color
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImageLoaderEngine
import io.github.lopezjavier.appfitness.feature.catalog.ComponentCatalogScreen
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val IMAGE = "https://img.example/pose.webp"

/** [ExerciseThumbnail] and [ExerciseAnimation]: states, tags, semantics and reduced motion. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ExerciseMediaTest {
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun installFakeImageLoader() {
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(Color.BLUE)).build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(RuntimeEnvironment.getApplication()).components { add(engine) }.build(),
        )
    }

    @After
    fun resetImageLoader() {
        SingletonImageLoader.reset()
    }

    @Test
    fun thumbnail_withoutUri_showsThePlaceholder() {
        rule.setContent { AppTheme { ExerciseThumbnail(null, 56.dp, "t") } }

        rule.onNodeWithTag("t_thumbnail_placeholder", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("t_thumbnail_image", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun thumbnail_withUri_showsTheImage_andDropsThePlaceholder() {
        rule.setContent { AppTheme { ExerciseThumbnail(IMAGE, 56.dp, "t") } }
        rule.waitForIdle()

        rule.onNodeWithTag("t_thumbnail_image", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("t_thumbnail_placeholder", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun animation_hasItsDescription_andBothPoses() {
        rule.setContent { AppTheme { ExerciseAnimation(IMAGE, IMAGE, true, "a", "Demostración de X") } }
        rule.waitForIdle()

        rule.onNodeWithTag("a_animation").assertContentDescriptionEquals("Demostración de X")
        rule.onNodeWithTag("a_start", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("a_peak", useUnmergedTree = true).assertExists()
    }

    @Test
    fun animation_withAnimationsDisabled_drawsOnlyTheStartPose() {
        rule.setContent { AppTheme { ExerciseAnimation(IMAGE, IMAGE, false, "a", "d") } }
        rule.waitForIdle()

        rule.onNodeWithTag("a_start", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("a_peak", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun animation_withoutAPeakUri_drawsOnlyTheStartPose() {
        rule.setContent { AppTheme { ExerciseAnimation(IMAGE, null, true, "a", "d") } }
        rule.waitForIdle()

        rule.onNodeWithTag("a_peak", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun catalog_rendersTheExerciseMediaSection() {
        rule.setContent { AppTheme { ComponentCatalogScreen() } }
        rule.onNodeWithTag("catalog_screen")
            .performScrollToNode(hasTestTag("catalog_section_exercise_media"))
        rule.waitForIdle()

        rule.onNodeWithTag("catalog_section_exercise_media").assertExists()
    }
}
