package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Material 3 dark scheme built from the Kinetic Dark tokens.
 *
 * Covers the mapping of planning/05-design-system.md §2.4 and sets every other role explicitly,
 * so no M3 baseline (purple) value reaches stock components.
 */
internal fun kineticDarkColorScheme(colors: AppColors = KineticDarkColors): ColorScheme = darkColorScheme(
    primary = colors.neonLime,
    onPrimary = colors.onNeonLime,
    primaryContainer = colors.neonLime,
    onPrimaryContainer = colors.onNeonLime,
    inversePrimary = colors.neonLime,
    secondary = colors.athleticCyan,
    onSecondary = colors.onNeonLime,
    secondaryContainer = colors.surfaceOverlay,
    onSecondaryContainer = colors.textPrimary,
    tertiary = colors.energyAmber,
    onTertiary = colors.onNeonLime,
    tertiaryContainer = colors.surfaceOverlay,
    onTertiaryContainer = colors.textPrimary,
    background = colors.surfaceBase,
    onBackground = colors.textPrimary,
    surface = colors.surfaceBase,
    onSurface = colors.textPrimary,
    surfaceVariant = colors.surfaceOverlay,
    onSurfaceVariant = colors.textSecondary,
    // No tonal tint: depth comes from the surface layers (05 §1.2).
    surfaceTint = Color.Transparent,
    // Dark snackbar: lime on textPrimary would only reach 1.17:1.
    inverseSurface = colors.surfaceOverlay,
    inverseOnSurface = colors.textPrimary,
    error = colors.error,
    onError = colors.onError,
    errorContainer = colors.errorContainer,
    onErrorContainer = colors.onErrorContainer,
    outline = colors.borderStrong,
    outlineVariant = colors.borderSubtle,
    scrim = colors.scrim,
    surfaceBright = colors.surfaceOverlay,
    surfaceContainer = colors.surfaceOverlay,
    surfaceContainerHigh = colors.surfaceOverlay,
    surfaceContainerHighest = colors.surfaceOverlay,
    surfaceContainerLow = colors.surfaceRaised,
    surfaceContainerLowest = colors.surfaceBase,
    surfaceDim = colors.surfaceBase,
    primaryFixed = colors.neonLime,
    primaryFixedDim = colors.neonLime,
    onPrimaryFixed = colors.onNeonLime,
    onPrimaryFixedVariant = colors.onNeonLime,
    secondaryFixed = colors.athleticCyan,
    secondaryFixedDim = colors.athleticCyan,
    onSecondaryFixed = colors.onNeonLime,
    onSecondaryFixedVariant = colors.onNeonLime,
    tertiaryFixed = colors.energyAmber,
    tertiaryFixedDim = colors.energyAmber,
    onTertiaryFixed = colors.onNeonLime,
    onTertiaryFixedVariant = colors.onNeonLime,
)
