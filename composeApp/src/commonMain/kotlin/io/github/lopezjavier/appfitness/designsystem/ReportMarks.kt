@file:Suppress("MagicNumber")
// MagicNumber: paint proportions of two small marks.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of the weekly report's graphics. */
object ReportMarks {
    /** Width of a main lift's trend line. */
    val SparklineWidth: Dp = 104.dp

    /** Height of the compact plan chart in the report's Weight card. */
    val CompactChartHeight: Dp = 128.dp
    val MarkHeight: Dp = 20.dp
    val GateDotsWidth: Dp = 56.dp
}

/**
 * Round slots, one per weigh-in day the gate needs: lit when logged, hollow when missing, so the count reads by shape
 * (R-41). [description] is the spoken count ("2 de 3 pesajes"); the caller writes the same figure as text nearby.
 */
@Composable
fun WeighInGateDots(have: Int, total: Int, description: String, modifier: Modifier = Modifier) {
    val lit = AppTheme.colors.athleticCyan
    val empty = AppTheme.colors.textTertiary
    Canvas(
        modifier
            .size(width = ReportMarks.GateDotsWidth, height = ReportMarks.MarkHeight)
            .semantics { contentDescription = description },
    ) {
        val radius = 7.dp.toPx()
        val step = size.width / total
        repeat(total) { index ->
            val center = Offset(step * index + step / 2f, size.height / 2f)
            if (index < have) {
                drawCircle(lit, radius, center)
            } else {
                drawCircle(empty, radius, center, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/** The older measurement (hollow) joined to the new one (filled) by a dashed line. Decorative. */
@Composable
fun MeasurementTrack(modifier: Modifier = Modifier) {
    val lit = AppTheme.colors.athleticCyan
    val hollow = AppTheme.colors.textTertiary
    Canvas(modifier.fillMaxWidth().height(ReportMarks.MarkHeight).clearAndSetSemantics {}) {
        val radius = 6.dp.toPx()
        val y = size.height / 2f
        val start = Offset(radius + 1.dp.toPx(), y)
        val end = Offset(size.width - radius - 1.dp.toPx(), y)
        drawLine(
            color = lit.copy(alpha = 0.5f),
            start = start,
            end = end,
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
        drawCircle(hollow, radius, start, style = Stroke(1.5.dp.toPx()))
        drawCircle(lit, radius, end)
    }
}
