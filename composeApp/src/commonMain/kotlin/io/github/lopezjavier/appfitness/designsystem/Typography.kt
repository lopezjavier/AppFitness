package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Type scale: [Tablet] (width >= 600 dp, selected in T-028) only enlarges the hero and metric styles. */
enum class TypeScale { Phone, Tablet }

/** Tabular, lining figures on every style, so numbers never jump (05 §1.7 and §3). */
internal const val NUMERIC_FONT_FEATURES = "tnum, lnum"

/** Kinetic Dark text styles (planning/05-design-system.md §3). Read them through [AppTheme.typography]. */
@Suppress("LongParameterList") // One parameter per text style.
@Immutable
class AppTypography(
    /** Ring kcal, current weight. */
    val displayHero: TextStyle,
    /** Card values, timer. */
    val headlineMetric: TextStyle,
    /** Section and card titles. */
    val titleLarge: TextStyle,
    /** Exercise and food names, buttons. */
    val titleMedium: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    /** KG/REPS inputs. */
    val labelNumeric: TextStyle,
    /**
     * Units and headers (KG, REPS, KCAL, PROT). UPPERCASE cannot live in a [TextStyle]: callers
     * pass `text.uppercase()` and keep the original casing in `contentDescription`.
     */
    val labelTechnical: TextStyle,
)

internal fun appTypography(fontFamily: FontFamily, scale: TypeScale = TypeScale.Phone): AppTypography {
    fun style(size: TextUnit, lineHeight: TextUnit, weight: FontWeight, tracking: TextUnit) = TextStyle(
        fontFamily = fontFamily,
        fontWeight = weight,
        fontSize = size,
        lineHeight = lineHeight,
        letterSpacing = tracking,
        fontFeatureSettings = NUMERIC_FONT_FEATURES,
    )

    val isTablet = scale == TypeScale.Tablet
    val (heroSize, heroLineHeight) = if (isTablet) 48.sp to 52.sp else 36.sp to 40.sp
    val (metricSize, metricLineHeight) = if (isTablet) 32.sp to 36.sp else 26.sp to 30.sp
    return AppTypography(
        displayHero = style(heroSize, heroLineHeight, FontWeight.W800, (-0.02).em),
        headlineMetric = style(metricSize, metricLineHeight, FontWeight.W700, (-0.01).em),
        titleLarge = style(20.sp, 26.sp, FontWeight.W600, (-0.01).em),
        titleMedium = style(16.sp, 22.sp, FontWeight.W600, 0.em),
        bodyLarge = style(16.sp, 24.sp, FontWeight.W400, 0.em),
        bodyMedium = style(14.sp, 20.sp, FontWeight.W400, 0.em),
        bodySmall = style(12.sp, 16.sp, FontWeight.W400, 0.em),
        labelNumeric = style(18.sp, 22.sp, FontWeight.W700, 0.02.em),
        labelTechnical = style(11.sp, 14.sp, FontWeight.W600, 0.06.em),
    )
}

/**
 * Material 3 typography for stock components. The five shared slots use the Kinetic styles; the
 * other ten keep the M3 metrics with [fontFamily] and tabular figures, so Roboto never leaks in.
 */
internal fun AppTypography.toMaterialTypography(fontFamily: FontFamily): Typography {
    val defaults = Typography()

    fun TextStyle.kinetic() = copy(fontFamily = fontFamily, fontFeatureSettings = NUMERIC_FONT_FEATURES)

    return Typography(
        displayLarge = defaults.displayLarge.kinetic(),
        displayMedium = defaults.displayMedium.kinetic(),
        displaySmall = defaults.displaySmall.kinetic(),
        headlineLarge = defaults.headlineLarge.kinetic(),
        headlineMedium = defaults.headlineMedium.kinetic(),
        headlineSmall = defaults.headlineSmall.kinetic(),
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        titleSmall = defaults.titleSmall.kinetic(),
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        bodySmall = bodySmall,
        labelLarge = defaults.labelLarge.kinetic(),
        labelMedium = defaults.labelMedium.kinetic(),
        labelSmall = defaults.labelSmall.kinetic(),
    )
}

internal val LocalAppTypography = staticCompositionLocalOf { appTypography(FontFamily.Default) }
