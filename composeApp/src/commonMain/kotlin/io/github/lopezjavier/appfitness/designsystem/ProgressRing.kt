package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_cd
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_excess
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_excess_spoken
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_of_target
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_remaining
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_remaining_spoken
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_state
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

/** Colours of [ProgressRing]. */
@Immutable
data class ProgressRingColors(val track: Color, val progress: Color, val status: Color)

/** Pure snapshot of the ring for a consumed/target pair. */
@Immutable
data class ProgressRingState(
    /** Degrees, 0f..360f. */
    val sweepAngle: Float,
    /** `consumed > target`; exactly on target is the normal state. */
    val isOverTarget: Boolean,
    /** `target - consumed`; negative when over target. */
    val remaining: Int,
)

/** Geometry, colours and pure logic of [ProgressRing] (04 §2, 05 §6). */
object ProgressRingDefaults {
    /** Inner diameter 152 dp leaves room for `displayHero` at 130% font scale. */
    val Size: Dp = 176.dp
    val StrokeWidth: Dp = 12.dp
    const val START_ANGLE = -90f
    const val FULL_SWEEP = 360f
    internal const val ANIMATION_MILLIS = 700

    /** Negative consumed is treated as 0; the sweep is capped at a full turn. */
    fun state(consumed: Int, target: Int): ProgressRingState {
        require(target > 0) { "target must be positive but was $target" }
        val safeConsumed = consumed.coerceAtLeast(0)
        val sweep = min(safeConsumed.toFloat() / target * FULL_SWEEP, FULL_SWEEP)
        return ProgressRingState(sweep, safeConsumed > target, target - safeConsumed)
    }

    /** Amber replaces lime when over target, together with the "en exceso" text (not colour-only). */
    fun colors(isOverTarget: Boolean, appColors: AppColors): ProgressRingColors {
        val accent = if (isOverTarget) appColors.energyAmber else appColors.neonLime
        return ProgressRingColors(track = appColors.surfaceOverlay, progress = accent, status = accent)
    }

    fun valueStyle(typography: AppTypography): TextStyle = typography.displayHero

    fun captionStyle(typography: AppTypography): TextStyle = typography.labelTechnical

    fun statusStyle(typography: AppTypography): TextStyle = typography.labelTechnical
}

/**
 * Daily calorie ring: consumed value and target in the centre, remaining or excess in a pill below
 * (it wraps at large font scales instead of clipping). The whole ring is one accessibility node
 * carrying the caller's [modifier] (and its testTag).
 */
@Composable
fun ProgressRing(consumedKcal: Int, targetKcal: Int, modifier: Modifier = Modifier) {
    val state = ProgressRingDefaults.state(consumedKcal, targetKcal)
    val colors = ProgressRingDefaults.colors(state.isOverTarget, AppTheme.colors)
    val sweep by animateFloatAsState(
        targetValue = state.sweepAngle,
        animationSpec = tween(ProgressRingDefaults.ANIMATION_MILLIS),
        label = "progress_ring_sweep",
    )
    val consumedText = AppNumberFormat.integer(consumedKcal.coerceAtLeast(0))
    val targetText = AppNumberFormat.integer(targetKcal)
    val statusText =
        if (state.isOverTarget) {
            stringResource(Res.string.progress_ring_excess, AppNumberFormat.integer(-state.remaining))
        } else {
            stringResource(Res.string.progress_ring_remaining, AppNumberFormat.integer(state.remaining))
        }
    val spokenStatus =
        if (state.isOverTarget) {
            stringResource(Res.string.progress_ring_excess_spoken, AppNumberFormat.integer(-state.remaining))
        } else {
            stringResource(Res.string.progress_ring_remaining_spoken, AppNumberFormat.integer(state.remaining))
        }
    val description = stringResource(Res.string.progress_ring_cd)
    val stateDescriptionText =
        stringResource(Res.string.progress_ring_state, consumedText, targetText, spokenStatus)
    val fraction = min(consumedKcal.coerceAtLeast(0).toFloat() / targetKcal, 1f)
    Column(
        modifier =
        modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            stateDescription = stateDescriptionText
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        RingBody(sweep, colors, consumedText, targetText)
        StatusPill(statusText, colors.status)
    }
}

@Composable
private fun RingBody(sweep: Float, colors: ProgressRingColors, consumedText: String, targetText: String) {
    val strokeWidth = ProgressRingDefaults.StrokeWidth
    Box(Modifier.size(ProgressRingDefaults.Size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(ProgressRingDefaults.Size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(size.width - strokeWidth.toPx(), size.height - strokeWidth.toPx())
            drawArc(
                color = colors.track,
                startAngle = 0f,
                sweepAngle = ProgressRingDefaults.FULL_SWEEP,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            if (sweep > 0f) {
                drawArc(
                    color = colors.progress,
                    startAngle = ProgressRingDefaults.START_ANGLE,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        Column(
            modifier = Modifier.clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = consumedText,
                style = ProgressRingDefaults.valueStyle(AppTheme.typography),
                color = AppTheme.colors.textPrimary,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = stringResource(Res.string.progress_ring_of_target, targetText),
                style = ProgressRingDefaults.captionStyle(AppTheme.typography),
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    PillContainer(modifier = Modifier.clearAndSetSemantics {}) {
        Text(
            text = text,
            style = ProgressRingDefaults.statusStyle(AppTheme.typography),
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun ProgressRingPreview() {
    AppTheme {
        Column(
            Modifier.background(AppTheme.colors.surfaceRaised).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            ProgressRing(consumedKcal = 1840, targetKcal = 2400)
            ProgressRing(consumedKcal = 2520, targetKcal = 2400)
        }
    }
}
