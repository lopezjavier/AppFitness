@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the chart components and their Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisTickComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import kotlin.math.ceil
import kotlin.math.max

object AppLineChartDefaults {
    /** Height of the plot with its axes. */
    val Height: Dp = 200.dp

    /** Target number of labels on the bottom axis; the item spacing is derived from it. */
    const val MAX_BOTTOM_LABELS = 4

    /** Line thickness. */
    val LineThickness: Dp = 2.dp

    /** Title-to-chart gap of [AppLineChartBlock]. */
    val TitleSpacing: Dp = Spacing.sm
}

/**
 * Line chart of one series (athleticCyan by default, no gradients, tabular numerals on the axes). [values] are plotted
 * left to right; [labels] name each value on the bottom axis (same size) and [valueLabel] formats the vertical axis.
 * [description] is the screen-reader summary (first, last, min, max and range), exposed as the state description of
 * the chart node.
 */
@Composable
fun AppLineChart(
    values: List<Double>,
    labels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    valueLabel: (Double) -> String = { it.toString() },
    color: Color = AppTheme.colors.athleticCyan,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        producer.runTransaction { lineModel { series(values) } }
    }
    val axisLabel =
        rememberAxisLabelComponent(AppTheme.typography.bodySmall.copy(color = AppTheme.colors.textSecondary))
    val gridColor = AppTheme.colors.divider
    val bottomSpacing = max(1, ceil(values.size / AppLineChartDefaults.MAX_BOTTOM_LABELS.toDouble()).toInt())
    val chart =
        rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider =
                LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(color)),
                        stroke = LineCartesianLayer.LineStroke.Continuous(AppLineChartDefaults.LineThickness),
                    ),
                ),
            ),
            startAxis =
            VerticalAxis.rememberStart(
                line = rememberAxisLineComponent(Fill(gridColor)),
                label = axisLabel,
                tick = rememberAxisTickComponent(Fill(gridColor)),
                guideline = rememberAxisGuidelineComponent(Fill(gridColor)),
                valueFormatter = remember(valueLabel) {
                    CartesianValueFormatter { _, value, _ -> valueLabel(value) }
                },
            ),
            bottomAxis =
            HorizontalAxis.rememberBottom(
                line = rememberAxisLineComponent(Fill(gridColor)),
                label = axisLabel,
                tick = rememberAxisTickComponent(Fill(gridColor)),
                guideline = null,
                valueFormatter = remember(labels) {
                    CartesianValueFormatter { _, value, _ -> labels.getOrElse(value.toInt()) { "" } }
                },
                itemPlacer = remember(bottomSpacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { bottomSpacing }) },
            ),
        )
    Box(modifier.height(AppLineChartDefaults.Height).semantics { stateDescription = description }) {
        CartesianChartHost(
            chart = chart,
            modelProducer = producer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            animationSpec = null,
            initialAnimationSpec = null,
        )
    }
}

/** Placeholder of a chart while its data loads; same height as [AppLineChart]. */
@Composable
fun AppLineChartSkeleton(modifier: Modifier = Modifier) {
    SkeletonBox(modifier.fillMaxWidth().height(AppLineChartDefaults.Height), AppShapes.md)
}

/** A titled chart for a card: [title] above, [chart] below with the standard gap. */
@Composable
fun AppLineChartBlock(title: String, modifier: Modifier = Modifier, chart: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppLineChartDefaults.TitleSpacing)) {
        Text(
            text = title,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        chart()
    }
}

@Preview
@Composable
private fun AppLineChartPreview() {
    AppTheme {
        AppLineChart(
            values = listOf(100.0, 102.5, 101.0, 105.0, 107.5),
            labels = listOf("1 sep", "8 sep", "15 sep", "22 sep", "29 sep"),
            description = "Evolución",
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
