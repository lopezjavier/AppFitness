package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of `AppEditorScreen` (clean, dirty, offline) on a 360x640 dp device (see [assertScreenshot]). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppEditorScreenScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun shot(name: String, isDirty: Boolean, isOnline: Boolean) {
        rule.setContent {
            AppTheme {
                AppEditorScreen(
                    title = "Editar alimento",
                    isDirty = isDirty,
                    isSaving = false,
                    isOnline = isOnline,
                    onSave = {},
                    onClose = {},
                    destructiveAction = EditorDestructiveAction("Eliminar") {},
                ) { Body() }
            }
        }
        rule.onRoot().assertScreenshot(name)
    }

    @Composable
    private fun Body() {
        Text("Nombre del alimento", color = AppTheme.colors.textPrimary, modifier = Modifier.padding(Spacing.lg))
    }

    @Test
    fun clean() = shot("editor_clean", isDirty = false, isOnline = true)

    @Test
    fun dirty() = shot("editor_dirty", isDirty = true, isOnline = true)

    @Test
    fun offline() = shot("editor_offline", isDirty = true, isOnline = false)
}
