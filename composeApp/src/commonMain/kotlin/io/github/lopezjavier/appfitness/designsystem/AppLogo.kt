@file:Suppress("MatchingDeclarationName") // The file holds the logo and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_app_logo
import org.jetbrains.compose.resources.painterResource

/** Sizes of the [AppLogo] (square mark). */
object AppLogoDefaults {
    /** Compact widths. */
    val Size: Dp = 160.dp

    /** Widths from 600 dp up. */
    val SizeLarge: Dp = 200.dp

    fun size(widthClass: WindowWidthClass): Dp = if (widthClass == WindowWidthClass.Compact) Size else SizeLarge
}

/**
 * The app logo: the full-colour vector `ic_app_logo` as supplied (no tint, no plate), square, fitted into [size].
 * [contentDescription] is null when the logo is decorative.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = AppLogoDefaults.Size, contentDescription: String? = null) {
    Image(
        painter = painterResource(Res.drawable.ic_app_logo),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

@Preview
@Composable
private fun AppLogoPreview() {
    AppTheme { AppLogo() }
}
