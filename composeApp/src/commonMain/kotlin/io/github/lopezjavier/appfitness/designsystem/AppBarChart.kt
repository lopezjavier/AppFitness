@file:Suppress("MatchingDeclarationName") // The file holds AppBarChart and its defaults.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max

/** Geometry of [AppBarChart]. */
object AppBarChartDefaults {
    val Height: Dp = 180.dp

    /** Target number of labels on the bottom axis. */
    const val MAX_BOTTOM_LABELS = 4

    /** Bars take this share of their slot (R-112), capped at [MaxBarWidth]. */
    const val BAR_SLOT_FRACTION = 0.6f

    /** Widest a bar gets, so a chart with few bars never shows one full-width slab (R-112). */
    val MaxBarWidth: Dp = 32.dp

    /** Height of the stub drawn for a zero value, so an empty slot still reads as a data point (R-112). */
    val ZeroStub: Dp = 2.dp

    /** Rounded top corners of a bar. */
    val BarCorner: Dp = 6.dp

    /** Opacity of the bars that are not the lit one, so the latest period stands out. */
    const val DIM_ALPHA = 0.5f

    /** Opacity of the light above the lit bar. */
    const val GLOW_ALPHA = 0.22f

    /** Dash and gap of the middle guide. */
    val GuideDash: Dp = 4.dp

    /** Gap between the value axis labels and the plot, and between the plot and the bottom labels. */
    val LabelGap: Dp = 6.dp
}

/**
 * Column chart of one series (athleticCyan by default, no scroll): rounded bars with the accent ramp, the latest
 * non-zero one lit and the rest dimmed ([highlightIndex] picks another bar to light). One bar per entry of [values],
 * the entries named by [labels] (same size) on the bottom axis (a few of them, evenly spaced) and a "0" and the
 * maximum label on the value axis. [description] is the screen-reader summary, exposed as the state description of
 * the chart node.
 */
@Composable
fun AppBarChart(
    values: List<Int>,
    labels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.athleticCyan,
    valueLabel: (Int) -> String = { AppNumberFormat.integer(it) },
    highlightIndex: Int? = null,
) {
    require(values.size == labels.size) { "values and labels must have the same size" }
    val measurer = rememberTextMeasurer()
    val labelStyle = AppTheme.typography.bodySmall.copy(color = AppTheme.colors.textSecondary)
    val gridColor = AppTheme.colors.divider
    Canvas(
        modifier
            .height(AppBarChartDefaults.Height)
            .semantics { stateDescription = description },
    ) {
        drawBars(BarChartInput(values, labels, valueLabel, color, gridColor, highlightIndex), measurer, labelStyle)
    }
}

private class BarChartInput(
    val values: List<Int>,
    val labels: List<String>,
    val valueLabel: (Int) -> String,
    val color: Color,
    val gridColor: Color,
    val highlightIndex: Int?,
)

private class BarPlot(val left: Float, val top: Float, val bottom: Float, val width: Float) {
    val height: Float get() = bottom - top
}

private fun DrawScope.drawBars(input: BarChartInput, measurer: TextMeasurer, style: TextStyle) {
    val gap = AppBarChartDefaults.LabelGap.toPx()
    val maxValue = max(input.values.maxOrNull() ?: 0, 1)
    val axisTexts = listOf(input.valueLabel(0), input.valueLabel(maxValue)).map { measurer.measure(it, style) }
    val axisWidth = axisTexts.maxOf { it.size.width }
    val labelHeight = axisTexts.first().size.height
    val plot = BarPlot(
        left = axisWidth + gap,
        top = labelHeight / 2f,
        bottom = size.height - labelHeight - gap,
        width = size.width - axisWidth - gap,
    )
    drawGuides(plot, input.gridColor)
    drawText(
        axisTexts[0],
        topLeft = Offset(axisWidth - axisTexts[0].size.width.toFloat(), plot.bottom - labelHeight / 2f),
    )
    drawText(axisTexts[1], topLeft = Offset(axisWidth - axisTexts[1].size.width.toFloat(), 0f))
    if (input.values.isEmpty()) return
    val slot = plot.width / input.values.size
    val barWidth = minOf(slot * AppBarChartDefaults.BAR_SLOT_FRACTION, AppBarChartDefaults.MaxBarWidth.toPx())
    val lit = input.highlightIndex ?: input.values.indexOfLast { it > 0 }
    val ramp = AppPaint.ramp(input.color)
    input.values.forEachIndexed { index, value ->
        val left = plot.left + slot * index + (slot - barWidth) / 2f
        drawBar(input.color, ramp, value, maxValue, left, barWidth, plot, isLit = index == lit)
    }
    val step = max(1, ceil(input.values.size / AppBarChartDefaults.MAX_BOTTOM_LABELS.toDouble()).toInt())
    for (index in input.labels.indices step step) {
        val text = measurer.measure(input.labels[index], style)
        val center = plot.left + slot * index + slot / 2f
        val left = (center - text.size.width / 2f).coerceIn(plot.left, max(plot.left, size.width - text.size.width))
        drawText(text, topLeft = Offset(left, plot.bottom + gap))
    }
}

/** One bar: a dim stub for a zero, otherwise a rounded gradient column (with a glow when [isLit]). */
@Suppress("LongParameterList") // The bar geometry, computed once by the caller.
private fun DrawScope.drawBar(
    color: Color,
    ramp: AccentRamp,
    value: Int,
    maxValue: Int,
    left: Float,
    barWidth: Float,
    plot: BarPlot,
    isLit: Boolean,
) {
    val barHeight = max(plot.height * value.coerceAtLeast(0) / maxValue, AppBarChartDefaults.ZeroStub.toPx())
    val top = plot.bottom - barHeight
    if (value <= 0) {
        drawRoundRect(
            color = color.copy(alpha = AppBarChartDefaults.DIM_ALPHA),
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f),
        )
        return
    }
    if (isLit) {
        drawRadialGlow(color, Offset(left + barWidth / 2f, top), barWidth * 1.6f, AppBarChartDefaults.GLOW_ALPHA)
    }
    val corner = minOf(AppBarChartDefaults.BarCorner.toPx(), barWidth / 2f, barHeight)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = left + barWidth,
                bottom = plot.bottom,
                topLeftCornerRadius = CornerRadius(corner),
                topRightCornerRadius = CornerRadius(corner),
            ),
        )
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(ramp.bright, ramp.base, ramp.deep),
            startY = top,
            endY = plot.bottom,
        ),
        alpha = if (isLit) 1f else AppBarChartDefaults.DIM_ALPHA,
    )
}

/** Baseline, top guide and a dashed middle guide. */
private fun DrawScope.drawGuides(plot: BarPlot, gridColor: Color) {
    drawLine(gridColor, Offset(plot.left, plot.bottom), Offset(size.width, plot.bottom))
    drawLine(gridColor, Offset(plot.left, plot.top), Offset(size.width, plot.top))
    val middle = plot.top + plot.height / 2f
    val dash = AppBarChartDefaults.GuideDash.toPx()
    drawLine(
        color = gridColor,
        start = Offset(plot.left, middle),
        end = Offset(size.width, middle),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
    )
}

@Preview
@Composable
private fun AppBarChartPreview() {
    AppTheme {
        AppBarChart(
            values = listOf(12, 18, 0, 24, 30),
            labels = listOf("1 sep", "8 sep", "15 sep", "22 sep", "29 sep"),
            description = "84 series en el periodo",
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
