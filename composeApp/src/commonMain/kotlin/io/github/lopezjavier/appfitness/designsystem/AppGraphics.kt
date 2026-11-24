@file:Suppress("MatchingDeclarationName", "MagicNumber", "TooManyFunctions")
// MatchingDeclarationName: the file holds the small data graphics of the premium kit and their Defaults objects.
// MagicNumber: paint proportions (alphas, glow reaches) relative to the mark they belong to.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flame_outlined
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.max

// ---------------------------------------------------------------------------------------------------------------------
// IconBadge
// ---------------------------------------------------------------------------------------------------------------------

/** Sizes of [IconBadge]. */
object IconBadgeDefaults {
    val Size: Dp = 40.dp
    val IconSize: Dp = 22.dp
    val SmallSize: Dp = 32.dp
    val SmallIconSize: Dp = 18.dp
    val LargeSize: Dp = 56.dp
    val LargeIconSize: Dp = 28.dp
}

/**
 * A glyph on a tinted, softly lit container: the leading mark of a row, a tile or a hero stat. The [tint] gives each
 * area its own colour (weight, strength, food...) but never carries a state. Decorative: the text beside it names it.
 */
@Composable
fun IconBadge(
    icon: DrawableResource,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = IconBadgeDefaults.Size,
    iconSize: Dp = IconBadgeDefaults.IconSize,
    shape: Shape = AppShapes.md,
) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(AppPaint.tintSurface(tint))
            .border(AppBorder.thin, AppPaint.accentBorder(tint), shape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = AppPaint.ramp(tint).bright,
            modifier = Modifier.size(iconSize),
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// HeroStat
// ---------------------------------------------------------------------------------------------------------------------

/**
 * One figure of a hero row: an [IconBadge] in the [accent], the [value] (with its optional [unit]) and a short
 * [label] under it (up to [labelMaxLines] lines, then ellipsis). The whole stat is one accessibility node
 * ("value unit, label").
 */
@Composable
fun HeroStat(
    icon: DrawableResource,
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    unit: String? = null,
    labelMaxLines: Int = 1,
) {
    Row(
        modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, accent, size = IconBadgeDefaults.SmallSize, iconSize = IconBadgeDefaults.SmallIconSize)
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = AppTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700),
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
                if (unit != null) {
                    Text(text = unit, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                }
            }
            Text(
                text = label,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                maxLines = labelMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// StatRing
// ---------------------------------------------------------------------------------------------------------------------

/** Sizes of [StatRing]. */
object StatRingDefaults {
    val Size: Dp = 72.dp
    val StrokeWidth: Dp = 8.dp
    val SmallSize: Dp = 48.dp
    val SmallStroke: Dp = 5.dp
}

/**
 * A generic progress ring towards a goal: the [fraction] (0..1, clamped) drawn with the ramp of [color] over a
 * `surfaceOverlay` track, with [content] in the centre (an icon or a short figure). It fills once on first
 * composition. Decorative: the caller writes the figure as text and owns the semantics.
 */
@Composable
fun StatRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.athleticCyan,
    size: Dp = StatRingDefaults.Size,
    strokeWidth: Dp = StatRingDefaults.StrokeWidth,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val fill = rememberFillProgress()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        RingArc(
            sweep = fraction.coerceIn(0f, 1f) * fill.value * ProgressRingDefaults.FULL_SWEEP,
            track = AppTheme.colors.surfaceOverlay,
            progress = color,
            size = size,
            strokeWidth = strokeWidth,
            modifier = Modifier.clearAndSetSemantics {},
        )
        content()
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Sparkline
// ---------------------------------------------------------------------------------------------------------------------

/** Geometry of [Sparkline]. */
object SparklineDefaults {
    val Height: Dp = 40.dp
    val StrokeWidth: Dp = 2.dp
    val EndDotRadius: Dp = 3.dp
}

/**
 * A small smoothed trend line of [values] (left to right, scaled to their own range) with a faded area under it and a
 * lit dot on the latest value. No axes and no labels: it previews a trend beside a figure that is written as text.
 * Decorative. Give it a width; the height defaults to 40 dp. It is revealed once from the start. With [hollowPoints]
 * (indices into [values]; an empty set is allowed) every point gets a small dot and the listed ones are drawn hollow
 * (the deload weeks of the weekly report); null keeps the original look.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.athleticCyan,
    fill: Boolean = true,
    hollowPoints: Set<Int>? = null,
) {
    val reveal = rememberFillProgress()
    val ramp = AppPaint.ramp(color)
    Canvas(modifier.height(SparklineDefaults.Height).clearAndSetSemantics {}) {
        if (values.isEmpty()) return@Canvas
        val dot = SparklineDefaults.EndDotRadius.toPx()
        val pad = dot * 2.5f
        val left = dot
        val right = size.width - pad
        val top = pad
        val bottom = size.height - pad
        val points = sparkPoints(values, left, right, top, bottom)
        clipRect(right = size.width * reveal.value) {
            if (points.size > 1) {
                if (fill) {
                    val area = smoothPath(points).apply {
                        lineTo(points.last().x, size.height)
                        lineTo(points.first().x, size.height)
                        close()
                    }
                    drawPath(area, AppPaint.areaFade(color, topAlpha = 0.26f))
                }
                drawPath(
                    path = smoothPath(points),
                    brush = Brush.horizontalGradient(listOf(ramp.deep, ramp.bright)),
                    style = Stroke(SparklineDefaults.StrokeWidth.toPx(), cap = StrokeCap.Round),
                )
            }
            drawSparkMarkers(points, hollowPoints, ramp.bright, dot)
        }
    }
}

/** The values projected into the box [left, right] x [top, bottom]; a flat series runs through the middle. */
private fun sparkPoints(values: List<Double>, left: Float, right: Float, top: Float, bottom: Float): List<Offset> {
    val low = values.min()
    val high = values.max()
    return values.mapIndexed { index, value ->
        val x = if (values.size == 1) right else left + (right - left) * index / (values.size - 1)
        val share = if (high == low) 0.5f else ((value - low) / (high - low)).toFloat()
        val y = bottom - share * (bottom - top)
        Offset(x, y)
    }
}

/** The end dot and, with [hollowPoints], a small dot on every point (hollow for the listed indices). */
private fun DrawScope.drawSparkMarkers(points: List<Offset>, hollowPoints: Set<Int>?, bright: Color, dot: Float) {
    if (hollowPoints != null) {
        points.forEachIndexed { index, point ->
            if (index == points.lastIndex) return@forEachIndexed
            if (index in hollowPoints) {
                drawCircle(bright, dot * 0.8f, point, style = Stroke(SparklineDefaults.StrokeWidth.toPx() * 0.7f))
            } else {
                drawCircle(bright, dot * 0.7f, point)
            }
        }
    }
    val last = points.last()
    if (hollowPoints != null && points.lastIndex in hollowPoints) {
        drawCircle(bright, dot, last, style = Stroke(SparklineDefaults.StrokeWidth.toPx()))
    } else {
        drawRadialGlow(bright, last, dot * 3.2f, alpha = 0.45f)
        drawCircle(bright, dot, last)
        drawCircle(Color.White.copy(alpha = 0.9f), dot * 0.4f, last)
    }
}

/** A curve through [points] with horizontal tangents at each one: smooth, and it never overshoots a value. */
internal fun smoothPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    for (index in 1 until points.size) {
        val previous = points[index - 1]
        val current = points[index]
        val middle = (previous.x + current.x) / 2f
        cubicTo(middle, previous.y, middle, current.y, current.x, current.y)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// MiniBarChart
// ---------------------------------------------------------------------------------------------------------------------

/** Geometry of [MiniBarChart]. */
object MiniBarChartDefaults {
    val Height: Dp = 104.dp
    val MaxBarWidth: Dp = 18.dp
    const val BAR_SLOT_FRACTION = 0.5f
    val LabelGap: Dp = 6.dp
    val MinFill: Dp = 3.dp
    val TargetDash: Dp = 4.dp
    const val TRACK_ALPHA = 0.35f
    const val DIM_ALPHA = 0.55f
    const val TARGET_HEADROOM = 1.15f
}

/**
 * A compact column chart for a handful of periods (the days of a week, the last weeks): each bar is a rounded pill
 * over a ghost track, filled with the ramp of [color]; the bar at [highlightIndex] is lit and the others dimmed. The
 * bars at [unlitIndices] are always dimmed and get no glow (a period that is still running). Every bar has the same
 * colour: a [target] only draws a dashed line across the plot. [labels] (same size as
 * [values], or empty for none) sit under the bars. With a [description] the chart is one accessibility node that
 * speaks it; without one it is decorative. Bars rise once on first composition.
 */
@Composable
fun MiniBarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    color: Color = AppTheme.colors.athleticCyan,
    highlightIndex: Int? = null,
    target: Float? = null,
    unlitIndices: Set<Int> = emptySet(),
    description: String? = null,
) {
    val measurer = rememberTextMeasurer()
    val colors = AppTheme.colors
    val rise = rememberFillProgress()
    val labelStyle = AppTheme.typography.bodySmall
    val palette = MiniBarPalette(
        color = color,
        track = colors.trackInactive.copy(alpha = MiniBarChartDefaults.TRACK_ALPHA),
        target = colors.textSecondary,
        label = colors.textTertiary,
        labelLit = colors.textPrimary,
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(MiniBarChartDefaults.Height)
            .then(
                if (description != null) {
                    Modifier.semantics { contentDescription = description }
                } else {
                    Modifier.clearAndSetSemantics {}
                },
            ),
    ) {
        val input = MiniBarInput(values, labels, highlightIndex, target, rise.value, unlitIndices)
        drawMiniBars(input, palette, measurer, labelStyle)
    }
}

private class MiniBarPalette(
    val color: Color,
    val track: Color,
    val target: Color,
    val label: Color,
    val labelLit: Color,
)

private class MiniBarInput(
    val values: List<Float>,
    val labels: List<String>,
    val highlightIndex: Int?,
    val target: Float?,
    val rise: Float,
    val unlitIndices: Set<Int> = emptySet(),
)

@Suppress("LongParameterList") // The bar geometry, computed once by the caller.
private fun DrawScope.drawMiniBar(
    input: MiniBarInput,
    palette: MiniBarPalette,
    value: Float,
    left: Float,
    barWidth: Float,
    plotBottom: Float,
    plotHeight: Float,
    scaleMax: Float,
    index: Int,
) {
    val lit = index !in input.unlitIndices && (input.highlightIndex == null || index == input.highlightIndex)
    val barColor = palette.color
    val ramp = AppPaint.ramp(barColor)
    val height = max(plotHeight * (value / scaleMax) * input.rise, MiniBarChartDefaults.MinFill.toPx())
        .coerceAtMost(plotHeight)
    val top = plotBottom - height
    if (lit && input.highlightIndex != null) {
        drawRadialGlow(barColor, Offset(left + barWidth / 2f, top), barWidth * 1.8f, alpha = 0.28f)
    }
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(ramp.bright, ramp.base, ramp.deep), startY = top, endY = plotBottom),
        topLeft = Offset(left, top),
        size = Size(barWidth, height),
        cornerRadius = CornerRadius(barWidth / 2f),
        alpha = if (lit) 1f else MiniBarChartDefaults.DIM_ALPHA,
    )
}

private fun DrawScope.drawMiniBars(
    input: MiniBarInput,
    palette: MiniBarPalette,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    if (input.values.isEmpty()) return
    val hasLabels = input.labels.size == input.values.size
    val labelHeight = if (hasLabels) measurer.measure("0", style).size.height.toFloat() else 0f
    val gap = if (hasLabels) MiniBarChartDefaults.LabelGap.toPx() else 0f
    val plotBottom = size.height - labelHeight - gap
    val plotTop = 0f
    val plotHeight = plotBottom - plotTop
    val dataMax = input.values.max()
    val scaleMax = max(dataMax, (input.target ?: 0f) * MiniBarChartDefaults.TARGET_HEADROOM).takeIf { it > 0f } ?: 1f
    val slot = size.width / input.values.size
    val barWidth = minOf(slot * MiniBarChartDefaults.BAR_SLOT_FRACTION, MiniBarChartDefaults.MaxBarWidth.toPx())
    val corner = CornerRadius(barWidth / 2f)
    input.values.forEachIndexed { index, value ->
        val left = slot * index + (slot - barWidth) / 2f
        drawRoundRect(palette.track, Offset(left, plotTop), Size(barWidth, plotHeight), corner)
        if (value > 0f) drawMiniBar(input, palette, value, left, barWidth, plotBottom, plotHeight, scaleMax, index)
        if (hasLabels) {
            val labelStyle = miniBarLabelStyle(style, palette, index == input.highlightIndex)
            val text = measurer.measure(input.labels[index], labelStyle)
            val x = (slot * index + slot / 2f - text.size.width / 2f).coerceIn(
                0f,
                max(0f, size.width - text.size.width),
            )
            drawText(text, topLeft = Offset(x, plotBottom + gap))
        }
    }
    input.target?.takeIf { it > 0f }?.let { target ->
        drawTargetLine(palette.target, plotBottom - plotHeight * (target / scaleMax))
    }
}

private fun miniBarLabelStyle(style: TextStyle, palette: MiniBarPalette, lit: Boolean): TextStyle = style.copy(
    color = if (lit) palette.labelLit else palette.label,
    fontWeight = if (lit) FontWeight.W700 else style.fontWeight,
)

/** The dashed horizontal target line at [y]. */
private fun DrawScope.drawTargetLine(color: Color, y: Float) {
    val dash = MiniBarChartDefaults.TargetDash.toPx()
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = AppBorder.thin.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
    )
}

// ---------------------------------------------------------------------------------------------------------------------
// WeekStrip
// ---------------------------------------------------------------------------------------------------------------------

/**
 * One day of a [WeekStrip]. [label] is its short name ("L"). [done] fills the day with a check. [progress] (0..1)
 * draws a small ring instead (a day's calories against its target); [over] turns that ring amber. [today] marks the
 * current day (bold label and an accent outline when it is not done).
 */
@Immutable
data class WeekStripDay(
    val label: String,
    val done: Boolean = false,
    val today: Boolean = false,
    val progress: Float? = null,
    val over: Boolean = false,
)

/** Geometry of [WeekStrip]. */
object WeekStripDefaults {
    val CircleSize: Dp = 32.dp
    val RingStroke: Dp = 4.dp
    val CheckSize: Dp = 18.dp
    val TodayDot: Dp = 6.dp
}

/**
 * The week at a glance: one circle per day of [days] over its label. Done days are filled with the ramp of [color]
 * and a check, days with a progress show a mini ring, today is outlined, the rest are an empty track. With a
 * [description] the strip is one accessibility node that speaks it ("3 de 7 días"); without one it is decorative.
 */
@Composable
fun WeekStrip(
    days: List<WeekStripDay>,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.neonLime,
    description: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (description != null) {
                    Modifier.clearAndSetSemantics { contentDescription = description }
                } else {
                    Modifier.clearAndSetSemantics {}
                },
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEach { day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                WeekStripCircle(day, color)
                Text(
                    text = day.label,
                    style = if (day.today) {
                        AppTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700)
                    } else {
                        AppTheme.typography.bodySmall
                    },
                    color = if (day.today) AppTheme.colors.textPrimary else AppTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun WeekStripCircle(day: WeekStripDay, color: Color) {
    val colors = AppTheme.colors
    val size = WeekStripDefaults.CircleSize
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            day.progress != null -> {
                RingArc(
                    sweep = day.progress.coerceIn(0f, 1f) * ProgressRingDefaults.FULL_SWEEP,
                    track = colors.surfaceOverlay,
                    progress = if (day.over) colors.energyAmber else color,
                    size = size,
                    strokeWidth = WeekStripDefaults.RingStroke,
                )
                if (day.today) {
                    Box(Modifier.size(WeekStripDefaults.TodayDot).clip(AppShapes.full).background(colors.textPrimary))
                }
            }

            day.done -> {
                Box(
                    Modifier
                        .size(size)
                        .softGlow(color, cornerRadius = size / 2, radius = 6.dp, alpha = 0.22f, offsetY = 0.dp)
                        .clip(AppShapes.full)
                        .background(AppPaint.diagonal(color)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_check),
                        contentDescription = null,
                        tint = colors.onNeonLime,
                        modifier = Modifier.size(WeekStripDefaults.CheckSize),
                    )
                }
            }

            else -> {
                Box(
                    Modifier
                        .size(size)
                        .clip(AppShapes.full)
                        .background(colors.surfaceOverlay)
                        .border(
                            width = if (day.today) AppBorder.focus else AppBorder.thin,
                            color = if (day.today) color else colors.borderSubtle,
                            shape = AppShapes.full,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (day.today) {
                        Box(Modifier.size(WeekStripDefaults.TodayDot).clip(AppShapes.full).background(color))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// GaugeBar
// ---------------------------------------------------------------------------------------------------------------------

/** One zone of a [GaugeBar]: it ends at [upTo] (the first zone starts at the gauge minimum) and is drawn in [color]. */
@Immutable
data class GaugeZone(val upTo: Double, val color: Color)

/** Geometry of [GaugeBar]. */
object GaugeBarDefaults {
    val Height: Dp = 20.dp
    val TrackHeight: Dp = 8.dp
    val Gap: Dp = 3.dp
    val MarkerRadius: Dp = 7.dp
    val MarkerRing: Dp = 3.dp
    const val IDLE_ALPHA = 0.30f
}

/**
 * A scale split into coloured [zones] with a marker at [value]: where a reading sits among its ranges (body mass
 * index, body fat, a ratio). The zone that holds the value is drawn in full with its ramp, the others dimmed; the
 * marker is a white dot with a light of that zone. The scale runs from [min] to the last zone's end; the value is
 * clamped to it. Decorative: the caller writes the value and the name of its zone as text.
 */
@Composable
fun GaugeBar(value: Double, min: Double, zones: List<GaugeZone>, modifier: Modifier = Modifier) {
    val ring = AppTheme.colors.surfaceBase
    val slide = rememberFillProgress()
    Canvas(modifier.fillMaxWidth().height(GaugeBarDefaults.Height).clearAndSetSemantics {}) {
        val end = zones.lastOrNull()?.upTo ?: return@Canvas
        if (end <= min) return@Canvas
        val span = end - min
        val trackHeight = GaugeBarDefaults.TrackHeight.toPx()
        val top = (size.height - trackHeight) / 2f
        val gap = GaugeBarDefaults.Gap.toPx()
        val clamped = value.coerceIn(min, end)
        var start = min
        var activeColor = zones.last().color
        zones.forEachIndexed { index, zone ->
            val left = ((start - min) / span).toFloat() * size.width + if (index == 0) 0f else gap / 2f
            val right =
                ((zone.upTo - min) / span).toFloat() * size.width - if (index == zones.lastIndex) 0f else gap / 2f
            val active = clamped >= start && (clamped < zone.upTo || index == zones.lastIndex)
            if (active) activeColor = zone.color
            if (right > left) {
                drawRoundRect(
                    brush = AppPaint.horizontal(zone.color),
                    topLeft = Offset(left, top),
                    size = Size(right - left, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f),
                    alpha = if (active) 1f else GaugeBarDefaults.IDLE_ALPHA,
                )
            }
            start = zone.upTo
        }
        val radius = GaugeBarDefaults.MarkerRadius.toPx()
        val target = ((clamped - min) / span).toFloat() * size.width
        val x = (target * slide.value).coerceIn(radius, size.width - radius)
        val center = Offset(x, size.height / 2f)
        drawRadialGlow(activeColor, center, radius * 2.6f, alpha = 0.5f)
        drawCircle(ring, radius + GaugeBarDefaults.MarkerRing.toPx() / 2f, center)
        drawCircle(Color.White, radius - GaugeBarDefaults.MarkerRing.toPx() / 2f, center)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// DonutChart
// ---------------------------------------------------------------------------------------------------------------------

/** One slice of a [DonutChart]: its [value] (any unit, compared with the others) and [color]. */
@Immutable
data class DonutSlice(val value: Float, val color: Color)

/** Geometry of [DonutChart]. */
object DonutChartDefaults {
    val Size: Dp = 104.dp
    val StrokeWidth: Dp = 14.dp
    const val GAP_DEGREES = 4f
}

/**
 * A ring split into [slices] by their share of the total (the split of the day's calories by meal or by macro), with
 * [content] in the centre. Slices with no value are skipped; with no value at all only the track shows. It is drawn
 * once from the top, clockwise. Decorative: the caller writes each share as text (a legend) and owns the semantics.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    size: Dp = DonutChartDefaults.Size,
    strokeWidth: Dp = DonutChartDefaults.StrokeWidth,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val track = AppTheme.colors.surfaceOverlay
    val turn = rememberFillProgress()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size).clearAndSetSemantics {}) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)
            drawArc(track, 0f, ProgressRingDefaults.FULL_SWEEP, false, topLeft, arcSize, style = Stroke(strokePx))
            val shown = slices.filter { it.value > 0f }
            val total = shown.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas
            val gap = if (shown.size > 1) DonutChartDefaults.GAP_DEGREES else 0f
            var start = ProgressRingDefaults.START_ANGLE
            shown.forEach { slice ->
                val sweep = slice.value / total * ProgressRingDefaults.FULL_SWEEP * turn.value
                val drawn = (sweep - gap).coerceAtLeast(0.5f)
                drawArc(
                    brush = AppPaint.diagonal(slice.color),
                    startAngle = start + gap / 2f,
                    sweepAngle = drawn,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        content()
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// LegendDot
// ---------------------------------------------------------------------------------------------------------------------

/** A legend entry: a dot in [color], the [label] and, at the end, its [value] (W600). One accessibility node. */
@Composable
fun LegendRow(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Spacing.sm).clip(AppShapes.full).background(AppPaint.diagonal(color)))
        Text(
            text = label,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            text = value,
            style = AppTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
            color = AppTheme.colors.textPrimary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Preview
@Composable
private fun AppGraphicsPreview() {
    AppTheme {
        Column(
            Modifier.background(AppTheme.colors.surfaceRaised),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            HeroStat(Res.drawable.ic_flame_outlined, "4", "días seguidos", AppTheme.colors.energyAmber)
            Sparkline(listOf(78.0, 78.4, 78.1, 77.6, 77.2, 76.9), Modifier.fillMaxWidth())
            MiniBarChart(
                values = listOf(1800f, 2100f, 2500f, 1900f, 0f, 0f, 0f),
                labels = listOf("L", "M", "X", "J", "V", "S", "D"),
                highlightIndex = 3,
                target = 2400f,
            )
            WeekStrip(
                listOf(
                    WeekStripDay("L", done = true),
                    WeekStripDay("M"),
                    WeekStripDay("X", done = true),
                    WeekStripDay("J", today = true),
                    WeekStripDay("V"),
                    WeekStripDay("S"),
                    WeekStripDay("D"),
                ),
            )
            GaugeBar(
                value = 25.4,
                min = 15.0,
                zones = listOf(
                    GaugeZone(18.5, AppTheme.colors.athleticCyan),
                    GaugeZone(25.0, AppTheme.colors.electricGreen),
                    GaugeZone(30.0, AppTheme.colors.energyAmber),
                    GaugeZone(40.0, AppTheme.colors.error),
                ),
            )
            DonutChart(
                listOf(
                    DonutSlice(30f, AppTheme.colors.macroProtein),
                    DonutSlice(45f, AppTheme.colors.macroCarbs),
                    DonutSlice(25f, AppTheme.colors.macroFat),
                ),
            )
        }
    }
}
