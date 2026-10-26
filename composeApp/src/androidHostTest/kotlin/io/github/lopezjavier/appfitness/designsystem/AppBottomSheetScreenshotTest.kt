package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.imageio.ImageIO

/**
 * E9 items 27 and 38, SHT-3: a sheet with a text field must show no line or box away from its own surface.
 * The old decoration (shadow and full border) was passed through the sheet's `modifier`, which sits outside
 * the sheet's offset, so it was drawn at the top of the window instead of on the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalRoborazziApi::class)
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

    /**
     * Pixel probe, independent of the golden's 1 % tolerance: the area above the sheet (right half, away from the
     * background text) is only scrim over the window, so it must be one uniform colour. The old decoration drew a
     * hairline across the window top and failed this.
     */
    @Test
    fun sheetWithTextField_leavesTheWindowTopUniform() {
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

        val file = File.createTempFile("sheet_probe", ".png")
        try {
            captureScreenRoboImage(
                filePath = file.path,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            )
            val image = ImageIO.read(file)
            val reference = image.getRGB(image.width - 1, 0)
            val stray = (0 until PROBE_HEIGHT_PX).flatMap { y ->
                (image.width / 2 until image.width).filter { x -> image.getRGB(x, y) != reference }.map { x -> x to y }
            }
            assertTrue("Stray pixels above the sheet: ${stray.take(5)} (${stray.size})", stray.isEmpty())
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val PROBE_HEIGHT_PX = 300
    }
}
