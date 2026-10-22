package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Asserts the node's state description, which charts use as their screen-reader summary. */
fun SemanticsNodeInteraction.assertStateDescription(expected: String): SemanticsNodeInteraction =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected))

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppLineChartTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(values: List<Double>, description: String) {
        rule.setContent {
            AppTheme {
                AppLineChart(
                    values = values,
                    labels = values.indices.map { "d$it" },
                    description = description,
                    modifier = Modifier.testTag("chart"),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun chart_exposesTheSummaryAsStateDescription() {
        show(listOf(100.0, 105.0, 110.0), "De 100 a 110 kg, 3 sesiones")

        rule.onNodeWithTag("chart").assertStateDescription("De 100 a 110 kg, 3 sesiones")
    }

    @Test
    fun chart_usesTheDesignSystemHeight() {
        show(listOf(100.0, 105.0), "Resumen")

        rule.onNodeWithTag("chart").assertHeightIsEqualTo(AppLineChartDefaults.Height)
    }

    @Test
    fun chart_withAReferenceLineOutsideTheDataStillRendersWithItsSummary() {
        rule.setContent {
            AppTheme {
                AppLineChart(
                    values = listOf(100.0, 105.0, 110.0),
                    labels = listOf("a", "b", "c"),
                    description = "Resumen con objetivo",
                    modifier = Modifier.testTag("chart"),
                    referenceLine = 90.0,
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("chart").assertHeightIsEqualTo(AppLineChartDefaults.Height)
            .assertStateDescription("Resumen con objetivo")
    }

    @Test
    fun skeleton_hasTheSameHeightAsTheChart() {
        rule.setContent { AppTheme { AppLineChartSkeleton(Modifier.testTag("skeleton")) } }
        rule.waitForIdle()

        rule.onNodeWithTag("skeleton").assertHeightIsEqualTo(AppLineChartDefaults.Height)
    }
}
