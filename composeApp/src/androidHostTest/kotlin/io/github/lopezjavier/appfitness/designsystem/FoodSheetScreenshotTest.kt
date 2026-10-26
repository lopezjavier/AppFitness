package io.github.lopezjavier.appfitness.designsystem

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
 * D14 guard for the food sheet in its Add context (it holds text fields): nothing may be drawn away from the sheet's
 * own surface. The area above the sheet (right half, away from the background text) is only scrim over the window,
 * so it must be one uniform colour; a stray line from a decoration passed through the wrong modifier fails this.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class FoodSheetScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun addContext_leavesTheWindowTopUniform() {
        rule.setContent {
            AppTheme {
                Text("fondo", color = AppTheme.colors.textPrimary)
                FoodSheet(
                    name = "Avena",
                    brand = "Marca",
                    quantity = { AppTextField("100", {}, "Cantidad en gramos", Modifier) },
                    totals = FoodSheetTotals("165", "6", "28", "3,5"),
                    details = { Text("Por 100 g", color = AppTheme.colors.textPrimary) },
                    extra = null,
                    context = FoodSheetContext.Add(canAdd = true, saving = false, onAdd = {}, offlineHint = null),
                    onDismiss = {},
                )
            }
        }
        rule.waitForIdle()

        val file = File.createTempFile("food_sheet_probe", ".png")
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
