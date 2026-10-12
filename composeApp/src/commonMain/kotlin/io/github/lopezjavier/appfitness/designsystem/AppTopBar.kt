@file:Suppress("MatchingDeclarationName", "ktlint:standard:property-naming")
// MatchingDeclarationName: the file holds AppTopBar and its Defaults object. property-naming: PascalCase consts.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_profile
import org.jetbrains.compose.resources.painterResource

/** Sizes and test tags of [AppTopBar]. */
object AppTopBarDefaults {
    val Height: Dp = 56.dp
    val AvatarTouchTarget: Dp = 48.dp
    val AvatarSize: Dp = 36.dp
    val AvatarIconSize: Dp = 20.dp

    /** Optical offset: the visual avatar circle sits on the screen margin while the touch target stays 48 dp. */
    val EndPadding: Dp = Spacing.screenMargin - (AvatarTouchTarget - AvatarSize) / 2

    const val TestTag = "topbar"

    const val TitleTestTag = "topbar_title"

    const val AvatarTestTag = "topbar_avatar"
}

/**
 * Top App Bar: active section title + circular avatar (placeholder icon until the Google photo arrives).
 * Flat `surfaceBase` with a `borderSubtle` bottom hairline; it pads the top and horizontal system insets itself.
 *
 * @param title section title, announced as a heading.
 * @param onAvatarClick called when the avatar is tapped.
 * @param avatarContentDescription accessibility label of the avatar button.
 */
@Composable
fun AppTopBar(
    title: String,
    onAvatarClick: () -> Unit,
    avatarContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val borderColor = colors.borderSubtle
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .testTag(AppTopBarDefaults.TestTag)
            .background(colors.surfaceBase)
            .drawBehind {
                val y = size.height - AppBorder.thin.toPx() / 2
                drawLine(borderColor, Offset(0f, y), Offset(size.width, y), strokeWidth = AppBorder.thin.toPx())
            }
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .heightIn(min = AppTopBarDefaults.Height)
            .padding(start = Spacing.screenMargin, end = AppTopBarDefaults.EndPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AppTheme.typography.titleLarge,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag(AppTopBarDefaults.TitleTestTag).semantics { heading() },
        )
        Box(
            modifier =
            Modifier
                .size(AppTopBarDefaults.AvatarTouchTarget)
                .clip(AppShapes.full)
                .clickable(role = Role.Button, onClick = onAvatarClick)
                .semantics { contentDescription = avatarContentDescription }
                .testTag(AppTopBarDefaults.AvatarTestTag),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                Modifier
                    .size(AppTopBarDefaults.AvatarSize)
                    .clip(AppShapes.full)
                    .background(colors.surfaceOverlay)
                    .border(AppBorder.thin, colors.borderStrong, AppShapes.full),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_nav_profile),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(AppTopBarDefaults.AvatarIconSize),
                )
            }
        }
    }
}

private const val LONG_TITLE = "Un título de sección muy largo para probar la elipsis"

@Preview
@Composable
private fun AppTopBarPreview() {
    AppTheme { AppTopBar("Diario", onAvatarClick = {}, avatarContentDescription = "Abrir perfil") }
}

@Preview
@Composable
private fun AppTopBarLongTitlePreview() {
    AppTheme { AppTopBar(LONG_TITLE, onAvatarClick = {}, avatarContentDescription = "Abrir perfil") }
}

@Preview
@Composable
private fun AppTopBarLargeFontPreview() {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
        AppTheme { AppTopBar(LONG_TITLE, onAvatarClick = {}, avatarContentDescription = "Abrir perfil") }
    }
}
