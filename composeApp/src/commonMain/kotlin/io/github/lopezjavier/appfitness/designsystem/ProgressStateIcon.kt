@file:Suppress("MagicNumber")
// MagicNumber: paint proportions of one small mark.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_downward
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_upward
import io.github.lopezjavier.appfitness.composeapp.resources.ic_pause
import io.github.lopezjavier.appfitness.composeapp.resources.ic_remove
import io.github.lopezjavier.appfitness.composeapp.resources.ic_trending_flat
import org.jetbrains.compose.resources.painterResource

/**
 * How one exercise moved against its baseline (weekly report, `07` §3 (1)). Each glyph has its own shape, so the state
 * never depends on colour: up = filled disc and arrow up, same = ring and dash, down = grey disc and arrow down,
 * stalled = dashed ring and a flat trend line, paused = tinted disc and the pause bars, uncompared = a dotted ring.
 * No glyph uses the error colour: a lift that did not go up is information, not an alarm.
 */
enum class ProgressGlyph { Up, Same, Down, Stalled, Paused, Uncompared }

/** Geometry of [ProgressStateIcon]. */
object ProgressStateIconDefaults {
    val Size: Dp = 32.dp
    val SmallSize: Dp = 24.dp
    val RingStroke: Dp = 1.5.dp
    val Dash: Dp = 3.dp
    const val ICON_SHARE = 0.56f
}

/**
 * The state of one exercise as a small round glyph (see [ProgressGlyph]). [contentDescription] is the spoken state
 * ("Press banca: sube"); null leaves it decorative for a caller that names the state in text beside it.
 */
@Composable
fun ProgressStateIcon(
    glyph: ProgressGlyph,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = ProgressStateIconDefaults.Size,
) {
    val colors = AppTheme.colors
    val look = glyphLook(glyph, colors)
    val stroke = ProgressStateIconDefaults.RingStroke
    val dash = ProgressStateIconDefaults.Dash
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics {}
    }
    Box(
        modifier
            .size(size)
            .then(semanticsModifier)
            .drawBehind {
                val radius = (this.size.minDimension - stroke.toPx()) / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                if (look.fill != Color.Unspecified) drawCircle(look.fill, radius, center)
                drawCircle(
                    color = look.ring,
                    radius = radius,
                    center = center,
                    style = Stroke(
                        width = stroke.toPx(),
                        pathEffect = if (look.dashed) {
                            PathEffect.dashPathEffect(
                                floatArrayOf(dash.toPx(), dash.toPx()),
                            )
                        } else {
                            null
                        },
                    ),
                )
                if (glyph == Uncompared) drawDot(look.ring, radius)
            },
        contentAlignment = Alignment.Center,
    ) {
        look.icon?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                tint = look.mark,
                modifier = Modifier.size(size * ProgressStateIconDefaults.ICON_SHARE),
            )
        }
    }
}

private val Uncompared = ProgressGlyph.Uncompared

private fun DrawScope.drawDot(color: Color, radius: Float) {
    drawCircle(color, radius * 0.18f, Offset(size.width / 2f, size.height / 2f))
}

private class GlyphLook(
    val icon: org.jetbrains.compose.resources.DrawableResource?,
    val mark: Color,
    val ring: Color,
    val fill: Color = Color.Unspecified,
    val dashed: Boolean = false,
)

private fun glyphLook(glyph: ProgressGlyph, colors: AppColors): GlyphLook = when (glyph) {
    ProgressGlyph.Up -> GlyphLook(
        Res.drawable.ic_arrow_upward,
        AppPaint.ramp(colors.electricGreen).bright,
        colors.electricGreen.copy(alpha = 0.7f),
        colors.electricGreen.copy(alpha = 0.18f),
    )

    ProgressGlyph.Same -> GlyphLook(
        Res.drawable.ic_remove,
        colors.textSecondary,
        colors.textSecondary.copy(alpha = 0.7f),
    )

    ProgressGlyph.Down -> GlyphLook(
        Res.drawable.ic_arrow_downward,
        colors.textSecondary,
        colors.textTertiary,
        colors.surfaceControl,
    )

    ProgressGlyph.Stalled -> GlyphLook(
        Res.drawable.ic_trending_flat,
        colors.textSecondary,
        colors.textSecondary.copy(alpha = 0.8f),
        dashed = true,
    )

    ProgressGlyph.Paused -> GlyphLook(
        Res.drawable.ic_pause,
        AppPaint.ramp(colors.athleticCyan).bright,
        colors.athleticCyan.copy(alpha = 0.7f),
        colors.athleticCyan.copy(alpha = 0.16f),
    )

    ProgressGlyph.Uncompared -> GlyphLook(null, colors.ghostData, colors.ghostData, dashed = true)
}

@Preview
@Composable
private fun ProgressStateIconPreview() {
    AppTheme {
        Row(Modifier.padding(Spacing.lg)) {
            ProgressGlyph.entries.forEach { ProgressStateIcon(it, Modifier.padding(end = Spacing.sm)) }
        }
    }
}
