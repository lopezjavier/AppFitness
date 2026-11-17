package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry of the diary share bars. */
object DiaryGraphicsDefaults {
    val SplitHeight: Dp = 10.dp
    val ShareHeight: Dp = 4.dp
    val SegmentGap: Dp = 2.dp
    const val TRACK_ALPHA = 0.6f
    const val MIN_SHARE = 0.02f
}

/** One segment of [DiarySplitBar]: its [value] (any unit) and [color]. */
data class DiarySplitPart(val value: Float, val color: Color)

/**
 * A slim bar split into [parts] by their share of the total (the calories of the day by meal). With no value it shows
 * only the track. Decorative: the caller writes the figures as text.
 */
@Composable
fun DiarySplitBar(parts: List<DiarySplitPart>, modifier: Modifier = Modifier) {
    val shown = parts.filter { it.value > 0f }
    val reveal = rememberFillProgress()
    Row(
        modifier
            .fillMaxWidth()
            .height(DiaryGraphicsDefaults.SplitHeight)
            .clip(CircleShape)
            .background(AppTheme.colors.surfaceOverlay)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(DiaryGraphicsDefaults.SegmentGap),
    ) {
        shown.forEach { part ->
            Box(
                Modifier
                    .weight(part.value * reveal.value.coerceAtLeast(DiaryGraphicsDefaults.MIN_SHARE))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(AppPaint.horizontal(part.color)),
            )
        }
    }
}

/** A thin bar showing [fraction] (0..1) of a whole in [color]: the share of the day of one meal. Decorative. */
@Composable
fun DiaryShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val reveal = rememberFillProgress()
    val value = (fraction * reveal.value).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(DiaryGraphicsDefaults.ShareHeight)
            .clip(CircleShape)
            .background(AppTheme.colors.surfaceOverlay.copy(alpha = DiaryGraphicsDefaults.TRACK_ALPHA))
            .clearAndSetSemantics {},
    ) {
        if (value > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.coerceAtLeast(DiaryGraphicsDefaults.MIN_SHARE))
                    .clip(CircleShape)
                    .background(AppPaint.horizontal(color)),
            )
        }
    }
}

/** A small round colour dot, the key of a legend entry. */
@Composable
fun DiaryDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(Spacing.sm).clip(CircleShape).background(color))
}
