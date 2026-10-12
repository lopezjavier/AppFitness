package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview

private const val SHIMMER_PERIOD_MILLIS = 1200

/**
 * Decorative loading placeholder with a left-to-right shimmer. It is hidden from accessibility.
 * Compose honors the system "Remove animations" setting, which freezes the shimmer on one frame.
 */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = AppShapes.base) {
    val progress by rememberInfiniteTransition(label = "skeleton_shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_PERIOD_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "skeleton_shimmer_progress",
    )
    val base = AppTheme.colors.surfaceRaised
    val highlight = AppTheme.colors.surfaceOverlay
    Box(
        modifier =
        modifier
            .clip(shape)
            .drawWithCache {
                val width = size.width
                onDrawBehind {
                    val startX = shimmerOffsetX(progress, width)
                    drawRect(
                        brush =
                        Brush.linearGradient(
                            colors = listOf(base, highlight, base),
                            start = Offset(startX, 0f),
                            end = Offset(startX + width, 0f),
                        ),
                    )
                }
            }
            .clearAndSetSemantics {},
    )
}

/** X offset of the shimmer gradient: sweeps from one width before the box to one width after. */
internal fun shimmerOffsetX(progress: Float, widthPx: Float): Float = -widthPx + progress.coerceIn(0f, 1f) * 2 * widthPx

@Preview
@Composable
private fun SkeletonBoxPreview() {
    AppTheme {
        SkeletonBox(Modifier.fillMaxWidth().height(WipPlaceholderDefaults.SkeletonHeight), AppShapes.lg)
    }
}
