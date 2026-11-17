@file:Suppress("MatchingDeclarationName", "MagicNumber", "TooManyFunctions")
// MatchingDeclarationName: the file holds the paint tokens and their draw helpers.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The three stops of an accent: [deep] at the origin of a fill, [base] the token itself, [bright] at its tip. */
@Immutable
data class AccentRamp(val deep: Color, val base: Color, val bright: Color) {
    val stops: List<Color> get() = listOf(deep, base, bright)
}

/**
 * Premium paint of the Kinetic Dark theme: gradients, hairlines and glows derived from the colour tokens. Nothing here
 * adds a new meaning to a colour; it only gives depth to the surfaces and light to the data marks (rings, bars, lines).
 * Glows are layered alpha shapes and radial gradients, never blur, so they render the same on every API level.
 */
object AppPaint {
    /** Hand-tuned ramps of the accent tokens; any other colour gets a computed one. */
    private val Ramps: Map<Color, AccentRamp> = mapOf(
        Color(0xFFA3E635) to AccentRamp(Color(0xFF6FCB2A), Color(0xFFA3E635), Color(0xFFDDF97A)),
        Color(0xFF38BDF8) to AccentRamp(Color(0xFF1C8DEB), Color(0xFF38BDF8), Color(0xFF8FE4FF)),
        Color(0xFFF97316) to AccentRamp(Color(0xFFE5500B), Color(0xFFF97316), Color(0xFFFFB648)),
        Color(0xFFF472B6) to AccentRamp(Color(0xFFDC4A99), Color(0xFFF472B6), Color(0xFFFFAFD9)),
        Color(0xFF22C55E) to AccentRamp(Color(0xFF12A04F), Color(0xFF22C55E), Color(0xFF7DECA2)),
    )

    /** Record and achievement gold: used only by badges and celebration graphics, never as a state colour. */
    val Gold: Color = Color(0xFFFACC15)

    /** Ramp of [color]; the alpha of [color] is kept on every stop. */
    fun ramp(color: Color): AccentRamp {
        val opaque = color.copy(alpha = 1f)
        val ramp = Ramps[opaque] ?: AccentRamp(
            deep = lerp(opaque, Color.Black, 0.24f),
            base = opaque,
            bright = lerp(opaque, Color.White, 0.32f),
        )
        return if (color.alpha >= 1f) {
            ramp
        } else {
            AccentRamp(
                ramp.deep.copy(alpha = color.alpha),
                ramp.base.copy(alpha = color.alpha),
                ramp.bright.copy(alpha = color.alpha),
            )
        }
    }

    /** Fill of a horizontal bar: deep at the start, bright at the tip. */
    fun horizontal(color: Color): Brush = Brush.horizontalGradient(ramp(color).stops)

    /** Fill of a vertical bar or a filled control: bright at the top, deep at the bottom. */
    fun vertical(color: Color): Brush = ramp(color).let { Brush.verticalGradient(listOf(it.bright, it.base, it.deep)) }

    /** Diagonal fill for medallions and filled circles. */
    fun diagonal(color: Color): Brush = ramp(color).let {
        Brush.linearGradient(listOf(it.bright, it.base, it.deep))
    }

    /** Tier-1 card surface: a soft top light over `surfaceRaised`. */
    fun cardSurface(colors: AppColors): Brush = Brush.verticalGradient(
        listOf(
            lerp(colors.surfaceRaised, Color.White, 0.045f),
            colors.surfaceRaised,
            lerp(colors.surfaceRaised, Color.Black, 0.14f),
        ),
    )

    /** Hairline of a card: lit at the top edge, fading down. */
    fun cardBorder(): Brush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.04f)),
    )

    /** Hairline of a card tinted by an [accent] at its top edge. */
    fun accentBorder(accent: Color): Brush = Brush.verticalGradient(
        listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f)),
    )

    /** Tier-2 tile surface over `surfaceOverlay`. */
    fun tileSurface(colors: AppColors): Brush = Brush.verticalGradient(
        listOf(lerp(colors.surfaceOverlay, Color.White, 0.06f), lerp(colors.surfaceOverlay, Color.Black, 0.10f)),
    )

    /** Hairline of a tile. */
    fun tileBorder(): Brush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f)),
    )

    /** Surface of a neutral control (secondary button, stepper). */
    fun controlSurface(colors: AppColors): Brush = Brush.verticalGradient(
        listOf(lerp(colors.surfaceControl, Color.White, 0.07f), lerp(colors.surfaceControl, Color.Black, 0.08f)),
    )

    /** Tinted container of an icon badge or a soft accent chip. */
    fun tintSurface(accent: Color): Brush = Brush.verticalGradient(
        listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.10f)),
    )

    /** Area under a line: the accent fading to nothing. */
    fun areaFade(color: Color, topAlpha: Float = 0.30f): Brush = Brush.verticalGradient(
        listOf(color.copy(alpha = topAlpha), color.copy(alpha = topAlpha * 0.35f), color.copy(alpha = 0f)),
    )
}

/** Geometry of the glows drawn by [softGlow] and [drawRadialGlow]. */
object AppGlowDefaults {
    val Radius: Dp = 18.dp
    val OffsetY: Dp = 5.dp
    const val ALPHA = 0.30f
    internal const val LAYERS = 12

    /** Ambient light of a screen backdrop. */
    const val AMBIENT_PRIMARY_ALPHA = 0.085f
    const val AMBIENT_SECONDARY_ALPHA = 0.07f

    /** Corner light of a hero card. */
    const val CARD_GLOW_ALPHA = 0.20f
}

/**
 * A soft glow of [color] behind a rounded rectangle of [cornerRadius]: layered translucent shapes growing by [radius]
 * and shifted down by [offsetY]. Drawn behind the content, so only the falloff around an opaque shape shows.
 */
fun Modifier.softGlow(
    color: Color,
    cornerRadius: Dp,
    radius: Dp = AppGlowDefaults.Radius,
    alpha: Float = AppGlowDefaults.ALPHA,
    offsetY: Dp = AppGlowDefaults.OffsetY,
): Modifier = drawBehind {
    val layers = AppGlowDefaults.LAYERS
    val reach = radius.toPx()
    val dy = offsetY.toPx()
    val corner = cornerRadius.toPx()
    val layerAlpha = alpha / layers
    for (layer in layers downTo 1) {
        val spread = reach * layer / layers
        drawRoundRect(
            color = color.copy(alpha = layerAlpha),
            topLeft = Offset(-spread, -spread + dy),
            size = Size(size.width + spread * 2, size.height + spread * 2),
            cornerRadius = CornerRadius(corner + spread),
        )
    }
}

/** A radial light of [color] at [center] that fades to nothing at [radius]. */
fun DrawScope.drawRadialGlow(color: Color, center: Offset, radius: Float, alpha: Float) {
    if (radius <= 0f || alpha <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.4f), color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * The ambient backdrop of a screen: a [primary] light from the top start and a [secondary] one from the top end, over
 * the base surface the caller already drew. Decorative; cards stay opaque on top of it.
 */
fun Modifier.ambientBackdrop(primary: Color, secondary: Color): Modifier = drawBehind {
    val reach = size.width * 0.95f
    drawRadialGlow(
        primary,
        Offset(size.width * 0.08f, -size.width * 0.12f),
        reach,
        AppGlowDefaults.AMBIENT_PRIMARY_ALPHA,
    )
    drawRadialGlow(
        secondary,
        Offset(size.width * 1.02f, size.width * 0.18f),
        reach * 0.85f,
        AppGlowDefaults.AMBIENT_SECONDARY_ALPHA,
    )
}
