@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_avatar_person
import org.jetbrains.compose.resources.painterResource

/** Geometry and tags of [AppAvatar]. */
object AppAvatarDefaults {
    /** Gap between the optional ring and the photo (the photo is inset, never cropped). */
    val RingGap: Dp = 2.dp

    /** Placeholder glyph size relative to the avatar size. */
    const val PLACEHOLDER_FRACTION = 0.55f

    fun imageTag(prefix: String): String = "${prefix}_avatar_image"

    fun placeholderTag(prefix: String): String = "${prefix}_avatar_placeholder"
}

/**
 * Circular profile avatar: the photo at [imageUrl], or the person placeholder when there is no URL, while it
 * loads and if it fails. Decorative (the caller owns the accessible name). A [ring] draws a
 * [AppBorder.focus] circle of that colour outside the photo, with [AppAvatarDefaults.RingGap] of space; the
 * total outer size stays [size].
 *
 * Children are tagged `<testTagPrefix>_avatar_image` and `<testTagPrefix>_avatar_placeholder`.
 */
@Composable
fun AppAvatar(imageUrl: String?, size: Dp, testTagPrefix: String, modifier: Modifier = Modifier, ring: Color? = null) {
    val colors = AppTheme.colors
    // The placeholder stays until the photo has actually loaded, so a slow or failed load never leaves a blank circle.
    var photoLoaded by remember(imageUrl) { mutableStateOf(false) }
    val inset = if (ring != null) AppBorder.focus + AppAvatarDefaults.RingGap else 0.dp
    Box(
        modifier =
        modifier
            .size(size)
            .then(if (ring != null) Modifier.border(AppBorder.focus, ring, AppShapes.full) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(inset)
                .clip(AppShapes.full)
                .background(colors.surfaceOverlay),
            contentAlignment = Alignment.Center,
        ) {
            if (!photoLoaded) {
                Icon(
                    painter = painterResource(Res.drawable.ic_avatar_person),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier =
                    Modifier
                        .size((size - inset * 2) * AppAvatarDefaults.PLACEHOLDER_FRACTION)
                        .testTag(AppAvatarDefaults.placeholderTag(testTagPrefix)),
                )
            }
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { photoLoaded = true },
                    modifier = Modifier.fillMaxSize().testTag(AppAvatarDefaults.imageTag(testTagPrefix)),
                )
            }
        }
    }
}

@Preview
@Composable
private fun AppAvatarPreview() {
    AppTheme { AppAvatar(imageUrl = null, size = 22.dp, testTagPrefix = "preview", ring = AppTheme.colors.neonLime) }
}
