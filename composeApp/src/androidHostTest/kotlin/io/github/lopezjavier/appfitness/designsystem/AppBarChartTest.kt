package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppBarChartTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(values: List<Int>, fontScale: Float = 1f) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme {
                    AppBarChart(
                        values = values,
                        labels = values.indices.map { "s$it" },
                        description = "84 series en el periodo",
                        modifier = Modifier.fillMaxWidth().testTag("bars"),
                    )
                }
            }
        }
    }

    @Test
    fun chart_exposesTheSummaryAsStateDescription() {
        show(listOf(12, 18, 0, 24, 30))

        val description = rule.onNodeWithTag("bars").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals("84 series en el periodo", description)
    }

    @Test
    fun allZeroValues_stillComposeWithTheFixedHeight() {
        show(listOf(0, 0, 0))

        rule.onNodeWithTag("bars").assertHeightIsAtLeast(AppBarChartDefaults.Height)
    }

    @Test
    fun largeFontScale_keepsTheChartComposed() {
        show(List(26) { it }, fontScale = 2f)

        rule.onNodeWithTag("bars").assertHeightIsAtLeast(AppBarChartDefaults.Height)
    }

    @Test
    fun emptySeries_composesWithoutBars() {
        show(emptyList())

        rule.onNodeWithTag("bars").assertHeightIsAtLeast(AppBarChartDefaults.Height)
    }
}
