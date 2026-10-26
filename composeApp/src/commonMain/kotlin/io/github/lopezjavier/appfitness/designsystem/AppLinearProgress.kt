@file:Suppress("MatchingDeclarationName") // The file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry of [AppLinearProgress]. */
object AppLinearProgressDefaults {
    val BarHeight: Dp = 8.dp
}

/**
 * A plain progress bar towards a goal: an `athleticCyan` fill over a `divider` track, rounded. [fraction] is clamped
 * to 0..1; [stateDescription] is the spoken value ("Progreso hacia el objetivo, 50 %"), so the bar is never
 * colour-only. Unlike `MacroProgressBar` it has no macro colour and no amounts.
 */
@Composable
fun AppLinearProgress(fraction: Float, stateDescription: String, modifier: Modifier = Modifier) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(AppLinearProgressDefaults.BarHeight)
            .clip(AppShapes.full)
            .background(AppTheme.colors.divider)
            .semantics {
                this.stateDescription = stateDescription
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped)
                .background(AppTheme.colors.athleticCyan),
        )
    }
}
