package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry of [TrainerBeforeAfter] and [TrainerTintedRow]. */
object TrainerGraphicsDefaults {
    val BarHeight: Dp = 8.dp
    const val MIN_FRACTION = 0.04f
    const val BEFORE_ALPHA = 0.55f
}

/**
 * Two stacked bars that compare a figure [before] and [after] on the same scale: the first one dim, the second one
 * lit with the ramp of [color] (a drop is drawn in [dropColor]). Each bar carries its own [beforeLabel] / [afterLabel]
 * at its end. Decorative: the caller writes the figures and the change as text elsewhere in the row.
 */
@Composable
fun TrainerBeforeAfter(
    before: Double,
    after: Double,
    beforeLabel: String,
    afterLabel: String,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.neonLime,
    dropColor: Color = AppTheme.colors.energyAmber,
) {
    val reveal = rememberFillProgress()
    val top = maxOf(before, after).takeIf { it > 0.0 } ?: 1.0
    val tone = if (after < before) dropColor else color
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        BeforeAfterBar((before / top).toFloat(), beforeLabel, AppTheme.colors.textTertiary, true, reveal.value)
        BeforeAfterBar((after / top).toFloat(), afterLabel, tone, false, reveal.value)
    }
}

@Composable
private fun BeforeAfterBar(fraction: Float, label: String, tone: Color, dim: Boolean, reveal: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(TrainerGraphicsDefaults.BarHeight)
                .clip(AppShapes.full)
                .background(AppTheme.colors.surfaceOverlay),
        ) {
            val width = (fraction * reveal).coerceIn(TrainerGraphicsDefaults.MIN_FRACTION, 1f)
            Box(
                Modifier
                    .fillMaxWidth(width)
                    .height(TrainerGraphicsDefaults.BarHeight)
                    .clip(AppShapes.full)
                    .background(
                        if (dim) {
                            AppPaint.horizontal(tone.copy(alpha = TrainerGraphicsDefaults.BEFORE_ALPHA))
                        } else {
                            AppPaint.horizontal(tone)
                        },
                    ),
            )
        }
        Text(
            text = label,
            style = AppTheme.typography.labelNumeric,
            color = if (dim) AppTheme.colors.textSecondary else AppTheme.colors.textPrimary,
        )
    }
}

/**
 * A choice card: a tinted surface with a lit hairline in [accent], an [IconBadge]-sized [leading] mark, then the
 * [content]. Used for the phase decision and other "pick one" blocks. Not clickable by itself.
 */
@Composable
fun TrainerTintedRow(
    accent: Color,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(AppShapes.md)
            .background(AppPaint.tintSurface(accent))
            .border(AppBorder.thin, AppPaint.accentBorder(accent), AppShapes.md)
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Box(Modifier.weight(1f)) { content() }
    }
}
