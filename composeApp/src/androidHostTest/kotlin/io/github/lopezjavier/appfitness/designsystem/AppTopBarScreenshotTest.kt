package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_star
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the top bar v2 on a 360x640 dp device (see [assertScreenshot] for record/verify). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppTopBarScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun actions(count: Int) = listOf(
        AppTopBarAction(Res.drawable.ic_edit, "Editar", {}),
        AppTopBarAction(Res.drawable.ic_star, "Favorito", {}),
        AppTopBarAction(Res.drawable.ic_delete, "Eliminar", {}),
    ).take(count)

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { AppTheme { Column(Modifier.testTag("shot")) { content() } } }
        rule.onNodeWithTag("shot").assertScreenshot(name)
    }

    @Test
    fun noActions() = shot("topbar_0_actions") { AppTopBar("Perfil", windowInsets = WindowInsets(0)) }

    @Test
    fun twoActions() =
        shot("topbar_2_actions") { AppTopBar("Perfil", actions = actions(2), windowInsets = WindowInsets(0)) }

    @Test
    fun threeActionsWithOverflow() =
        shot("topbar_3_actions") { AppTopBar("Perfil", actions = actions(3), windowInsets = WindowInsets(0)) }

    @Test
    fun withSubtitleAndBack() = shot("topbar_subtitle") {
        AppTopBar(
            "Rutina",
            subtitle = "Día de empuje",
            navigationButton = AppTopBarButton("Volver") {},
            actions = actions(2),
            windowInsets = WindowInsets(0),
        )
    }
}
