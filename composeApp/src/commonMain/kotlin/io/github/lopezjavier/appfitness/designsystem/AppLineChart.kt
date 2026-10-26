@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the chart components and their Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalBox
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.DashedShape
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
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

    /** Diameter of a record marker (also the legend sample). */
    val MarkerSize: Dp = 10.dp

    /** Ring thickness of a record marker. */
    val MarkerRing: Dp = 2.dp

    /** Width the vertical axis labels take on the left of the plot; taps left of it map to the first point. */
    val PlotStartInset: Dp = 44.dp

    /** Duration of the data-change animation when a chart asks for it. */
    const val ANIMATION_MILLIS = 300

    /** Opacity of the shaded band of a goal range. */
    const val BAND_ALPHA = 0.15f

    /** Thickness of the dashed reference line. */
    val ReferenceLineThickness: Dp = 2.dp

    /** Dash and gap length of the reference line. */
    val ReferenceDash: Dp = 6.dp

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
 * [referenceLine] draws a dashed horizontal line at that value (textSecondary, dashed so it is not colour-only) and
 * widens the vertical range to include it; the caller mentions it in [description]. With [connectGaps] the line joins
 * consecutive non-null values across the null gaps (sparse readings); indices, dots and selection are unchanged.
 * [markers] are indices into [values] drawn on top of the line as record markers (decoration only; indices without a
 * value are ignored); empty by default, which leaves the chart as it was.
 * [band] shades a horizontal value range (low-opacity athleticCyan, behind the line). It never widens the vertical
 * range (only the data and [referenceLine] do): the band is clipped to the visible range and skipped when it lies
 * outside; the caller mentions it in [description]. Null by default, which leaves the chart as it was.
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
    referenceLine: Double? = null,
    connectGaps: Boolean = false,
    markers: List<Int> = emptyList(),
    band: ClosedFloatingPointRange<Double>? = null,
) {
    val producer = remember { CartesianChartModelProducer() }
    val markerPoints = markers.distinct().sorted().filter { values.getOrNull(it) != null }
    LaunchedEffect(values, dots, connectGaps, markerPoints) {
        producer.runTransaction {
            lineModel {
                dots?.let { series ->
                    val present = if (connectGaps) series.present() else emptyList()
                    if (present.isEmpty()) {
                        addSeries(series)
                    } else {
                        series(present.map { it.first }, present.map { it.second })
                    }
                }
                val present = if (connectGaps) values.present() else emptyList()
                if (present.isEmpty()) {
                    addSeries(values)
                } else {
                    series(present.map { it.first }, present.map { it.second })
                }
                if (markerPoints.isNotEmpty()) {
                    series(markerPoints, markerPoints.map { values[it] as Double })
                }
            }
        }
    }
    val pointCount = max(values.size, dots?.size ?: 0)
    val chart = rememberAppCartesianChart(
        color,
        lineThickness,
        dots != null,
        markerPoints.isNotEmpty(),
        valueLabel,
        labels,
        pointCount,
        overlaySpec(referenceLine, band, values, dots),
    )
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

/**
 * Adds [series]; with [connectGaps] only the non-null values are added, at their original index as x, so the line joins
 * consecutive readings across the gaps. An all-null series keeps the null-aware form (Vico rejects empty x/y lists).
 */
private fun List<Double?>.present(): List<Pair<Int, Double>> = mapIndexedNotNull { i, v -> v?.let { i to it } }

private const val EMPTY_LABEL = "-"

@Composable
private fun rememberReferenceDecorations(overlay: OverlaySpec?): List<Decoration> {
    val color = AppTheme.colors.textSecondary
    val bandColor = AppTheme.colors.athleticCyan.copy(alpha = AppLineChartDefaults.BAND_ALPHA)
    return remember(overlay?.reference, overlay?.band, color, bandColor) {
        listOfNotNull(
            overlay?.band?.let { band ->
                HorizontalBox(y = { _ -> band }, box = ShapeComponent(fill = Fill(bandColor)))
            },
            overlay?.reference?.let { value ->
                HorizontalLine(
                    y = { _ -> value },
                    line = LineComponent(
                        fill = Fill(color),
                        thickness = AppLineChartDefaults.ReferenceLineThickness,
                        shape = DashedShape(
                            RectangleShape,
                            AppLineChartDefaults.ReferenceDash,
                            AppLineChartDefaults.ReferenceDash,
                        ),
                    ),
                )
            },
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
    withMarkers: Boolean,
    valueLabel: (Double) -> String,
    labels: List<String>,
    pointCount: Int,
    overlay: OverlaySpec?,
): CartesianChart {
    val axisLabel =
        rememberAxisLabelComponent(AppTheme.typography.bodySmall.copy(color = AppTheme.colors.textSecondary))
    val gridColor = AppTheme.colors.divider
    val bottomSpacing = max(1, ceil(pointCount / AppLineChartDefaults.MAX_BOTTOM_LABELS.toDouble()).toInt())
    val decorations = rememberReferenceDecorations(overlay)
    return rememberCartesianChart(
        rememberLineCartesianLayer(
            rangeProvider =
            overlay?.let {
                CartesianLayerRangeProvider.fixed(minY = it.range.start, maxY = it.range.endInclusive)
            }
                ?: CartesianLayerRangeProvider.auto(),
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
                    if (withMarkers) add(rememberMarkersLine(color))
                },
            ),
        ),
        decorations = decorations,
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
                // Vico rejects blank labels; for a frame after the data shrinks its model still has more points than
                // the new labels, so an index past the end reuses the last label until the model catches up.
                CartesianValueFormatter { _, value, _ ->
                    labels.getOrElse(value.toInt()) { labels.lastOrNull().orEmpty() }.ifBlank { EMPTY_LABEL }
                }
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

/**
 * A titled chart for a card: [title] above, [chart] below with the standard gap. With [recordLegend] (the chart has
 * record markers) a sample of the marker and that text sit under the title; [recordLegendModifier] tags it.
 */
@Composable
fun AppLineChartBlock(
    title: String,
    modifier: Modifier = Modifier,
    recordLegendModifier: Modifier = Modifier,
    recordLegend: String? = null,
    chart: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppLineChartDefaults.TitleSpacing)) {
        Text(
            text = title,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        if (recordLegend != null) RecordLegend(recordLegend, recordLegendModifier)
        chart()
    }
}

@Composable
private fun RecordLegend(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(AppLineChartDefaults.MarkerSize)
                .clip(CircleShape)
                .background(AppTheme.colors.textPrimary)
                .border(AppLineChartDefaults.MarkerRing, AppTheme.colors.athleticCyan, CircleShape),
        )
        Text(text = text, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
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
