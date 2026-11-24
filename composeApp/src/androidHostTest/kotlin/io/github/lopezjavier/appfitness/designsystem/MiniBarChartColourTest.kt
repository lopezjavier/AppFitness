package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** [MiniBarChart] draws every bar in one colour (a target never recolours) and dims the [unlit][MiniBarChart] ones. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class MiniBarChartColourTest {
    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun Chart(tag: String, values: List<Float>, target: Float?, unlit: Set<Int>) {
        Box(Modifier.width(200.dp).background(Color.Black).testTag(tag)) {
            MiniBarChart(values = values, modifier = Modifier.fillMaxWidth(), target = target, unlitIndices = unlit)
        }
    }

    private fun capture(tag: String): PixelMap = rule.onNodeWithTag(tag).captureToImage().toPixelMap()

    /** The bar's colour a few pixels above its bottom edge, at the centre of the [index]-th slot. */
    private fun PixelMap.bar(index: Int, count: Int): Color = this[width * (2 * index + 1) / (2 * count), height - 6]

    private fun assertSimilar(expected: Color, actual: Color) {
        val close = abs(expected.red - actual.red) < TOLERANCE &&
            abs(expected.green - actual.green) < TOLERANCE &&
            abs(expected.blue - actual.blue) < TOLERANCE
        assertTrue("$expected is not close to $actual", close)
    }

    @Test
    fun barsAboveTheTarget_haveTheSameColourAsTheOthers() {
        rule.setContent {
            AppTheme {
                Column {
                    Chart("over", listOf(3000f, 3000f), 2000f, emptySet())
                    Chart("under", listOf(1000f, 1000f), 2000f, emptySet())
                }
            }
        }
        rule.waitForIdle()
        val over = capture("over")
        val under = capture("under")

        assertSimilar(over.bar(0, 2), over.bar(1, 2))
        assertSimilar(under.bar(0, 2), under.bar(1, 2))
        // The gradient spans each bar's own height, so compare with a tolerance (an amber bar would be far off).
        assertSimilar(under.bar(0, 2), over.bar(0, 2))
    }

    @Test
    fun anUnlitBar_isDimmedComparedWithItsNeighbour() {
        rule.setContent { AppTheme { Chart("chart", listOf(1000f, 1000f), null, setOf(1)) } }
        rule.waitForIdle()
        val map = capture("chart")

        assertNotEquals(map.bar(0, 2), map.bar(1, 2))
    }
}

private const val TOLERANCE = 0.05f
