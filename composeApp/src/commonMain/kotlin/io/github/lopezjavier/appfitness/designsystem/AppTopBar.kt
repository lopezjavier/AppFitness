@file:Suppress("MatchingDeclarationName", "ktlint:standard:property-naming")
// MatchingDeclarationName: the file holds AppTopBar and its Defaults object. property-naming: PascalCase consts.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_back
import io.github.lopezjavier.appfitness.composeapp.resources.ic_avatar_person
import org.jetbrains.compose.resources.painterResource

/** Sizes and test tags of [AppTopBar]. */
object AppTopBarDefaults {
    val Height: Dp = 56.dp
    val AvatarTouchTarget: Dp = 48.dp
    val AvatarSize: Dp = 36.dp
    val AvatarIconSize: Dp = 20.dp

    /** Optical offset: the visual avatar circle sits on the screen margin while the touch target stays 48 dp. */
    val EndPadding: Dp = Spacing.screenMargin - (AvatarTouchTarget - AvatarSize) / 2

    val NavigationTouchTarget: Dp = 48.dp
    val NavigationIconSize: Dp = 24.dp

    /** Optical offset: the back arrow glyph box sits on the screen margin while the touch target stays 48 dp. */
    val StartPaddingWithNavigation: Dp = Spacing.screenMargin - (NavigationTouchTarget - NavigationIconSize) / 2

    const val TestTag = "topbar"

    const val TitleTestTag = "${TestTag}_title"

    const val AvatarTestTag = "${TestTag}_avatar"

    const val BackTestTag = "${TestTag}_back"

    /** Child tags of a bar with a custom [prefix] (the catalog embeds bars that must not reuse `topbar_*`). */
    fun titleTag(prefix: String): String = "${prefix}_title"

    fun avatarTag(prefix: String): String = "${prefix}_avatar"

    fun backTag(prefix: String): String = "${prefix}_back"

    /** Tag of the profile photo inside the avatar circle. */
    fun avatarImageTag(prefix: String): String = "${prefix}_avatar_image"

    /** Tag of the placeholder icon shown when there is no photo or while it loads. */
    fun avatarPlaceholderTag(prefix: String): String = "${prefix}_avatar_placeholder"

    /** Insets the bar pads for: top and horizontal system insets. */
    val windowInsets: WindowInsets
        @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
}

/** An icon button of [AppTopBar]: its accessibility label and click handler. */
@Immutable
data class AppTopBarButton(val contentDescription: String, val onClick: () -> Unit)

/**
 * Top App Bar: section title with an optional leading back arrow and an optional trailing circular
 * avatar (the Google photo when [avatarImageUrl] is set, the placeholder icon otherwise or while it loads).
 * Flat `surfaceBase` with a `borderSubtle` bottom hairline; it pads the top and horizontal system insets itself.
 *
 * @param title section title, announced as a heading.
 * @param navigationButton leading back arrow (full-screen destinations), or null.
 * @param avatarButton trailing avatar button (tab destinations), or null.
 * @param avatarImageUrl profile photo URL for the avatar circle, or null for the placeholder icon.
 * @param windowInsets insets the bar pads for; pass `WindowInsets(0)` when it is embedded mid-screen.
 * @param testTagPrefix root tag; children are `<prefix>_title`, `<prefix>_back`, `<prefix>_avatar` and
 * `<prefix>_avatar_image`.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationButton: AppTopBarButton? = null,
    avatarButton: AppTopBarButton? = null,
    avatarImageUrl: String? = null,
    windowInsets: WindowInsets = AppTopBarDefaults.windowInsets,
    testTagPrefix: String = AppTopBarDefaults.TestTag,
) {
    val colors = AppTheme.colors
    val borderColor = colors.borderSubtle
    val startPadding =
        if (navigationButton != null) AppTopBarDefaults.StartPaddingWithNavigation else Spacing.screenMargin
    val endPadding = if (avatarButton != null) AppTopBarDefaults.EndPadding else Spacing.screenMargin
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .testTag(testTagPrefix)
            .background(colors.surfaceBase)
            .drawBehind {
                val y = size.height - AppBorder.thin.toPx() / 2
                drawLine(borderColor, Offset(0f, y), Offset(size.width, y), strokeWidth = AppBorder.thin.toPx())
            }
            .windowInsetsPadding(windowInsets)
            .heightIn(min = AppTopBarDefaults.Height)
            .padding(start = startPadding, end = endPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationButton != null) {
            BackButton(navigationButton, AppTopBarDefaults.backTag(testTagPrefix))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(
            text = title,
            style = AppTheme.typography.titleLarge,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag(AppTopBarDefaults.titleTag(testTagPrefix)).semantics { heading() },
        )
        if (avatarButton != null) {
            AvatarButton(avatarButton, avatarImageUrl, AppTopBarDefaults.avatarTag(testTagPrefix), testTagPrefix)
        }
    }
}

@Composable
private fun BackButton(button: AppTopBarButton, tag: String) {
    Box(
        modifier =
        Modifier
            .size(AppTopBarDefaults.NavigationTouchTarget)
            .clip(AppShapes.full)
            .clickable(role = Role.Button, onClick = button.onClick)
            .semantics { contentDescription = button.contentDescription }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_arrow_back),
            contentDescription = null,
            tint = AppTheme.colors.textPrimary,
            modifier = Modifier.size(AppTopBarDefaults.NavigationIconSize),
        )
    }
}

@Composable
private fun AvatarButton(button: AppTopBarButton, imageUrl: String?, tag: String, prefix: String) {
    val colors = AppTheme.colors
    // The placeholder stays until the photo has actually loaded, so a slow or failed load never leaves a blank circle.
    var photoLoaded by remember(imageUrl) { mutableStateOf(false) }
    Box(
        modifier =
        Modifier
            .size(AppTopBarDefaults.AvatarTouchTarget)
            .clip(AppShapes.full)
            .clickable(role = Role.Button, onClick = button.onClick)
            .semantics { contentDescription = button.contentDescription }
            .testTag(tag),
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
            if (!photoLoaded) {
                Icon(
                    painter = painterResource(Res.drawable.ic_avatar_person),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier =
                    Modifier
                        .size(AppTopBarDefaults.AvatarIconSize)
                        .testTag(AppTopBarDefaults.avatarPlaceholderTag(prefix)),
                )
            }
            if (imageUrl != null) {
                // Decorative: the button carries the accessible label.
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { photoLoaded = true },
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(AppShapes.full)
                        .testTag(AppTopBarDefaults.avatarImageTag(prefix)),
                )
            }
        }
    }
}

private const val LONG_TITLE = "Un título de sección muy largo para probar la elipsis"

private val PreviewAvatar = AppTopBarButton("Abrir perfil") {}
private val PreviewBack = AppTopBarButton("Volver") {}

@Preview
@Composable
private fun AppTopBarPreview() {
    AppTheme { AppTopBar("Diario", avatarButton = PreviewAvatar) }
}

@Preview
@Composable
private fun AppTopBarProfilePreview() {
    AppTheme { AppTopBar("Perfil", navigationButton = PreviewBack) }
}

@Preview
@Composable
private fun AppTopBarLongTitlePreview() {
    AppTheme { AppTopBar(LONG_TITLE, avatarButton = PreviewAvatar) }
}

@Preview
@Composable
private fun AppTopBarLargeFontPreview() {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
        AppTheme { AppTopBar(LONG_TITLE, avatarButton = PreviewAvatar) }
    }
}

@Preview
@Composable
private fun AppTopBarProfileLargeFontPreview() {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
        AppTheme { AppTopBar(LONG_TITLE, navigationButton = PreviewBack) }
    }
}
