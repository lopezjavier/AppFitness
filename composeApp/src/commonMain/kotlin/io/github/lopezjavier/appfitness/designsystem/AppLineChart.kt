@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the chart components and their Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.tween
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
import com.patrykandpatrick.vico.compose.cartesian.CartesianChart
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

    /** Thicker line of a moving average drawn over [AppLineChart]'s `dots`. */
    val MaLineThickness: Dp = 3.dp

    /** Opacity of the daily dots: they sit behind the solid line. */
    const val DOT_ALPHA = 0.35f

    /** Diameter of a dot. */
    val DotSize: Dp = 6.dp

    /** Width the vertical axis labels take on the left of the plot; taps left of it map to the first point. */
    val PlotStartInset: Dp = 44.dp

    /** Duration of the data-change animation when a chart asks for it. */
    const val ANIMATION_MILLIS = 300

    /** Title-to-chart gap of [AppLineChartBlock]. */
    val TitleSpacing: Dp = Spacing.sm
}

/**
 * Line chart of one series (athleticCyan by default, no gradients, tabular numerals on the axes). [values] are plotted
 * left to right; [labels] name each value on the bottom axis (same size) and [valueLabel] formats the vertical axis.
 * [description] is the screen-reader summary (first, last, min, max and range), exposed as the state description of
 * the chart node.
 *
 * Optional trend layer: [values] may hold nulls (the line breaks there) and [dots] adds a second, low-opacity series
 * of points drawn behind the line (same indices as [values]); [lineThickness] overrides the line width. When
 * [onPointLongPress] / [onPointTap] are set, a long press / tap reports the nearest point index, and
 * [detailActionLabel] exposes the same as an accessibility action on the last point. [animated] animates data changes.
 */
@Composable
// The extra parameters are the optional trend layer; every default keeps the single-series chart as it was.
@Suppress("LongParameterList")
fun AppLineChart(
    values: List<Double?>,
    labels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    valueLabel: (Double) -> String = { it.toString() },
    color: Color = AppTheme.colors.athleticCyan,
    dots: List<Double?>? = null,
    lineThickness: Dp = AppLineChartDefaults.LineThickness,
    onPointLongPress: ((Int) -> Unit)? = null,
    onPointTap: ((Int) -> Unit)? = null,
    detailActionLabel: String? = null,
    animated: Boolean = false,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values, dots) {
        producer.runTransaction {
            lineModel {
                dots?.let { addSeries(it) }
                addSeries(values)
            }
        }
    }
    val pointCount = max(values.size, dots?.size ?: 0)
    val chart = rememberAppCartesianChart(color, lineThickness, dots != null, valueLabel, labels, pointCount)
    val animationSpec = if (animated) tween<Float>(AppLineChartDefaults.ANIMATION_MILLIS) else null
    Box(
        modifier
            .height(AppLineChartDefaults.Height)
            .semantics { stateDescription = description }
            .pointSelection(pointCount, onPointLongPress, onPointTap, detailActionLabel),
    ) {
        CartesianChartHost(
            chart = chart,
            modelProducer = producer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            animationSpec = animationSpec,
            initialAnimationSpec = null,
        )
    }
}

// The chart's look is set by these inputs only.
@Suppress("LongParameterList")
@Composable
private fun rememberAppCartesianChart(
    color: Color,
    lineThickness: Dp,
    withDots: Boolean,
    valueLabel: (Double) -> String,
    labels: List<String>,
    pointCount: Int,
): CartesianChart {
    val axisLabel =
        rememberAxisLabelComponent(AppTheme.typography.bodySmall.copy(color = AppTheme.colors.textSecondary))
    val gridColor = AppTheme.colors.divider
    val bottomSpacing = max(1, ceil(pointCount / AppLineChartDefaults.MAX_BOTTOM_LABELS.toDouble()).toInt())
    return rememberCartesianChart(
        rememberLineCartesianLayer(
            lineProvider =
            LineCartesianLayer.LineProvider.series(
                buildList {
                    if (withDots) add(rememberDotsLine(color))
                    add(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(color)),
                            stroke = LineCartesianLayer.LineStroke.Continuous(lineThickness),
                        ),
                    )
                },
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
