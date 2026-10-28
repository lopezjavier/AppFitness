package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel

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
    /** Bottom sheet drag handle (decorative). */
    val sheetHandle: Color,
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
) {
    /**
     * §2.5 RPE ramp (green = easiest, red = hardest) for a whole value 6 to 10; only used by the RPE picker. The
     * nine ramp stops are spread over the five values, interpolating between neighbours (no extra tokens).
     */
    fun rpeScale(value: Double): Color {
        val position = ((value - RPE_RAMP_MIN) / (RPE_RAMP_MAX - RPE_RAMP_MIN)).coerceIn(0.0, 1.0) * RpeRamp.lastIndex
        val lower = position.toInt().coerceAtMost(RpeRamp.lastIndex - 1)
        return lerp(RpeRamp[lower], RpeRamp[lower + 1], (position - lower).toFloat())
    }

    /**
     * §2.5 Confidence ramp for a whole value 1 to 10 (clamped): 1 = red end, 10 = green end of the RPE ramp, reversed.
     * Reinforcement only: the value is always also written as text.
     */
    fun confidenceScale(value: Int): Color {
        val position = (value.coerceIn(CONFIDENCE_MIN, CONFIDENCE_MAX) - CONFIDENCE_MIN).toFloat() /
            (CONFIDENCE_MAX - CONFIDENCE_MIN) * RpeRamp.lastIndex
        val lower = position.toInt().coerceAtMost(RpeRamp.lastIndex - 1)
        return lerp(RpeRamp[RpeRamp.lastIndex - lower], RpeRamp[RpeRamp.lastIndex - lower - 1], position - lower)
    }

    /**
     * §2.6 Discomfort scale: five ordered stops from greenish yellow to red, taken from the RPE ramp without its lime
     * (the primary colour). Marker bar / dot only; the label is always text in `textPrimary`.
     */
    fun discomfortScale(level: DiscomfortLevel): Color = RpeRamp[DiscomfortRampIndexes[level.value - 1]]
}

/** Indexes of [RpeRamp] used by the five discomfort levels. */
private val DiscomfortRampIndexes = listOf(3, 4, 5, 6, 8)

private const val CONFIDENCE_MIN = 1
private const val CONFIDENCE_MAX = 10

private const val RPE_RAMP_MIN = 6.0
private const val RPE_RAMP_MAX = 10.0

/** Ramp stops from RPE 6 (green) to RPE 10 (red) (05 §2.5). */
private val RpeRamp = listOf(
    Color(0xFF22C55E),
    Color(0xFF65D04A),
    Color(0xFFA3E635),
    Color(0xFFD4D93A),
    Color(0xFFFACC15),
    Color(0xFFFB923C),
    Color(0xFFEF4444),
    Color(0xFFE5383B),
    Color(0xFFDC2626),
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
        sheetHandle = Color.White.copy(alpha = 0.20f),
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
