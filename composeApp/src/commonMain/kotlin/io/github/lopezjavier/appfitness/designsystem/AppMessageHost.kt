@file:Suppress("ktlint:standard:property-naming") // PascalCase consts, like the other Defaults objects.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Height of the footer the current `AppScreen` pins at the bottom (zero when it has none). `AppScreen` publishes it,
 * [AppMessageHost] reads it to sit above the footer.
 */
@Stable
class PinnedFooterState {
    var height: Dp by mutableStateOf(0.dp)
}

/** The shell's [PinnedFooterState]; null outside the shell (previews, isolated screen tests). */
val LocalPinnedFooter = staticCompositionLocalOf<PinnedFooterState?> { null }

/** Sizes and tag of [AppMessageHost]. */
object AppMessageHostDefaults {
    const val TestTag = "app_message"

    /** Gap between the message and the footer (or bottom edge) it floats above. */
    val Gap: Dp = Spacing.sm
    val MaxWidth: Dp = WindowWidthClassDefaults.FormContentMaxWidth
}

/**
 * Renders the current message of [LocalAppMessages] (R-28): a `surfaceOverlay` card with `bodyMedium` text, no
 * button, a polite live region (announced without stealing focus). The shell places it once, aligned to the bottom
 * over the content; it lifts itself above the footer pinned by `AppScreen` ([LocalPinnedFooter]).
 */
@Composable
fun AppMessageHost(modifier: Modifier = Modifier) {
    val message by LocalAppMessages.current.current.collectAsState()
    val text = message?.text ?: return
    val footer = LocalPinnedFooter.current?.height ?: 0.dp
    val colors = AppTheme.colors
    Box(modifier.padding(horizontal = Spacing.screenMargin).padding(bottom = footer + AppMessageHostDefaults.Gap)) {
        Surface(
            modifier =
            Modifier
                .widthIn(max = AppMessageHostDefaults.MaxWidth)
                .border(AppBorder.thin, colors.borderStrong, AppShapes.md)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .testTag(AppMessageHostDefaults.TestTag),
            shape = AppShapes.md,
            color = colors.surfaceOverlay,
        ) {
            Text(
                text = text,
                style = AppTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
        }
    }
}
