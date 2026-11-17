package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp

/** Colours of [AppCard]. */
@Immutable
data class AppCardColors(val container: Color, val border: Color)

/** Shape, spacing and colours of [AppCard] (planning/05-design-system.md §6). */
object AppCardDefaults {
    val Shape = AppShapes.lg
    val Padding: Dp = Spacing.cardPadding
    val BorderWidth: Dp = AppBorder.thin

    /** Border of a [highlighted] card (training domain: cyan, 1.5 dp). */
    val HighlightBorderWidth: Dp = AppBorder.focus

    /** Reach of the corner light of a glowing card, as a share of its width. */
    const val GLOW_REACH = 0.85f

    fun colors(appColors: AppColors, highlighted: Boolean = false, selected: Boolean = false): AppCardColors =
        AppCardColors(
            container = if (selected) appColors.selectedContainer else appColors.surfaceRaised,
            border = when {
                selected -> appColors.neonLime
                highlighted -> appColors.athleticCyan
                else -> appColors.borderSubtle
            },
        )
}

/**
 * Tier-1 card: a `surfaceRaised` surface with a soft top light, a lit 1 dp hairline and a 16 dp radius. It draws no
 * shadow; depth comes from the tonal step, the gradient and the border. [highlighted] swaps the border for a 1.5 dp
 * cyan one (callers add a non-colour cue too). [selected] (R-64) fills with `selectedContainer` and draws a 1 dp lime
 * border; it wins over [highlighted]. [lifted] is the picked-up look of a dragged card: `surfaceOverlay` with a 1 dp
 * `borderControl`. [glow] makes it a hero card: a corner light and a top hairline in that accent (decorative).
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(AppCardDefaults.Padding),
    lifted: Boolean = false,
    glow: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appColors = AppTheme.colors
    val base = AppCardDefaults.colors(appColors, highlighted, selected)
    val colors = if (lifted) AppCardColors(appColors.surfaceOverlay, appColors.borderControl) else base
    val plain = !lifted && !selected
    val borderWidth = if (highlighted && plain) AppCardDefaults.HighlightBorderWidth else AppCardDefaults.BorderWidth
    val container: Brush = if (plain) AppPaint.cardSurface(appColors) else SolidColor(colors.container)
    val border: Brush = when {
        !plain -> SolidColor(colors.border)
        highlighted -> AppPaint.vertical(colors.border)
        glow != null -> AppPaint.accentBorder(glow)
        else -> AppPaint.cardBorder()
    }
    val light = if (plain) glow ?: colors.border.takeIf { highlighted } else null
    Column(
        modifier =
        modifier
            .clip(AppCardDefaults.Shape)
            .background(container)
            .then(if (light != null) Modifier.cardCornerLight(light) else Modifier)
            .border(borderWidth, border, AppCardDefaults.Shape)
            .padding(contentPadding),
        content = content,
    )
}

/** The corner light of a hero card: a radial glow of [accent] from the top end, inside the clipped card. */
private fun Modifier.cardCornerLight(accent: Color): Modifier = drawBehind {
    drawRadialGlow(
        color = accent,
        center = Offset(size.width, 0f),
        radius = size.width * AppCardDefaults.GLOW_REACH,
        alpha = AppGlowDefaults.CARD_GLOW_ALPHA,
    )
}

@Preview
@Composable
private fun AppCardPreview() {
    AppTheme {
        AppCard(Modifier.fillMaxWidth()) {
            Text("Tarjeta", style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            Text("Contenido", style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
    }
}
