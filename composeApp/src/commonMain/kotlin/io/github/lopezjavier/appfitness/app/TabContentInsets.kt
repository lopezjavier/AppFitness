package io.github.lopezjavier.appfitness.app

import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.designsystem.AppNavigationType
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.UniversalFabDefaults

/** Space tab content reserves for the shell chrome. */
internal data class TabContentInsets(val start: Dp, val bottom: Dp, val contentBottomClearance: Dp)

/**
 * Bar: the bar height at the bottom and the FAB clearance. Rail: the rail width at the start, only the
 * system inset at the bottom, and no FAB clearance (the FAB lives in the rail).
 */
internal fun tabContentInsets(
    type: AppNavigationType,
    barHeight: Dp,
    railWidth: Dp,
    innerStart: Dp,
    innerBottom: Dp,
): TabContentInsets = when (type) {
    AppNavigationType.Bar -> TabContentInsets(innerStart, barHeight, UniversalFabDefaults.ContentClearance)
    AppNavigationType.Rail -> TabContentInsets(railWidth, innerBottom, Spacing.none)
}
