@file:Suppress("MatchingDeclarationName") // The file holds AppBarChart and its defaults.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

    /** Gap between two bars. */
    val BarGap: Dp = 4.dp

    /** Rounded top corners of a bar. */
    val BarCorner: Dp = 2.dp

    /** Gap between the value axis labels and the plot, and between the plot and the bottom labels. */
    val LabelGap: Dp = 6.dp
}

/**
 * Column chart of one series (athleticCyan by default, no scroll, no gradients): one bar per entry of [values], the
 * entries named by [labels] (same size) on the bottom axis (a few of them, evenly spaced) and a "0" and maximum label
 * on the value axis. [description] is the screen-reader summary, exposed as the state description of the chart node.
 */
@Composable
fun AppBarChart(
    values: List<Int>,
    labels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.athleticCyan,
    valueLabel: (Int) -> String = { AppNumberFormat.integer(it) },
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
        drawBars(BarChartInput(values, labels, valueLabel, color, gridColor), measurer, labelStyle)
    }
}

private class BarChartInput(
    val values: List<Int>,
    val labels: List<String>,
    val valueLabel: (Int) -> String,
    val color: Color,
    val gridColor: Color,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBars(
    input: BarChartInput,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val gap = AppBarChartDefaults.LabelGap.toPx()
    val maxValue = max(input.values.maxOrNull() ?: 0, 1)
    val axisTexts = listOf(input.valueLabel(0), input.valueLabel(maxValue)).map { measurer.measure(it, style) }
    val axisWidth = axisTexts.maxOf { it.size.width }
    val labelHeight = axisTexts.first().size.height
    val plotLeft = axisWidth + gap
    val plotTop = labelHeight / 2f
    val plotBottom = size.height - labelHeight - gap
    val plotHeight = plotBottom - plotTop
    val plotWidth = size.width - plotLeft
    // Baseline and top guide.
    drawLine(input.gridColor, Offset(plotLeft, plotBottom), Offset(size.width, plotBottom))
    drawLine(input.gridColor, Offset(plotLeft, plotTop), Offset(size.width, plotTop))
    drawText(
        axisTexts[0],
        topLeft = Offset(axisWidth - axisTexts[0].size.width.toFloat(), plotBottom - labelHeight / 2f),
    )
    drawText(axisTexts[1], topLeft = Offset(axisWidth - axisTexts[1].size.width.toFloat(), 0f))
    if (input.values.isEmpty()) return
    val slot = plotWidth / input.values.size
    val barWidth = max(slot - AppBarChartDefaults.BarGap.toPx(), 1f)
    val corner = CornerRadius(AppBarChartDefaults.BarCorner.toPx())
    input.values.forEachIndexed { index, value ->
        val barHeight = plotHeight * value.coerceAtLeast(0) / maxValue
        if (barHeight > 0f) {
            drawRoundRect(
                color = input.color,
                topLeft = Offset(plotLeft + slot * index + (slot - barWidth) / 2f, plotBottom - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
    }
    val step = max(1, ceil(input.values.size / AppBarChartDefaults.MAX_BOTTOM_LABELS.toDouble()).toInt())
    for (index in input.labels.indices step step) {
        val text = measurer.measure(input.labels[index], style)
        val center = plotLeft + slot * index + slot / 2f
        val left = (center - text.size.width / 2f).coerceIn(plotLeft, max(plotLeft, size.width - text.size.width))
        drawText(text, topLeft = Offset(left, plotBottom + gap))
    }
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
