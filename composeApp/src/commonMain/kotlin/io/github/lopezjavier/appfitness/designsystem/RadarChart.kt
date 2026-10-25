@file:Suppress("MatchingDeclarationName") // The file holds RadarChart, RadarLegend and the defaults.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Geometry of [RadarChart] and [RadarLegend]. */
object RadarChartDefaults {
    /** Number of concentric guide polygons. */
    const val RINGS = 4

    /** Fill opacity of the current polygon. */
    const val FILL_ALPHA = 0.25f

    val StrokeWidth: Dp = 2.dp
    val GridWidth: Dp = 1.dp

    /** Axis labels wrap at this width. */
    val LabelMaxWidth: Dp = 72.dp
    val LabelGap: Dp = 6.dp
    val DashLength: Dp = 6.dp
    val LegendSampleWidth: Dp = 24.dp
}

/**
 * Radar of one or two series over [axes] (one spoke per name). [current] is drawn solid with a light fill in
 * athleticCyan; [previous] (null to omit) is drawn dashed in textSecondary. Both lists have one value per axis and
 * share one scale (the largest value). With no positive value only the grid is drawn. [description] is the
 * screen-reader summary (each axis with its values), exposed as the state description.
 */
@Composable
fun RadarChart(
    axes: List<String>,
    current: List<Double>,
    description: String,
    modifier: Modifier = Modifier,
    previous: List<Double>? = null,
) {
    require(axes.size >= MIN_AXES && current.size == axes.size && (previous == null || previous.size == axes.size)) {
        "A radar needs at least $MIN_AXES axes and one value per axis"
    }
    val measurer = rememberTextMeasurer()
    val style = AppTheme.typography.bodySmall.copy(color = AppTheme.colors.textSecondary)
    val colors = RadarColors(
        AppTheme.colors.athleticCyan,
        AppTheme.colors.textSecondary,
        AppTheme.colors.divider,
        AppTheme.colors.borderSubtle,
    )
    Canvas(modifier.aspectRatio(1f).semantics { stateDescription = description }) {
        drawRadar(axes, current, previous, colors, measurer, style)
    }
}

private const val MIN_AXES = 3

private class RadarColors(val current: Color, val previous: Color, val grid: Color, val spoke: Color)

// Private drawing routine: one pass over the grid, labels and both polygons.
@Suppress("LongParameterList", "LongMethod")
private fun DrawScope.drawRadar(
    axes: List<String>,
    current: List<Double>,
    previous: List<Double>?,
    colors: RadarColors,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val labelWidth = RadarChartDefaults.LabelMaxWidth.roundToPx()
    val labels = axes.map { measurer.measure(it, style, constraints = Constraints(maxWidth = labelWidth)) }
    val gap = RadarChartDefaults.LabelGap.toPx()
    val maxLabelHeight = labels.maxOf { it.size.height }
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width / 2f - labelWidth - gap, size.height / 2f - maxLabelHeight - gap).coerceAtLeast(1f)
    val angleOf = { i: Int -> -PI / 2 + 2 * PI * i / axes.size }
    val point = { i: Int, fraction: Float ->
        Offset(
            center.x + radius * fraction * cos(angleOf(i)).toFloat(),
            center.y + radius * fraction * sin(angleOf(i)).toFloat(),
        )
    }
    val grid = Stroke(RadarChartDefaults.GridWidth.toPx())
    for (ring in 1..RadarChartDefaults.RINGS) {
        drawPath(
            polygon(
                axes.indices.map {
                    point(it, ring / RadarChartDefaults.RINGS.toFloat())
                },
            ),
            colors.grid,
            style = grid,
        )
    }
    axes.indices.forEach { drawLine(colors.spoke, center, point(it, 1f), RadarChartDefaults.GridWidth.toPx()) }
    labels.forEachIndexed { i, text ->
        val dx = cos(angleOf(i)).toFloat()
        val dy = sin(angleOf(i)).toFloat()
        val anchor = point(i, 1f)
        val x = when {
            dx > SIDE_THRESHOLD -> anchor.x + gap
            dx < -SIDE_THRESHOLD -> anchor.x - gap - text.size.width
            else -> anchor.x - text.size.width / 2f
        }
        val y = if (dy < 0) anchor.y - gap - text.size.height else anchor.y + gap
        drawText(
            text,
            topLeft = Offset(
                x.coerceIn(0f, size.width - text.size.width),
                y.coerceIn(
                    0f,
                    size.height - text.size.height,
                ),
            ),
        )
    }
    val scale = max(current.maxOrNull() ?: 0.0, previous?.maxOrNull() ?: 0.0)
    if (scale <= 0.0) return
    val shape = { values: List<Double> -> polygon(values.mapIndexed { i, v -> point(i, (v / scale).toFloat()) }) }
    previous?.let {
        val dash = PathEffect.dashPathEffect(
            floatArrayOf(RadarChartDefaults.DashLength.toPx(), RadarChartDefaults.DashLength.toPx()),
        )
        drawPath(shape(it), colors.previous, style = Stroke(RadarChartDefaults.StrokeWidth.toPx(), pathEffect = dash))
    }
    val now = shape(current)
    drawPath(now, colors.current.copy(alpha = RadarChartDefaults.FILL_ALPHA), style = Fill)
    drawPath(now, colors.current, style = Stroke(RadarChartDefaults.StrokeWidth.toPx()))
}

private const val SIDE_THRESHOLD = 0.3f

private fun polygon(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    close()
}

/**
 * Legend of [RadarChart]: a solid cyan line sample with [currentLabel] and, when [previousLabel] is not null, a dashed
 * textSecondary sample with it (the line style carries the meaning too, never colour alone). Each text is tagged
 * `<testTag>_current` / `<testTag>_previous` through [currentModifier] and [previousModifier].
 */
@Composable
fun RadarLegend(
    currentLabel: String,
    previousLabel: String?,
    modifier: Modifier = Modifier,
    currentModifier: Modifier = Modifier,
    previousModifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        LegendEntry(currentLabel, AppTheme.colors.athleticCyan, dashed = false, currentModifier)
        previousLabel?.let { LegendEntry(it, AppTheme.colors.textSecondary, dashed = true, previousModifier) }
    }
}

@Composable
private fun LegendEntry(text: String, color: Color, dashed: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            Modifier.width(
                RadarChartDefaults.LegendSampleWidth,
            ).padding(vertical = Spacing.xs).aspectRatio(LEGEND_RATIO),
        ) {
            val effect = if (dashed) {
                PathEffect.dashPathEffect(
                    floatArrayOf(
                        RadarChartDefaults.DashLength.toPx() / 2,
                        RadarChartDefaults.DashLength.toPx() / 2,
                    ),
                )
            } else {
                null
            }
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                RadarChartDefaults.StrokeWidth.toPx(),
                pathEffect = effect,
            )
        }
        Text(text, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
    }
}

private const val LEGEND_RATIO = 3f

@Preview
@Composable
private fun RadarChartPreview() {
    AppTheme {
        RadarChart(
            axes = listOf("Pecho", "Espalda", "Hombros", "Bíceps", "Tríceps"),
            current = listOf(12.0, 8.0, 6.0, 4.0, 5.0),
            previous = listOf(8.0, 9.0, 3.0, 4.0, 2.0),
            description = "Pecho 12 series",
            modifier = Modifier.padding(Spacing.lg),
        )
    }
}
