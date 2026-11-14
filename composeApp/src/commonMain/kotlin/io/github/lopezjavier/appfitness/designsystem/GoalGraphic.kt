@file:Suppress("MatchingDeclarationName") // The file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.domain.model.Phase

/** Geometry of [GoalGraphic] (C-126). */
object GoalGraphicDefaults {
    val Width: Dp = 96.dp
    val Height: Dp = 48.dp

    /** The size from a font scale of [LargeFontScale] up, so the name beside it keeps its room. */
    val LargeWidth: Dp = 64.dp
    val LargeHeight: Dp = 40.dp
    const val LargeFontScale: Float = 1.3f

    val StrokeWidth: Dp = 2.5.dp
    val SecondStrokeWidth: Dp = 2.dp
    val BaselineWidth: Dp = 1.dp
    val BaselineDash: Dp = 4.dp
    val DotSize: Dp = 8.dp
    val DotRing: Dp = 2.dp

    /** Room kept around the plot so the end dot and its ring are not cut. */
    val Inset: Dp = 5.dp

    const val FillAlpha: Float = 0.24f
    const val BandAlpha: Float = 0.12f
    const val FLAT_LEVEL: Float = 0.5f
    const val BAND_LOW: Float = 0.36f
    const val BAND_HIGH: Float = 0.64f

    /** Unit-box points (x right, y up) of a rising curve (Volumen). */
    val Rising: List<Offset> = listOf(Offset(0f, 0.18f), Offset(0.4f, 0.30f), Offset(0.75f, 0.62f), Offset(1f, 0.86f))

    /** Definición mirrors [Rising] left to right. */
    val Falling: List<Offset> = Rising.map { Offset(it.x, 1f - it.y) }

    val RecompositionUp: List<Offset> = listOf(Offset(0f, 0.30f), Offset(1f, 0.74f))
    val RecompositionDown: List<Offset> = listOf(Offset(0f, 0.74f), Offset(1f, 0.30f))
}

/**
 * A decorative sketch of what the goal does to the weight (C-126): a rising curve for Volumen, its mirror for
 * Definición, a flat line over a band for Mantenimiento and two crossing lines for Recomposición. 96 x 48 dp (64 x 40
 * from a font scale of 1.3). It carries no semantics: the goal name beside it is the text. It is not a measured trend,
 * so it never uses the `ic_trending_*` glyphs (R-81). The stroke is drawn from start to end once (R-97, stagger 1) and
 * the end dot comes last.
 */
@Composable
fun GoalGraphic(phase: Phase, modifier: Modifier = Modifier, staggerIndex: Int = 1) {
    val large = LocalDensity.current.fontScale >= GoalGraphicDefaults.LargeFontScale
    val width = if (large) GoalGraphicDefaults.LargeWidth else GoalGraphicDefaults.Width
    val height = if (large) GoalGraphicDefaults.LargeHeight else GoalGraphicDefaults.Height
    val colors = AppTheme.colors
    val progress = rememberFillProgress(staggerIndex)
    Canvas(modifier.size(width, height).clearAndSetSemantics {}) {
        drawGoal(
            phase = phase,
            progress = progress.value,
            cyan = colors.athleticCyan,
            ring = colors.surfaceControl,
            baseline = colors.borderControl,
            second = colors.textTertiary,
        )
    }
}

@Suppress("LongParameterList") // The phase, the draw progress and the four token colours.
private fun DrawScope.drawGoal(
    phase: Phase,
    progress: Float,
    cyan: Color,
    ring: Color,
    baseline: Color,
    second: Color,
) {
    val inset = GoalGraphicDefaults.Inset.toPx()
    val plot = Size(size.width - 2 * inset, size.height - 2 * inset)
    fun at(p: Offset) = Offset(inset + p.x * plot.width, inset + (1f - p.y) * plot.height)
    val main = when (phase) {
        Phase.Gain -> GoalGraphicDefaults.Rising
        Phase.Cut -> GoalGraphicDefaults.Falling
        Phase.Maintenance -> listOf(Offset(0f, GoalGraphicDefaults.FLAT_LEVEL), Offset(1f, GoalGraphicDefaults.FLAT_LEVEL))
        Phase.Recomposition -> GoalGraphicDefaults.RecompositionUp
    }.map(::at)
    if (phase == Phase.Maintenance) {
        val top = at(Offset(0f, GoalGraphicDefaults.BAND_HIGH)).y
        val bottom = at(Offset(0f, GoalGraphicDefaults.BAND_LOW)).y
        drawRect(
            cyan.copy(alpha = GoalGraphicDefaults.BandAlpha * progress),
            Offset(inset, top),
            Size(plot.width, bottom - top),
        )
    }
    // Dashed baseline at the start level.
    val dash = GoalGraphicDefaults.BaselineDash.toPx()
    drawLine(
        color = baseline,
        start = Offset(inset, main.first().y),
        end = Offset(inset + plot.width, main.first().y),
        strokeWidth = GoalGraphicDefaults.BaselineWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
    )
    if (phase == Phase.Recomposition) {
        val down = GoalGraphicDefaults.RecompositionDown.map(::at)
        drawLine(
            color = second,
            start = down.first(),
            end = down.last(),
            strokeWidth = GoalGraphicDefaults.SecondStrokeWidth.toPx(),
            cap = StrokeCap.Round,
        )
    }
    val line = smoothPath(main)
    if (phase != Phase.Maintenance) {
        val area = Path().apply {
            addPath(line)
            lineTo(main.last().x, size.height)
            lineTo(main.first().x, size.height)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(
                listOf(cyan.copy(alpha = GoalGraphicDefaults.FillAlpha * progress), Color.Transparent),
            ),
        )
    }
    drawPath(
        partOf(line, progress),
        cyan,
        style = Stroke(GoalGraphicDefaults.StrokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    // The end dot comes last: it fades in over the final tenth of the stroke.
    val dotAlpha = ((progress - DOT_START) / (1f - DOT_START)).coerceIn(0f, 1f)
    if (dotAlpha > 0f) {
        val end = main.last()
        val radius = GoalGraphicDefaults.DotSize.toPx() / 2
        drawCircle(ring.copy(alpha = dotAlpha), radius + GoalGraphicDefaults.DotRing.toPx(), end)
        drawCircle(cyan.copy(alpha = dotAlpha), radius, end)
    }
}

private const val DOT_START = 0.9f
private const val SMOOTHING = 6f

/** A smooth path through [points] (Catmull-Rom as cubic Béziers); two points make a straight line. */
private fun smoothPath(points: List<Offset>): Path = Path().apply {
    moveTo(points.first().x, points.first().y)
    if (points.size == 2) {
        lineTo(points.last().x, points.last().y)
        return@apply
    }
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
        cubicTo(
            p1.x + (p2.x - p0.x) / SMOOTHING,
            p1.y + (p2.y - p0.y) / SMOOTHING,
            p2.x - (p3.x - p1.x) / SMOOTHING,
            p2.y - (p3.y - p1.y) / SMOOTHING,
            p2.x,
            p2.y,
        )
    }
}

/** The first [fraction] of [path], by length. */
private fun partOf(path: Path, fraction: Float): Path {
    if (fraction >= 1f) return path
    val measure = PathMeasure().apply { setPath(path, false) }
    return Path().apply { measure.getSegment(0f, measure.length * fraction, this, true) }
}

@Preview
@Composable
private fun GoalGraphicPreview() {
    AppTheme { GoalGraphic(Phase.Recomposition) }
}
