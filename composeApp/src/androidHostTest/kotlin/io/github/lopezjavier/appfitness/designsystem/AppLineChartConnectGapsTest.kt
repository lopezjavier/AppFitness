package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-49: the line joins sparse readings across the null gaps. Geometry is checked by eye at the checkpoint. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppLineChartConnectGapsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun showGapped(values: List<Double?>, dots: List<Double?>) {
        rule.setContent {
            AppTheme {
                AppLineChart(
                    values = values,
                    labels = values.indices.map { "d$it" },
                    description = "Tendencia",
                    modifier = Modifier.testTag("chart"),
                    dots = dots,
                    connectGaps = true,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun withNullsBetweenPoints_rendersAndKeepsTheSummary() {
        showGapped(
            values = listOf(79.5, null, null, null, null, 79.4),
            dots = listOf(79.5, null, null, null, null, 79.4),
        )

        rule.onNodeWithTag("chart").assertStateDescription("Tendencia")
    }

    @Test
    fun withASingleNonNullPoint_doesNotCrash() {
        showGapped(values = listOf(null, null, 79.5, null), dots = listOf(null, null, 79.5, null))

        rule.onNodeWithTag("chart").assertStateDescription("Tendencia")
    }

    @Test
    fun withTwoPointsAYearApart_doesNotCrash() {
        val values = List(366) { if (it == 0 || it == 365) 80.0 - it / 365.0 else null }
        showGapped(values = values, dots = values)

        rule.onNodeWithTag("chart").assertStateDescription("Tendencia")
    }

    @Test
    fun withoutAnyLineValue_stillDrawsTheDots() {
        showGapped(values = List(4) { null }, dots = listOf(79.0, null, 79.2, null))

        rule.onNodeWithTag("chart").assertStateDescription("Tendencia")
    }
}
