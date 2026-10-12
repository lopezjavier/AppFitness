package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/**
 * Kinetic Dark theme (dark only, planning/05-design-system.md). Provides [AppTheme.colors] and
 * [AppTheme.typography], and a consistent [MaterialTheme] so stock M3 components match.
 *
 * @param typeScale [TypeScale.Tablet] for widths >= 600 dp (wired in T-028).
 */
@Composable
fun AppTheme(typeScale: TypeScale = TypeScale.Phone, content: @Composable () -> Unit) {
    val fontFamily = plusJakartaSans()
    val typography = remember(fontFamily, typeScale) { appTypography(fontFamily, typeScale) }
    val materialTypography = remember(typography, fontFamily) { typography.toMaterialTypography(fontFamily) }
    CompositionLocalProvider(
        LocalAppColors provides KineticDarkColors,
        LocalAppTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = kineticDarkColorScheme(),
            typography = materialTypography,
            shapes = kineticMaterialShapes(),
            content = content,
        )
    }
}

/** Accessors for the Kinetic Dark tokens of the current composition. */
object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val typography: AppTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTypography.current
}
