@file:Suppress("ktlint:standard:property-naming", "MatchingDeclarationName") // PascalCase consts (Defaults).

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_back_cd
import org.jetbrains.compose.resources.stringResource

/** Defaults of [AppScreen]. */
object AppScreenDefaults {
    const val TestTag = "app_screen"

    fun footerTag(prefix: String): String = "${prefix}_footer"
}

/**
 * Screen template (R-06): the v2 top bar (title, optional [subtitle], back arrow when [onBack] is given, [actions]),
 * the body capped to [maxContentWidth] and centred on wide windows, and an optional [footer] pinned at the bottom
 * (capped the same way, above the keyboard). The footer's height is published for the message host so a message
 * floats above it. [content] receives the padding of the body (no top inset: the bar already took it).
 *
 * Insets: the bar and the footer pad for whatever system insets are not consumed yet, so inside the shell (which
 * consumes them for `AppScreen` routes) nothing is padded twice, and standalone (tests, previews) the screen is
 * edge-to-edge safe. Tags: `<prefix>` (the bar), `<prefix>_title`, `<prefix>_subtitle`, `<prefix>_back`,
 * `<prefix>_footer`.
 */
@Suppress("LongParameterList") // Template slots fixed by the T-118 contract.
@Composable
fun AppScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: List<AppTopBarAction> = emptyList(),
    footer: (@Composable () -> Unit)? = null,
    maxContentWidth: Dp = WindowWidthClassDefaults.FormContentMaxWidth,
    testTagPrefix: String = AppScreenDefaults.TestTag,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backDescription = stringResource(Res.string.topbar_back_cd)
    val backButton = remember(onBack, backDescription) { onBack?.let { AppTopBarButton(backDescription, it) } }
    Column(modifier.fillMaxSize().background(AppTheme.colors.surfaceBase)) {
        AppTopBar(
            title = title,
            subtitle = subtitle,
            navigationButton = backButton,
            actions = actions,
            testTagPrefix = testTagPrefix,
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.widthIn(max = maxContentWidth).fillMaxSize()) { content(PaddingValues()) }
        }
        if (footer != null) {
            PinnedFooter(maxContentWidth, AppScreenDefaults.footerTag(testTagPrefix), footer)
        }
    }
}

@Composable
private fun PinnedFooter(maxContentWidth: Dp, tag: String, footer: @Composable () -> Unit) {
    val state = LocalPinnedFooter.current
    val density = LocalDensity.current
    DisposableEffect(state) { onDispose { state?.height = Spacing.none } }
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .onSizeChanged { size -> state?.height = with(density) { size.height.toDp() } }
            .testTag(tag),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.widthIn(max = maxContentWidth).fillMaxWidth().padding(Spacing.screenMargin)) { footer() }
    }
}
