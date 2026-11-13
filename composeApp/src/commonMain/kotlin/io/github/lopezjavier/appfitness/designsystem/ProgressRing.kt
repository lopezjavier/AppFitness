package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_cd
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_excess
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_excess_spoken
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_left
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_left_past
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_met
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_of_target
import io.github.lopezjavier.appfitness.composeapp.resources.progress_ring_state
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor
import kotlin.math.min

private fun roundHalfUp(value: Double): Int = floor(value + 0.5).toInt()

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

    /** The compact hero ring (R-68): 132 dp with a 10 dp stroke, so the first meal card fits above the fold. */
    val CompactHeroSize: Dp = 132.dp
    val CompactHeroStroke: Dp = 10.dp

    /** [PercentRing]: fits a list row's trailing slot. */
    val CompactSize: Dp = 40.dp
    val CompactStroke: Dp = 4.dp
    const val START_ANGLE = -90f
    const val FULL_SWEEP = 360f
    internal const val ANIMATION_MILLIS = 700

    /**
     * Rounds [consumed] half up to whole kcal first, so the shown value, the remaining and the over-target flag
     * always agree (1839.6 shows "1.840" and stays under a 2.400 target).
     */
    fun state(consumed: Double, target: Int): ProgressRingState = state(roundHalfUp(consumed), target)

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

    fun captionStyle(typography: AppTypography): TextStyle = typography.bodyMedium.copy(fontWeight = FontWeight.W600)
}

/**
 * Daily calorie ring: consumed value and "de N kcal" in the centre, and one status pill below (R-67): "Te quedan N
 * kcal", "Objetivo cumplido", "+N kcal en exceso" or, for a [pastDay], "Quedaron N kcal"; it wraps at large font
 * scales instead of clipping. [compact] draws the 132 dp hero ring of a phone (R-68). The whole ring is one accessibility
 * node carrying the caller's [modifier] (and its testTag).
 */
@Composable
fun ProgressRing(
    consumedKcal: Int,
    targetKcal: Int,
    modifier: Modifier = Modifier,
    pastDay: Boolean = false,
    compact: Boolean = false,
) = ProgressRing(consumedKcal.toDouble(), targetKcal, modifier, pastDay, compact)

/** [ProgressRing] for a consumed value with decimals; it is shown rounded half up to whole kcal. */
@Composable
fun ProgressRing(
    consumedKcal: Double,
    targetKcal: Int,
    modifier: Modifier = Modifier,
    pastDay: Boolean = false,
    compact: Boolean = false,
) {
    val wholeKcal = roundHalfUp(consumedKcal)
    val state = ProgressRingDefaults.state(wholeKcal, targetKcal)
    val colors = ProgressRingDefaults.colors(state.isOverTarget, AppTheme.colors)
    val sweep by animateFloatAsState(
        targetValue = state.sweepAngle,
        animationSpec = tween(ProgressRingDefaults.ANIMATION_MILLIS),
        label = "progress_ring_sweep",
    )
    val consumedText = AppNumberFormat.integer(wholeKcal.coerceAtLeast(0))
    val targetText = AppNumberFormat.integer(targetKcal)
    val spokenStatus = statusSentence(state, pastDay)
    val statusText = if (state.isOverTarget) {
        stringResource(Res.string.progress_ring_excess, AppNumberFormat.integer(-state.remaining))
    } else {
        spokenStatus
    }
    val description = stringResource(Res.string.progress_ring_cd)
    val stateDescriptionText = stringResource(Res.string.progress_ring_state, consumedText, targetText, spokenStatus)
    val fraction = min(wholeKcal.coerceAtLeast(0).toFloat() / targetKcal, 1f)
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
        RingBody(sweep, colors, consumedText, targetText, compact)
        StatusPill(statusText, if (state.isOverTarget || state.remaining == 0) colors.status else AppTheme.colors.textSecondary)
    }
}

/**
 * The one sentence that says what is left of the day (R-67, C-23): "Te quedan N kcal" under target, "Objetivo
 * cumplido" on it, "Quedaron N kcal" for a [pastDay] under target; over target it is the excess, spoken in full.
 */
@Composable
private fun statusSentence(state: ProgressRingState, pastDay: Boolean): String = when {
    state.isOverTarget ->
        stringResource(Res.string.progress_ring_excess_spoken, AppNumberFormat.integer(-state.remaining))

    state.remaining == 0 -> stringResource(Res.string.progress_ring_met)

    pastDay -> stringResource(Res.string.progress_ring_left_past, AppNumberFormat.integer(state.remaining))

    else -> stringResource(Res.string.progress_ring_left, AppNumberFormat.integer(state.remaining))
}

/**
 * The real width of the compact ring column (V-15b): the 132 dp ring or, when wider, the status pill, which is not
 * wrapped by the column itself. The diary hero measures its macro labels against what is left beside this width.
 */
@Composable
fun compactRingColumnWidth(consumedKcal: Double, targetKcal: Int, pastDay: Boolean): Dp {
    val state = ProgressRingDefaults.state(roundHalfUp(consumedKcal), targetKcal)
    val text = if (state.isOverTarget) {
        stringResource(Res.string.progress_ring_excess, AppNumberFormat.integer(-state.remaining))
    } else {
        statusSentence(state, pastDay)
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textWidth = measurer.measure(text, ProgressRingDefaults.captionStyle(AppTheme.typography), softWrap = false, maxLines = 1).size.width
    val pillWidth = with(density) { textWidth.toDp() } + PillDefaults.HorizontalPadding * 2
    return maxOf(ProgressRingDefaults.CompactHeroSize, pillWidth)
}

@Composable
private fun RingBody(
    sweep: Float,
    colors: ProgressRingColors,
    consumedText: String,
    targetText: String,
    compact: Boolean,
) {
    val ringSize = if (compact) ProgressRingDefaults.CompactHeroSize else ProgressRingDefaults.Size
    val stroke = if (compact) ProgressRingDefaults.CompactHeroStroke else ProgressRingDefaults.StrokeWidth
    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        RingArc(
            sweep = sweep,
            track = colors.track,
            progress = colors.progress,
            size = ringSize,
            strokeWidth = stroke,
        )
        Column(
            modifier = Modifier.clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = consumedText,
                style = if (compact) AppTheme.typography.headlineMetric else ProgressRingDefaults.valueStyle(AppTheme.typography),
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
            style = ProgressRingDefaults.captionStyle(AppTheme.typography),
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
