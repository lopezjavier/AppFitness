package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import kotlin.math.roundToInt

/** Adds [values] as one series; null entries leave a gap. A series with no value at all is skipped. */
internal fun LineCartesianLayerModel.BuilderScope.addSeries(values: List<Double?>) {
    val indexed = values.withIndex().filter { it.value != null }
    if (indexed.isEmpty()) return
    series(x = indexed.map { it.index }, y = indexed.map { it.value as Double })
}

/** The dots layer: an invisible connector with a low-opacity point on every value. */
@Composable
internal fun rememberDotsLine(color: Color): LineCartesianLayer.Line {
    val dot = color.copy(alpha = AppLineChartDefaults.DOT_ALPHA)
    val point =
        LineCartesianLayer.Point(
            rememberShapeComponent(fill = Fill(dot), shape = CircleShape),
            AppLineChartDefaults.DotSize,
        )
    return LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(color.copy(alpha = 0f))),
        stroke = LineCartesianLayer.LineStroke.Continuous(0.dp),
        pointProvider = LineCartesianLayer.PointProvider.single(point),
    )
}

/**
 * The record markers layer: an invisible connector with a [AppLineChartDefaults.MarkerSize] point (textPrimary fill and
 * a ring in [color]) on every marked value, so the marker is told apart from the line by shape, not only by colour.
 */
@Composable
internal fun rememberMarkersLine(color: Color): LineCartesianLayer.Line {
    val point =
        LineCartesianLayer.Point(
            rememberShapeComponent(
                fill = Fill(AppTheme.colors.textPrimary),
                shape = CircleShape,
                strokeFill = Fill(color),
                strokeThickness = AppLineChartDefaults.MarkerRing,
            ),
            AppLineChartDefaults.MarkerSize,
        )
    return LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(color.copy(alpha = 0f))),
        stroke = LineCartesianLayer.LineStroke.Continuous(0.dp),
        pointProvider = LineCartesianLayer.PointProvider.single(point),
    )
}

/**
 * Maps a horizontal touch to the nearest of [count] points (the plot starts after the axis labels, so a touch on
 * them gives the first point and one past the end the last). Pure so it can be tested without a layout.
 */
internal fun nearestPointIndex(x: Float, widthPx: Float, insetPx: Float, count: Int): Int {
    if (count <= 1) return 0
    val plot = (widthPx - insetPx).coerceAtLeast(1f)
    val fraction = ((x - insetPx) / plot).coerceIn(0f, 1f)
    return (fraction * (count - 1)).roundToInt()
}

/**
 * Long press / tap on the plot report the nearest point; [actionLabel] adds an accessibility click action that
 * reports the last point (the latest reading) for screen-reader users, who cannot long press a position.
 */
@Composable
internal fun Modifier.pointSelection(
    count: Int,
    onLongPress: ((Int) -> Unit)?,
    onTap: ((Int) -> Unit)?,
    actionLabel: String?,
): Modifier {
    if (count == 0 || (onLongPress == null && onTap == null)) return this
    val insetPx = with(androidx.compose.ui.platform.LocalDensity.current) { AppLineChartDefaults.PlotStartInset.toPx() }
    val select = onLongPress ?: onTap
    return this
        .pointerInput(count, onLongPress, onTap) {
            detectTapGestures(
                onLongPress = onLongPress?.let { cb ->
                    { at -> cb(nearestPointIndex(at.x, size.width.toFloat(), insetPx, count)) }
                },
                onTap = onTap?.let { cb ->
                    { at -> cb(nearestPointIndex(at.x, size.width.toFloat(), insetPx, count)) }
                },
            )
        }
        .semantics {
            if (actionLabel != null && select != null) {
                onClick(label = actionLabel) {
                    select(count - 1)
                    true
                }
            }
        }
}
