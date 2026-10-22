package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.centerRight
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppTrendLineChartTest {
    @get:Rule
    val rule = createComposeRule()

    private fun showTrend(
        onLongPress: ((Int) -> Unit)? = null,
        onTap: ((Int) -> Unit)? = null,
        ma: List<Double?> = listOf(null, 80.5, 80.4, 80.2, 80.0),
    ) {
        rule.setContent {
            AppTheme {
                AppLineChart(
                    values = ma,
                    labels = ma.indices.map { "d$it" },
                    description = "Tendencia",
                    modifier = Modifier.testTag("chart"),
                    dots = listOf(81.0, 80.2, 80.9, 80.0, 79.8),
                    lineThickness = AppLineChartDefaults.MaLineThickness,
                    onPointLongPress = onLongPress,
                    onPointTap = onTap,
                    detailActionLabel = "Ver detalle",
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun trendChart_withDotsAndGappedLine_exposesTheSummary() {
        showTrend()

        rule.onNodeWithTag("chart").assertStateDescription("Tendencia")
    }

    @Test
    fun trendChart_whenTheLineHasNoValues_stillDrawsTheDots() {
        showTrend(ma = List(5) { null })

        rule.onNodeWithTag("chart").assertStateDescription("Tendencia")
    }

    @Test
    fun longPress_atTheRightEdge_reportsTheLastPoint() {
        val reported = mutableListOf<Int>()
        showTrend(onLongPress = { reported += it })

        rule.onNodeWithTag("chart").performTouchInput { longClick(centerRight.copy(x = right - 1f)) }

        assertEquals(listOf(4), reported)
    }

    @Test
    fun tap_atTheRightEdge_reportsTheLastPoint() {
        val reported = mutableListOf<Int>()
        showTrend(onTap = { reported += it })

        rule.onNodeWithTag("chart").performTouchInput { click(centerRight.copy(x = right - 1f)) }

        assertEquals(listOf(4), reported)
    }

    @Test
    fun detailAction_isExposedToAccessibilityAndOpensTheLastPoint() {
        val reported = mutableListOf<Int>()
        showTrend(onLongPress = { reported += it })

        rule.onNodeWithTag("chart").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(4), reported)
    }

    @Test
    fun withoutCallbacks_theChartHasNoClickAction() {
        showTrend()

        rule.onNodeWithTag("chart").assert(
            SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick),
        )
    }
}
