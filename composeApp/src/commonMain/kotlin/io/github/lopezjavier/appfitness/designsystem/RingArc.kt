@file:Suppress("MagicNumber", "MatchingDeclarationName", "SpreadOperator")
// MagicNumber: paint proportions of the ring. MatchingDeclarationName: RingArc and its Defaults.
// SpreadOperator: the gradient stops are built once per draw.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Paint proportions of [RingArc]. */
internal object RingArcDefaults {
    /** From this stroke width the ring gets its halo, its inner light and the tip highlight. */
    val RichStroke: Dp = 8.dp

    /** Shortest arc (degrees) that shows the tip highlight. */
    const val MIN_TIP_SWEEP = 6f

    /** From this sweep (degrees) the arc counts as a closed turn. */
    const val FULL_TURN_SWEEP = 359f

    /** Smallest share of the turn the gradient spreads over, so a short arc still shows its ramp. */
    const val MIN_GRADIENT_SPAN = 0.04f
}

/**
 * The drawing shared by [ProgressRing], [ScoreRing] and [PercentRing]: a full [track] circle and, on top, a [progress]
 * arc of [sweep] degrees starting at the top ([ProgressRingDefaults.START_ANGLE]). The arc runs from the deep to the
 * bright stop of its accent ramp; rings with a stroke of 8 dp or more also get a soft halo, an inner light and a
 * highlight on the tip. Carries no semantics.
 */
@Composable
internal fun RingArc(
    sweep: Float,
    track: Color,
    progress: Color,
    size: Dp,
    strokeWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val ramp = AppPaint.ramp(progress)
    Canvas(modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val rich = strokeWidth >= RingArcDefaults.RichStroke
        val inset = strokePx / 2
        val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
        val topLeft = Offset(inset, inset)
        val radius = arcSize.width / 2
        if (rich) drawInnerLight(progress, radius - inset)
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = ProgressRingDefaults.FULL_SWEEP,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
        if (sweep <= 0f) return@Canvas
        val clamped = sweep.coerceAtMost(ProgressRingDefaults.FULL_SWEEP)
        // The sweep gradient starts at 3 o'clock: rotate so its origin is the top, where the arc starts.
        rotate(ProgressRingDefaults.START_ANGLE) {
            if (rich) {
                val wide = haloStroke(strokePx, 2.3f)
                val near = haloStroke(strokePx, 1.6f)
                drawArc(progress.copy(alpha = 0.07f), 0f, clamped, false, topLeft, arcSize, style = wide)
                drawArc(progress.copy(alpha = 0.10f), 0f, clamped, false, topLeft, arcSize, style = near)
            }
            drawArc(
                brush = sweepBrush(ramp, clamped, center),
                startAngle = 0f,
                sweepAngle = clamped,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
        // A full turn has no tip: the arc closes on itself.
        val hasTip = clamped >= RingArcDefaults.MIN_TIP_SWEEP && clamped < RingArcDefaults.FULL_TURN_SWEEP
        if (rich && hasTip) drawTip(clamped, radius, strokePx, ramp.bright)
    }
}

private fun haloStroke(strokePx: Float, factor: Float) = Stroke(width = strokePx * factor, cap = StrokeCap.Round)

/**
 * The ramp spread over the drawn part of the turn: deep at the origin, bright at the tip; past the tip it returns to
 * deep so the round cap at the origin never samples the bright end.
 */
private fun sweepBrush(ramp: AccentRamp, sweep: Float, center: Offset): Brush {
    val tip = (sweep / ProgressRingDefaults.FULL_SWEEP).coerceIn(RingArcDefaults.MIN_GRADIENT_SPAN, 1f)
    val stops = buildList {
        add(0f to ramp.deep)
        add(tip * 0.55f to ramp.base)
        add(tip to ramp.bright)
        if (tip < 0.94f) {
            add(tip + 0.03f to ramp.deep)
            add(1f to ramp.deep)
        }
    }
    return Brush.sweepGradient(*stops.toTypedArray(), center = center)
}

/** A faint light inside the ring: nothing in the middle (the number stays clean), the accent at the rim. */
private fun DrawScope.drawInnerLight(color: Color, innerRadius: Float) {
    if (innerRadius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            0.62f to Color.Transparent,
            1f to color.copy(alpha = 0.09f),
            center = center,
            radius = innerRadius,
        ),
        radius = innerRadius,
        center = center,
    )
}

/** The highlight on the tip of the arc: a soft halo and a bright core, like a light running along the ring. */
private fun DrawScope.drawTip(sweep: Float, radius: Float, strokePx: Float, bright: Color) {
    val angle = (ProgressRingDefaults.START_ANGLE + sweep) * PI / 180.0
    val tip = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
    drawRadialGlow(bright, tip, strokePx * 1.5f, alpha = 0.45f)
    drawCircle(Color.White.copy(alpha = 0.92f), radius = strokePx * 0.2f, center = tip)
}
