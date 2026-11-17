@file:Suppress("MatchingDeclarationName") // The file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry of [AppLinearProgress]. */
object AppLinearProgressDefaults {
    val BarHeight: Dp = 8.dp

    /** The thin session bar under a top bar (C-69). */
    val SlimBarHeight: Dp = 4.dp
    val StopDotSize: Dp = 4.dp
    val StopDotInset: Dp = 2.dp
    const val STOP_DOT_TAG: String = "track_stop_dot"

    /** The marker of a bar (05 §6.0f): a 2 dp `textPrimary` tick that overhangs the track by 3 dp above and below. */
    val MarkerWidth: Dp = 2.dp
    val MarkerOverhang: Dp = 3.dp
}

/** The 4 dp `textTertiary` dot at the end of an unfilled bar track (C-11); the fill drawn over it hides it at 100 %. */
@Composable
internal fun BoxScope.TrackStopDot() {
    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = AppLinearProgressDefaults.StopDotInset)
            .size(AppLinearProgressDefaults.StopDotSize)
            .testTag(AppLinearProgressDefaults.STOP_DOT_TAG)
            .clip(AppShapes.full)
            .background(AppTheme.colors.textTertiary),
    )
}

/**
 * A plain progress bar towards a goal: an `athleticCyan` fill over a `trackInactive` track with a stop dot, rounded.
 * [fraction] is clamped to 0..1; [stateDescription] is the spoken value ("Progreso hacia el objetivo, 50 %"), so the
 * bar is never colour-only. [fillColor] overrides the fill (for example the caution token at a limit). With a
 * [staggerIndex] the fill rises once on first composition (R-97). Unlike
 * `MacroProgressBar` it has no macro colour and no amounts. [markerFraction] (0..1, null for none) draws the marker
 * tick across the track: a target or a "where you should be by now" point (05 §6.0f); the bar then takes 6 dp more
 * height for the overhang, and the caller's [stateDescription] must say what the marker means in words.
 */
@Composable
fun AppLinearProgress(
    fraction: Float,
    stateDescription: String,
    modifier: Modifier = Modifier,
    fillColor: Color? = null,
    barHeight: Dp = AppLinearProgressDefaults.BarHeight,
    staggerIndex: Int? = null,
    markerFraction: Float? = null,
) {
    if (markerFraction == null) {
        LinearTrack(fraction, stateDescription, fillColor, barHeight, staggerIndex, modifier)
        return
    }
    val markerColor = AppTheme.colors.textPrimary
    val at = markerFraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(barHeight + AppLinearProgressDefaults.MarkerOverhang * 2)
            .drawWithContent {
                drawContent()
                val half = AppLinearProgressDefaults.MarkerWidth.toPx() / 2
                val x = (size.width * at).coerceIn(half, size.width - half)
                drawLine(
                    color = markerColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = AppLinearProgressDefaults.MarkerWidth.toPx(),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        LinearTrack(fraction, stateDescription, fillColor, barHeight, staggerIndex)
    }
}

@Composable
private fun LinearTrack(
    fraction: Float,
    stateDescription: String,
    fillColor: Color?,
    barHeight: Dp,
    staggerIndex: Int?,
    modifier: Modifier = Modifier,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    val fill = rememberFillProgress(staggerIndex ?: 0)
    Box(
        modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(AppShapes.full)
            .background(AppTheme.colors.trackInactive)
            .semantics {
                this.stateDescription = stateDescription
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            },
    ) {
        TrackStopDot()
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(if (staggerIndex != null) clamped * fill.value else clamped)
                .clip(AppShapes.full)
                .background(AppPaint.horizontal(fillColor ?: AppTheme.colors.athleticCyan))
                .barTipLight(),
        )
    }
}

/**
 * The light on the tip of a filled bar: a short white fade at its end, so the fill reads as a moving mark and not as a
 * flat slab. Decorative; drawn inside the (clipped) fill.
 */
internal fun Modifier.barTipLight(): Modifier = drawWithContent {
    drawContent()
    val reach = size.height * BAR_TIP_REACH
    if (size.width > reach) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = BAR_TIP_ALPHA)),
                startX = size.width - reach,
                endX = size.width,
            ),
            topLeft = Offset(size.width - reach, 0f),
            size = Size(reach, size.height),
        )
    }
}

private const val BAR_TIP_REACH = 2.5f
private const val BAR_TIP_ALPHA = 0.32f
