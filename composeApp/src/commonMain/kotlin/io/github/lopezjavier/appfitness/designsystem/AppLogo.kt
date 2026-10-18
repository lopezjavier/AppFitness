@file:Suppress("MatchingDeclarationName") // The file holds the logo and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_app_logo
import org.jetbrains.compose.resources.painterResource

/** Sizes of the [AppLogo]. The mark is wide, so it is sized by width and its height follows. */
object AppLogoDefaults {
    /** Width / height of the mark; matches the drawable's viewport (the artwork is wide, not square). */
    const val ASPECT_RATIO: Float = 1024f / 466f

    /** Width on compact windows. */
    val Width: Dp = 260.dp

    /** Width from 600 dp up. */
    val WidthLarge: Dp = 340.dp

    fun width(widthClass: WindowWidthClass): Dp = if (widthClass == WindowWidthClass.Compact) Width else WidthLarge

    /** Height of the mark at [width], keeping [ASPECT_RATIO]. */
    fun height(width: Dp): Dp = width / ASPECT_RATIO
}

/**
 * The app logo: the white silhouette vector `ic_app_logo` (no tint, transparent background) laid out
 * at [width] with the height that keeps its wide aspect, so no empty height surrounds the mark.
 * [contentDescription] is null when the logo is decorative.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, width: Dp = AppLogoDefaults.Width, contentDescription: String? = null) {
    Image(
        painter = painterResource(Res.drawable.ic_app_logo),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.width(width).height(AppLogoDefaults.height(width)),
    )
}

@Preview
@Composable
private fun AppLogoPreview() {
    AppTheme { AppLogo() }
}
