@file:Suppress("MatchingDeclarationName") // The file holds the ring, its tile and their Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where a goal stands against its target (05 §6.0e, R-104). The ring colour and the status chip both come from it,
 * and the chip text always says it too, so colour is never the only cue (R-41).
 */
enum class GoalProgressState {
    /** Below the pace the period needs by now: `energyAmber`, caution glyph. */
    Behind,

    /** Below the target but at or above the pace: `athleticCyan`. */
    OnTrack,

    /** The target is reached: `electricGreen`, favourable glyph. */
    Done,

    /** Clearly past the target: `electricGreen` plus the inner "second lap" arc. */
    Over,

    /** Nothing to judge yet (no data, the period has not started): `textTertiary`, no pace tick. */
    Idle,
}

/** Geometry and colours of [GoalRing] and [GoalRingTile]. */
object GoalRingDefaults {
    /** At font scale 1.0; the ring grows with the font scale (up to 1.5x) so the centre value never clips. */
    val Size: Dp = 72.dp
    val StrokeWidth: Dp = 7.dp

    /** The inner arc of an [GoalProgressState.Over] ring: thinner, 3 dp inside the main stroke. */
    val OverStrokeWidth: Dp = 3.dp
    val OverGap: Dp = 3.dp

    /** The pace tick: a short radial line across the stroke at "where you should be by now". */
    val TickWidth: Dp = 2.dp
    val TickOverhang: Dp = 2.dp

    /** The glyph above the centre value, tinted with the ring's state colour. */
    val IconSize: Dp = 14.dp
    const val MAX_SCALE = 1.5f
    const val FULL_SWEEP = 360f
    const val START_ANGLE = -90f

    fun color(state: GoalProgressState, appColors: AppColors): Color = when (state) {
        GoalProgressState.Behind -> appColors.energyAmber
        GoalProgressState.OnTrack -> appColors.athleticCyan
        GoalProgressState.Done, GoalProgressState.Over -> appColors.electricGreen
        GoalProgressState.Idle -> appColors.textTertiary
    }

    /** The chip tone that matches the ring: Caution, Info, Good or Neutral (glyphs as `StatusChip`). */
    fun tone(state: GoalProgressState): StatusTone = when (state) {
        GoalProgressState.Behind -> StatusTone.Caution
        GoalProgressState.OnTrack -> StatusTone.Info
        GoalProgressState.Done, GoalProgressState.Over -> StatusTone.Good
        GoalProgressState.Idle -> StatusTone.Neutral
    }

    /** The ring size at the current font scale. */
    @Composable
    fun scaledSize(): Dp = Size * LocalDensity.current.fontScale.coerceIn(1f, MAX_SCALE)
}

/**
 * A goal ring: a `trackInactive` circle, an arc of [fraction] (done / target, clamped to one lap) coloured by
 * [state], an inner thinner arc for the part past the target when [state] is Over (the "second lap"), and, while the
 * goal is still open (Behind or OnTrack), a tick at [paceFraction]: where the period's pace should be by now. [value]
 * sits in the centre (the exact figure; the unit lives in the caller's label). The arc fills once on first
 * composition (R-97). One accessibility node: [description] is the spoken sentence (label, figure, target, state).
 */
@Composable
fun GoalRing(
    fraction: Float,
    state: GoalProgressState,
    value: String,
    description: String,
    modifier: Modifier = Modifier,
    paceFraction: Float? = null,
    icon: DrawableResource? = null,
    staggerIndex: Int = 0,
    testTag: String = "goal_ring",
) {
    val ringSize = GoalRingDefaults.scaledSize()
    val colors = AppTheme.colors
    val arcColor = GoalRingDefaults.color(state, colors)
    val fill = rememberFillProgress(staggerIndex)
    val safe = fraction.coerceAtLeast(0f)
    val tickColor = colors.textPrimary
    val tick = paceFraction?.takeIf {
        it > 0f && it < 1f && (state == GoalProgressState.Behind || state == GoalProgressState.OnTrack)
    }
    Box(
        modifier
            .size(ringSize)
            .testTag(testTag)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(safe.coerceAtMost(1f), 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(ringSize)) {
            val progress = fill.value
            val stroke = GoalRingDefaults.StrokeWidth.toPx()
            drawRing(stroke, 0f, colors.trackInactive, GoalRingDefaults.FULL_SWEEP)
            val main = safe.coerceAtMost(1f) * progress
            if (main > 0f) drawRing(stroke, 0f, arcColor, main * GoalRingDefaults.FULL_SWEEP)
            if (state == GoalProgressState.Over && safe > 1f) {
                val over = (safe - 1f).coerceAtMost(1f) * progress
                val inset = stroke + GoalRingDefaults.OverGap.toPx()
                drawRing(GoalRingDefaults.OverStrokeWidth.toPx(), inset, arcColor, over * GoalRingDefaults.FULL_SWEEP)
            }
            if (tick != null) drawPaceTick(tick, stroke, tickColor)
        }
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
            icon?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    tint = arcColor.takeIf { state != GoalProgressState.Idle } ?: colors.textSecondary,
                    modifier = Modifier.size(GoalRingDefaults.IconSize),
                )
            }
            Text(
                text = value,
                style = AppTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
                color = colors.textPrimary,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** An arc of [sweep] degrees from the top, [inset] px inside the main stroke's outer edge. */
private fun DrawScope.drawRing(strokePx: Float, inset: Float, color: Color, sweep: Float) {
    val edge = inset + strokePx / 2
    drawArc(
        color = color,
        startAngle = GoalRingDefaults.START_ANGLE,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(edge, edge),
        size = Size(size.width - edge * 2, size.height - edge * 2),
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
    )
}

private const val HALF = 2f
private const val DEGREES_HALF_TURN = 180f

/** The pace tick: a radial line across the stroke at [fraction] of the lap, overhanging it a little on both sides. */
private fun DrawScope.drawPaceTick(fraction: Float, strokePx: Float, color: Color) {
    val angle = (GoalRingDefaults.START_ANGLE + fraction * GoalRingDefaults.FULL_SWEEP) * PI.toFloat() /
        DEGREES_HALF_TURN
    val radius = size.minDimension / HALF - strokePx / HALF
    val reach = strokePx / HALF + GoalRingDefaults.TickOverhang.toPx()
    val centre = Offset(size.width / HALF, size.height / HALF)
    val direction = Offset(cos(angle), sin(angle))
    drawLine(
        color = color,
        start = centre + direction * (radius - reach),
        end = centre + direction * (radius + reach),
        strokeWidth = GoalRingDefaults.TickWidth.toPx(),
        cap = StrokeCap.Round,
    )
}

/**
 * One goal of a period as a tile: [GoalRing] with the [icon] and the [value] inside, the [label], the target line
 * ([target], e.g. "de 150 min") and the status line [statusText] in the ring's colour (with the `StatusChip` glyph of
 * its tone when [horizontal]). [horizontal] lays the ring at the start and the texts beside it (large fonts, one
 * column); otherwise the tile is a centred column, three of which share a row. The tile is one accessibility node
 * read as [description]; it opens nothing.
 */
@Suppress("LongParameterList") // A tile of figures: each text is its own slot.
@Composable
fun GoalRingTile(
    label: String,
    icon: DrawableResource,
    value: String,
    target: String,
    state: GoalProgressState,
    statusText: String,
    fraction: Float,
    description: String,
    modifier: Modifier = Modifier,
    paceFraction: Float? = null,
    horizontal: Boolean = false,
    staggerIndex: Int = 0,
    testTag: String = "goal_ring_tile",
) {
    val tileModifier = modifier
        .testTag(testTag)
        .clearAndSetSemantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
        }
        .background(AppTheme.colors.surfaceControl, AppShapes.md)
        .padding(horizontal = Spacing.sm, vertical = Spacing.md)
    val ring: @Composable () -> Unit = {
        GoalRing(
            fraction = fraction,
            state = state,
            value = value,
            description = description,
            paceFraction = paceFraction,
            icon = icon,
            staggerIndex = staggerIndex,
            testTag = "${testTag}_ring",
        )
    }
    val align = if (horizontal) TextAlign.Start else TextAlign.Center
    val texts: @Composable () -> Unit = {
        Text(
            text = label,
            style = AppTheme.typography.bodySmall,
            fontWeight = FontWeight.W600,
            color = AppTheme.colors.textPrimary,
            textAlign = align,
        )
        Text(text = target, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary, textAlign = align)
        GoalStatusLine(statusText, state, withGlyph = horizontal, align = align)
    }
    if (horizontal) {
        Row(
            tileModifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ring()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) { texts() }
        }
    } else {
        Column(
            tileModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ring()
            Spacer(Modifier.height(Spacing.xs))
            texts()
        }
    }
}

/** The state in words, W600 in the ring's colour; the glyph of the matching `StatusChip` tone when it fits. */
@Composable
private fun GoalStatusLine(text: String, state: GoalProgressState, withGlyph: Boolean, align: TextAlign) {
    val color = GoalRingDefaults.color(state, AppTheme.colors).takeIf { state != GoalProgressState.Idle }
        ?: AppTheme.colors.textSecondary
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        val glyph = StatusChipDefaults.icon(GoalRingDefaults.tone(state))
        if (withGlyph && glyph != null) {
            Icon(
                painter = painterResource(glyph),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(StatusChipDefaults.IconSize),
            )
        }
        Text(
            text = text,
            style = AppTheme.typography.bodySmall,
            fontWeight = FontWeight.W600,
            color = color,
            textAlign = align,
        )
    }
}

@Preview
@Composable
private fun GoalRingPreview() {
    AppTheme {
        Row(
            Modifier.background(AppTheme.colors.surfaceRaised).padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            GoalRing(0.3f, GoalProgressState.Behind, "45", "Cardio, vas por detrás", paceFraction = 0.57f)
            GoalRing(0.6f, GoalProgressState.OnTrack, "90", "Cardio, en camino", paceFraction = 0.57f)
            GoalRing(1f, GoalProgressState.Done, "150", "Cardio, cumplido")
            GoalRing(1.3f, GoalProgressState.Over, "195", "Cardio, superado")
        }
    }
}
