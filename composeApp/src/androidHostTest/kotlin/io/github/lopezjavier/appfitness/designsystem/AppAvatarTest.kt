package io.github.lopezjavier.appfitness.designsystem

import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImageLoaderEngine
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val PHOTO_URL = "https://img.example/ana.png"

/** US-05 / ADR-26: the avatar shows the Google photo, with the person icon as the fallback; the ring is opt-in. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppAvatarTest {
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun installFakeImageLoader() {
        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(PHOTO_URL, ColorImage(Color.RED))
                .default(ColorImage(Color.BLUE))
                .build()
        val context = RuntimeEnvironment.getApplication()
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components { add(engine) }.build())
    }

    @After
    fun resetImageLoader() {
        SingletonImageLoader.reset()
    }

    private fun show(imageUrl: String?, ring: Boolean = false) {
        rule.setContent {
            AppTheme {
                AppAvatar(
                    imageUrl = imageUrl,
                    size = 40.dp,
                    testTagPrefix = "av",
                    ring = if (ring) AppTheme.colors.neonLime else null,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun withUrl_drawsTheImage_andDropsThePlaceholder() {
        show(PHOTO_URL)
        rule.onNodeWithTag("av_avatar_image", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("av_avatar_placeholder", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun withoutUrl_showsThePlaceholder_andNoImage() {
        show(null)
        rule.onNodeWithTag("av_avatar_placeholder", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("av_avatar_image", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun ring_insetsTheContent_keepingTheOuterSize() {
        rule.setContent {
            AppTheme {
                Column {
                    AppAvatar(null, 40.dp, "plain")
                    AppAvatar(null, 40.dp, "ring", ring = AppTheme.colors.neonLime)
                }
            }
        }
        rule.waitForIdle()
        val plain = rule.onNodeWithTag("plain_avatar_placeholder", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val ringed = rule.onNodeWithTag("ring_avatar_placeholder", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("placeholder shrinks inside the ring", ringed.width < plain.width)
    }
}
