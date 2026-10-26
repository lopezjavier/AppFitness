package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the screen and editor templates on a 360x640 dp device (see [assertScreenshot]). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppScreenScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun screenWithFooter() {
        rule.setContent {
            AppTheme {
                AppScreen(
                    title = "Pantalla",
                    subtitle = "Subtítulo",
                    onBack = {},
                    footer = { PrimaryButton("Continuar", onClick = {}, modifier = Modifier.fillMaxWidth()) },
                ) { Text("Contenido", color = AppTheme.colors.textPrimary, modifier = Modifier.padding(Spacing.lg)) }
            }
        }
        rule.onRoot().assertScreenshot("screen_footer")
    }
}
