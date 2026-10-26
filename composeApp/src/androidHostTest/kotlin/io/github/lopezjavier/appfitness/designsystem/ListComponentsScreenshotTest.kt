package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.centerY
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the T-120 list components and the two swipe layer states (360 x 640 dp). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class ListComponentsScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            AppTheme { Column(Modifier.testTag("shot").padding(Spacing.md)) { content() } }
        }
        rule.onNodeWithTag("shot").assertScreenshot(name)
    }

    @Test
    fun listRows() = shot("list_rows") {
        ListSectionHeader("Comidas", count = 3)
        AppListRow("Avena", supporting = "100 g", onClick = {})
        NavigationRow("Unidades", {}, value = "kg")
        AddItemButton("comida", {})
    }

    @Test
    fun metricCard() = shot("metric_card") {
        MetricCard("PESO ACTUAL", "74,8 kg", supporting = { Text("Desde tu primer peso: -2,6 kg") })
    }

    @Test
    fun timeRangeSelector() = shot("time_range_selector") {
        TimeRangeSelector(
            selected = TimeRange.Month,
            onSelect = {},
            ranges = listOf(TimeRange.Week, TimeRange.Month, TimeRange.ThreeMonths, TimeRange.Year, TimeRange.All),
            tagPrefix = "range",
        )
    }

    private fun swipeShot(name: String, fraction: Float) {
        rule.setContent {
            AppTheme {
                Column(Modifier.testTag("shot").padding(Spacing.md)) {
                    SwipeToDeleteRow("Eliminar Avena", true, {}, "bg", Modifier.testTag("row")) {
                        AppListRow("Avena", supporting = "100 g")
                    }
                }
            }
        }
        rule.onNodeWithTag("row").performTouchInput {
            down(Offset(right - 1f, centerY))
            moveTo(Offset(right * (1f - fraction), centerY))
        }
        rule.waitForIdle()
        rule.onNodeWithTag("shot").assertScreenshot(name)
    }

    @Test
    fun swipeMuted() = swipeShot("swipe_muted", 0.3f)

    @Test
    fun swipeArmed() = swipeShot("swipe_armed", 0.7f)
}
