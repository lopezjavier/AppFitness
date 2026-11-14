@file:Suppress("MatchingDeclarationName") // The file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry of [SegmentedProgress] (C-101). */
object SegmentedProgressDefaults {
    val Height: Dp = 8.dp
    val Gap: Dp = 4.dp

    /** Above this many segments the bar falls back to [AppLinearProgress]. */
    const val MaxSegments: Int = 12

    fun segmentTag(testTag: String, index: Int): String = "${testTag}_segment_$index"
}

/**
 * Step progress as [total] rounded segments: the first [done] are `athleticCyan`, the one at 1-based [current] (when
 * given) is a track segment with a 1.5 dp cyan border, the rest are `trackInactive`. Above 12 segments it falls back
 * to [AppLinearProgress]. [stateDescription] is the spoken value ("Semana 2 de 4"); segments are decorative.
 * Segments fill once on first composition (R-97); [staggerOffset] shifts the 40 ms stagger of the first segment.
 */
@Composable
fun SegmentedProgress(
    total: Int,
    done: Int,
    stateDescription: String,
    modifier: Modifier = Modifier,
    current: Int? = null,
    testTag: String = "segmented_progress",
    staggerOffset: Int = 0,
) {
    if (total > SegmentedProgressDefaults.MaxSegments) {
        AppLinearProgress(
            fraction = if (total > 0) done.toFloat() / total else 0f,
            stateDescription = stateDescription,
            modifier = modifier.testTag(testTag),
        )
        return
    }
    val colors = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(SegmentedProgressDefaults.Height)
            .testTag(testTag)
            .semantics(mergeDescendants = true) { this.stateDescription = stateDescription },
        horizontalArrangement = Arrangement.spacedBy(SegmentedProgressDefaults.Gap),
    ) {
        repeat(total) { index ->
            val isDone = index < done
            val isCurrent = !isDone && current == index + 1
            val fill = rememberFillProgress(staggerOffset + index)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(AppShapes.full)
                    .background(colors.trackInactive)
                    .let { if (isCurrent) it.border(AppBorder.focus, colors.athleticCyan, AppShapes.full) else it }
                    .testTag(SegmentedProgressDefaults.segmentTag(testTag, index))
                    .clearAndSetSemantics {},
            ) {
                if (isDone) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill.value)
                            .background(colors.athleticCyan),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SegmentedProgressPreview() {
    AppTheme { SegmentedProgress(total = 4, done = 1, current = 2, stateDescription = "Semana 2 de 4") }
}
