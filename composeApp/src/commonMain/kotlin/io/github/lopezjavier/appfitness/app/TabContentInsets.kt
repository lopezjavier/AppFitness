package io.github.lopezjavier.appfitness.app

import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.designsystem.AppNavigationType

/** Space tab content reserves for the shell chrome. */
internal data class TabContentInsets(val start: Dp, val bottom: Dp)

/**
 * Bar: the bar height at the bottom. Rail: the rail width at the start and only the system inset at
 * the bottom.
 */
internal fun tabContentInsets(
    type: AppNavigationType,
    barHeight: Dp,
    railWidth: Dp,
    innerStart: Dp,
    innerBottom: Dp,
): TabContentInsets = when (type) {
    AppNavigationType.Bar -> TabContentInsets(innerStart, barHeight)
    AppNavigationType.Rail -> TabContentInsets(railWidth, innerBottom)
}
