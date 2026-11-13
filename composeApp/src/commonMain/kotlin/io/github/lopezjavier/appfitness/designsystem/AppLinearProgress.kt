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
    val StopDotSize: Dp = 4.dp
    val StopDotInset: Dp = 2.dp
    const val STOP_DOT_TAG: String = "track_stop_dot"
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
 * bar is never colour-only. [fillColor] overrides the fill (for example the caution token at a limit). Unlike
 * `MacroProgressBar` it has no macro colour and no amounts.
 */
@Composable
fun AppLinearProgress(
    fraction: Float,
    stateDescription: String,
    modifier: Modifier = Modifier,
    fillColor: Color? = null,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(AppLinearProgressDefaults.BarHeight)
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
                .fillMaxWidth(clamped)
                .background(fillColor ?: AppTheme.colors.athleticCyan),
        )
    }
}
