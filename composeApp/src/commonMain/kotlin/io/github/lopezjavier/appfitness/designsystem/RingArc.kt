package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * The drawing shared by [ProgressRing], [ScoreRing] and [PercentRing]: a full [track] circle and, on top, a [progress]
 * arc of [sweep] degrees starting at the top ([ProgressRingDefaults.START_ANGLE]). Carries no semantics.
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
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val inset = strokeWidth.toPx() / 2
        val arcSize = Size(this.size.width - strokeWidth.toPx(), this.size.height - strokeWidth.toPx())
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = ProgressRingDefaults.FULL_SWEEP,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
        if (sweep > 0f) {
            drawArc(
                color = progress,
                startAngle = ProgressRingDefaults.START_ANGLE,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
    }
}
