@file:Suppress("MatchingDeclarationName") // The file holds the components and their Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Geometry of the Inicio graphics. */
object StartDefaults {
    val SegmentHeight: Dp = 6.dp
    val ShimmerWidth: Dp = 132.dp
    val ShimmerHeight: Dp = 4.dp
    val IntensityBarWidth: Dp = 5.dp
    val IntensityBarStep: Dp = 5.dp
    val IntensityBarBase: Dp = 8.dp
    const val STAGE_ASPECT = 1.5f
    internal const val ORBIT_MILLIS = 90_000
    internal const val SHIMMER_MILLIS = 1_600
}

/**
 * The hero stage behind the brand: soft lime and cyan lights, thin concentric rings and two small lights orbiting on
 * them (very slow; still when animations are off). [content] sits centred on top. Decorative.
 */
@Composable
fun StartHeroStage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = AppTheme.colors
    val angle: State<Float> = if (LocalAnimationsEnabled.current) {
        rememberInfiniteTransition(label = "orbit").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(StartDefaults.ORBIT_MILLIS, easing = LinearEasing)),
            label = "orbit_angle",
        )
    } else {
        remember { mutableFloatStateOf(40f) }
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(StartDefaults.STAGE_ASPECT)
            .drawBehind {
                val centre = Offset(size.width / 2f, size.height / 2f)
                val reach = min(size.width, size.height) / 2f
                drawRadialGlow(colors.neonLime, centre, size.width * 0.62f, 0.20f)
                drawRadialGlow(
                    colors.athleticCyan,
                    Offset(centre.x + reach * 0.9f, centre.y - reach * 0.5f),
                    reach * 1.3f,
                    0.14f,
                )
                val radii = listOf(0.62f, 0.82f, 1.0f)
                val alphas = listOf(0.12f, 0.09f, 0.06f)
                radii.forEachIndexed { i, r ->
                    val ringColor = if (i == 1) colors.neonLime else Color.White
                    drawCircle(ringColor.copy(alpha = alphas[i]), reach * r, centre, style = Stroke(1.dp.toPx()))
                }
                listOf(
                    Triple(0.82f, angle.value, colors.neonLime),
                    Triple(1.0f, 150f - angle.value * 0.7f, colors.athleticCyan),
                ).forEach { (r, deg, color) ->
                    val rad = deg * PI.toFloat() / 180f
                    val p = Offset(centre.x + cos(rad) * reach * r, centre.y + sin(rad) * reach * r)
                    drawRadialGlow(color, p, 14.dp.toPx(), 0.7f)
                    drawCircle(color, 3.dp.toPx(), p)
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Lit segments of a step flow: the first [step] of [total] are lime, the rest ghost tracks. Decorative: the caller
 * announces the step in words.
 */
@Composable
fun StartStepProgress(step: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(total) { index ->
            val lit = index < step
            Box(
                Modifier.weight(1f).height(StartDefaults.SegmentHeight).clip(AppShapes.full)
                    .background(if (lit) AppPaint.horizontal(colors.neonLime) else AppPaint.tileSurface(colors)),
            )
        }
    }
}

/** [level] of [total] bars growing in height, lit in [color] up to the level; the rest are ghost bars. Decorative. */
@Composable
fun StartIntensityBars(level: Int, color: Color, modifier: Modifier = Modifier, total: Int = 5) {
    Row(
        modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs / 2),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(total) { index ->
            Box(
                Modifier.width(StartDefaults.IntensityBarWidth)
                    .height(StartDefaults.IntensityBarBase + StartDefaults.IntensityBarStep * index)
                    .clip(AppShapes.full)
                    .background(
                        if (index < level) AppPaint.vertical(color) else AppPaint.tileSurface(AppTheme.colors),
                    ),
            )
        }
    }
}

/** One value proposition: an [IconBadge], a [title] and a short [text]. One accessibility node. */
@Composable
fun StartValueProp(icon: DrawableResource, tint: Color, title: String, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint)
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            Text(text, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
    }
}

/** A short indeterminate bar for the splash: a lime light sweeping a ghost track (a still fill without motion). */
@Composable
fun StartShimmerBar(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val phase: State<Float> = if (LocalAnimationsEnabled.current) {
        rememberInfiniteTransition(label = "shimmer").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(StartDefaults.SHIMMER_MILLIS, easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "shimmer_phase",
        )
    } else {
        remember { mutableFloatStateOf(0.3f) }
    }
    Box(
        modifier.width(StartDefaults.ShimmerWidth).height(StartDefaults.ShimmerHeight).clip(AppShapes.full)
            .background(colors.surfaceOverlay).clearAndSetSemantics {}
            .drawBehind {
                val w = size.width * 0.4f
                val x = -w + (size.width + w) * phase.value
                drawRect(AppPaint.horizontal(colors.neonLime), Offset(x, 0f), Size(w, size.height))
            },
    )
}
