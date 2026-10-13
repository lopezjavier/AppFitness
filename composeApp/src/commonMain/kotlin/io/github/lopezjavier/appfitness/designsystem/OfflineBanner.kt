package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_cloud_off
import io.github.lopezjavier.appfitness.composeapp.resources.offline_banner_message
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Colours of the [OfflineBanner]: strip background, bottom line, icon and text. */
@Immutable
data class OfflineBannerColors(val background: Color, val border: Color, val icon: Color, val text: Color)

/** Sizes and colours of [OfflineBanner]. */
object OfflineBannerDefaults {
    val HorizontalPadding: Dp = Spacing.screenMargin
    val VerticalPadding: Dp = Spacing.sm
    val IconTextGap: Dp = Spacing.sm
    val IconSize: Dp = 20.dp

    /** Lower bound only: the strip grows when the text wraps at a large font scale. */
    val MinHeight: Dp = 36.dp

    fun colors(appColors: AppColors): OfflineBannerColors = OfflineBannerColors(
        background = appColors.surfaceRaised,
        border = appColors.borderSubtle,
        icon = appColors.energyAmber,
        text = appColors.textPrimary,
    )
}

/**
 * Full-width "no connection" strip shown below the top bar. Non-interactive; a polite live region on the
 * root (merged), so a screen reader announces [message] once when the banner appears. The text wraps and
 * the strip grows at large font scales. With [testTagPrefix] the root gets the prefix and the text
 * `<prefix>_message`. The caller's [modifier] lands on the root.
 */
@Composable
fun OfflineBanner(
    modifier: Modifier = Modifier,
    message: String = stringResource(Res.string.offline_banner_message),
    testTagPrefix: String? = null,
) {
    val colors = OfflineBannerDefaults.colors(AppTheme.colors)
    val rootModifier = testTagPrefix?.let { modifier.testTag(it) } ?: modifier
    val messageTag = childTestTag(testTagPrefix, "message")?.let { Modifier.testTag(it) } ?: Modifier
    Row(
        modifier =
        rootModifier
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .background(colors.background)
            .drawBehind {
                val y = size.height - AppBorder.thin.toPx() / 2
                drawLine(colors.border, Offset(0f, y), Offset(size.width, y), strokeWidth = AppBorder.thin.toPx())
            }
            .heightIn(min = OfflineBannerDefaults.MinHeight)
            .padding(
                horizontal = OfflineBannerDefaults.HorizontalPadding,
                vertical = OfflineBannerDefaults.VerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OfflineBannerDefaults.IconTextGap),
    ) {
        // Decorative: the text carries the meaning.
        Icon(
            painter = painterResource(Res.drawable.ic_cloud_off),
            contentDescription = null,
            tint = colors.icon,
            modifier = Modifier.size(OfflineBannerDefaults.IconSize),
        )
        Text(
            text = message,
            style = AppTheme.typography.bodyMedium,
            color = colors.text,
            modifier = Modifier.weight(1f).then(messageTag),
        )
    }
}

@Preview
@Composable
private fun OfflineBannerPreview() {
    AppTheme { OfflineBanner(Modifier.fillMaxWidth()) }
}

@Preview
@Composable
private fun OfflineBannerLargeFontPreview() {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
        AppTheme { OfflineBanner(Modifier.fillMaxWidth()) }
    }
}
