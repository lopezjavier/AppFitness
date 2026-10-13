package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

// Built once: the theme is static (dark only), so nothing is allocated on recomposition.
private val KineticDarkColorScheme = kineticDarkColorScheme()
private val KineticMaterialShapes = kineticMaterialShapes()

/**
 * Kinetic Dark theme (dark only, planning/05-design-system.md). Provides [AppTheme.colors] and
 * [AppTheme.typography], and a consistent [MaterialTheme] so stock M3 components match.
 *
 * @param typeScale [TypeScale.Tablet] for wide windows; pick it with [windowWidthClassFor] (`.typeScale`).
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
            colorScheme = KineticDarkColorScheme,
            typography = materialTypography,
            shapes = KineticMaterialShapes,
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
