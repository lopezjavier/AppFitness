@file:Suppress("MatchingDeclarationName") // The file holds the components and their Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource

/** Geometry of [DiaryAnalysingGraphic]. */
object DiaryAnalysingDefaults {
    val Size: Dp = 176.dp
    val Medallion: Dp = 72.dp
    val IconSize: Dp = 36.dp
    val RingStroke: Dp = 2.dp
    const val PULSE_MILLIS = 1800
    const val RINGS = 3
}

/**
 * The branded "analysing" illustration: a lit medallion with [icon] at the centre and three rings that expand and fade
 * in turn. The rings loop only while [LocalAnimationsEnabled] is true; otherwise they rest as a static halo.
 * Decorative: the caller writes the message.
 */
@Composable
fun DiaryAnalysingGraphic(
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.neonLime,
) {
    val animate = LocalAnimationsEnabled.current
    val phase: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "diary_analysing").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(DiaryAnalysingDefaults.PULSE_MILLIS * 2, easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "diary_analysing_phase",
        )
    } else {
        remember { mutableStateOf(STATIC_PHASE) }
    }
    Box(modifier.size(DiaryAnalysingDefaults.Size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(DiaryAnalysingDefaults.Size).clearAndSetSemantics {}) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val inner = DiaryAnalysingDefaults.Medallion.toPx() / 2f
            val outer = size.minDimension / 2f
            drawRadialGlow(color, centre, outer, GLOW_ALPHA)
            for (i in 0 until DiaryAnalysingDefaults.RINGS) {
                val t = (phase.value + i / DiaryAnalysingDefaults.RINGS.toFloat()) % 1f
                val radius = inner + (outer - inner) * t
                drawCircle(
                    color = color.copy(alpha = (1f - t) * RING_ALPHA),
                    radius = radius,
                    center = centre,
                    style = Stroke(DiaryAnalysingDefaults.RingStroke.toPx()),
                )
            }
        }
        IconBadge(
            icon = icon,
            tint = color,
            size = DiaryAnalysingDefaults.Medallion,
            iconSize = DiaryAnalysingDefaults.IconSize,
            shape = AppShapes.full,
        )
    }
}

private const val STATIC_PHASE = 0.3f
private const val GLOW_ALPHA = 0.18f
private const val RING_ALPHA = 0.55f
