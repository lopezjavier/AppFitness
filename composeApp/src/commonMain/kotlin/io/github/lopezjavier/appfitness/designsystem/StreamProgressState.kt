package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign

/**
 * R-31 state for waiting on the AI model (a seconds-long call whose answer arrives whole): an indeterminate
 * progress and a Spanish [message] such as "Preparando tu plan…". Not a skeleton: use `SkeletonBox` shapes for
 * loading stored data. [stateDescription] is what a screen reader announces; the whole block is tagged [testTag].
 */
@Composable
fun StreamProgressState(
    message: String,
    stateDescription: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Column(
        modifier = modifier
            .padding(Spacing.screenMargin)
            .semantics(mergeDescendants = true) {
                this.stateDescription = stateDescription
                liveRegion = LiveRegionMode.Polite
            }
            .let { if (testTag != null) it.testTag(testTag) else it },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // Decorative: the state description above carries the meaning.
        CircularProgressIndicator(color = AppTheme.colors.neonLime, modifier = Modifier.size(Spacing.minTouchTarget))
        Text(
            text = message,
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
