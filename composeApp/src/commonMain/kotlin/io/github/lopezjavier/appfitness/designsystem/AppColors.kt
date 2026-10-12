package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Kinetic Dark color tokens (planning/05-design-system.md §2.1-2.3, §4).
 *
 * Read them through [AppTheme.colors]. Field names match the token names in 05.
 */
@Suppress("LongParameterList") // One parameter per design token.
@Immutable
class AppColors(
    // §2.1 Surfaces and text
    val surfaceBase: Color,
    val surfaceRaised: Color,
    val surfaceOverlay: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Supporting data only (about 3.3:1): never the only cue (05 §7). */
    val ghostData: Color,
    val scrim: Color,
    // §2.2 Accents
    val neonLime: Color,
    val onNeonLime: Color,
    val athleticCyan: Color,
    val energyAmber: Color,
    val electricGreen: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    // §2.3 Macros
    val macroProtein: Color,
    val macroCarbs: Color,
    val macroFat: Color,
    // §4 Shadow colors (tokens only; see AppElevation)
    val shadowSheet: Color,
    val glowLime: Color,
)

private val NeonLime = Color(0xFFA3E635)
private val AthleticCyan = Color(0xFF38BDF8)
private val EnergyAmber = Color(0xFFF97316)

internal val KineticDarkColors =
    AppColors(
        surfaceBase = Color(0xFF0B0F14),
        surfaceRaised = Color(0xFF121820),
        surfaceOverlay = Color(0xFF1A222D),
        borderSubtle = Color.White.copy(alpha = 0.08f),
        borderStrong = Color.White.copy(alpha = 0.12f),
        divider = Color.White.copy(alpha = 0.04f),
        textPrimary = Color(0xFFE0E2EA),
        textSecondary = Color(0xFFC2CAB0),
        ghostData = Color.White.copy(alpha = 0.36f),
        scrim = Color.Black.copy(alpha = 0.65f),
        neonLime = NeonLime,
        onNeonLime = Color(0xFF0B0F14),
        athleticCyan = AthleticCyan,
        energyAmber = EnergyAmber,
        electricGreen = Color(0xFF22C55E),
        error = Color(0xFFFFB4AB),
        // M3 baseline red family, set explicitly so a library default change cannot alter it.
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        macroProtein = NeonLime,
        macroCarbs = AthleticCyan,
        macroFat = EnergyAmber,
        shadowSheet = Color.Black.copy(alpha = 0.70f),
        glowLime = NeonLime.copy(alpha = 0.15f),
    )

internal val LocalAppColors = staticCompositionLocalOf { KineticDarkColors }
