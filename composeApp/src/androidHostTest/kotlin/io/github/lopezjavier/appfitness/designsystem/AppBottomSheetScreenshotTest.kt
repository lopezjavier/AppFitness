package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * E9 items 27 and 38, SHT-3: a sheet with a text field must show no line or box away from its own surface.
 * The old decoration (shadow and full border) was passed through the sheet's `modifier`, which sits outside
 * the sheet's offset, so it was drawn at the top of the window instead of on the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppBottomSheetScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun sheetWithTextField_hasNoLineCrossingTheSheet() {
        rule.setContent {
            AppTheme {
                Text("fondo", color = AppTheme.colors.textPrimary)
                AppBottomSheet(onDismissRequest = {}, testTagPrefix = "sheet") {
                    Text(
                        "Nombre de la comida",
                        modifier = Modifier.padding(Spacing.lg),
                        color = AppTheme.colors.textPrimary,
                    )
                    AppTextField("abc", {}, "Nombre", Modifier.padding(Spacing.lg))
                }
            }
        }
        rule.waitForIdle()

        assertScreenScreenshot("sheet_with_text_field")
    }
}
