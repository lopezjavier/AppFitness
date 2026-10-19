package io.github.lopezjavier.appfitness.designsystem

import android.graphics.Color
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImageLoaderEngine
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val PHOTO_URL = "https://img.example/ana.png"

/** US-05: the Top App Bar avatar shows the Google photo, with the person icon as the fallback. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppTopBarAvatarTest {
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

    private fun show(avatarImageUrl: String?) {
        rule.setContent {
            AppTheme {
                AppTopBar(
                    title = "Diario",
                    avatarButton = AppTopBarButton("Abrir perfil") {},
                    avatarImageUrl = avatarImageUrl,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun withUrl_drawsTheImageInsideTheAvatar_andDropsThePlaceholder() {
        show(PHOTO_URL)
        rule.onNodeWithTag("topbar_avatar_image", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("topbar_avatar_placeholder", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun withoutUrl_showsThePlaceholder_andNoImage() {
        show(null)
        rule.onNodeWithTag("topbar_avatar_placeholder", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("topbar_avatar_image", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun avatarButton_keepsTargetAndLabel() {
        show(PHOTO_URL)
        rule.onNodeWithTag("topbar_avatar")
            .assertContentDescriptionEquals("Abrir perfil")
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
    }
}
