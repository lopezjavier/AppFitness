@file:Suppress("MatchingDeclarationName", "ktlint:standard:property-naming", "TooManyFunctions") // Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A weekly average at [x] (weeks from the window start, fractional allowed); a [partial] week is drawn hollow. */
data class WeightPacePoint(val x: Float, val kg: Double, val partial: Boolean = false)

/** The 95 % interval of the fit at [x]. */
data class WeightPaceBandPoint(val x: Float, val lowKg: Double, val highKg: Double)

/**
 * The plan: the outcome [endKg] at the deadline [endX], drawn as a flat goal line. [startX] and [startKg] (the weight
 * at plan start) only widen the x range; they are not drawn.
 */
data class WeightPacePlanLine(val startX: Float, val startKg: Double, val endX: Float, val endKg: Double)

/** An x axis label at [x]; the [emphasized] one ("Hoy 79,3 kg") is `athleticCyan` W700 and marks today. */
data class WeightPaceLabel(val x: Float, val text: String, val emphasized: Boolean = false)

/**
 * Plain data of [WeightPaceChart]. [weekly] is sorted by x; the trend runs from its first to its last point and is
 * never projected. [daily] are the single weigh-ins as (x, kg) pairs. A null [plan] or fewer than two [weekly] points
 * leave out that line. [band] is optional, never reaches beyond the trend's x range and is clipped to the plot.
 */
data class WeightPaceChartData(
    val weekly: List<WeightPacePoint>,
    val daily: List<WeightPacePoint> = emptyList(),
    val plan: WeightPacePlanLine? = null,
    val band: List<WeightPaceBandPoint> = emptyList(),
    val xLabels: List<WeightPaceLabel> = emptyList(),
)

/** Geometry of [WeightPaceChart] (R-101). */
object WeightPaceChartDefaults {
    val Height: Dp = 176.dp
    val PlanStroke: Dp = 1.5.dp
    val PlanDash: Dp = 6.dp
    val PlanGap: Dp = 4.dp
    val TargetRadius: Dp = 4.5.dp
    val TargetHaloRadius: Dp = 9.dp
    val TargetCore: Dp = 1.5.dp
    val TrendStroke: Dp = 3.dp
    val WeeklyDotRadius: Dp = 2.5.dp
    val WeeklyDotRing: Dp = 1.5.dp
    val NowDotRadius: Dp = 4.5.dp
    val NowDotRing: Dp = 2.dp
    val DailyDotRadius: Dp = 2.dp
    val GridStroke: Dp = 1.dp
    val GridDash: Dp = 2.dp
    val GridGap: Dp = 4.dp
    val TodayDash: Dp = 3.dp
    val HatchSpacing: Dp = 7.dp
    val LabelGap: Dp = 6.dp
    val XLabelGap: Dp = 8.dp
    val PlotEndInset: Dp = 12.dp
    const val GridIntervals: Int = 3
    const val DailyAlpha: Float = 0.35f
    const val BandAlpha: Float = 0.12f
    const val BandMaxShare: Double = 0.5
    const val AreaAlpha: Float = 0.28f
    const val GridAlpha: Float = 0.16f
    const val BaselineAlpha: Float = 0.32f
    const val TodayAlpha: Float = 0.55f
    const val FutureAlpha: Float = 0.04f
    const val HatchAlpha: Float = 0.07f
    const val TargetHaloAlpha: Float = 0.35f
    const val LastGlowFactor: Float = 4f
    const val LastGlowAlpha: Float = 0.55f
    const val DotsStart: Float = 0.9f
    const val Tenth: Double = 10.0
}

/**
 * The weight against the goal on the Trainer root: a flat dashed goal line at the [WeightPaceChartData.plan] outcome
 * that ends in a target marker at the deadline, and the solid real trend through the weekly averages with its area,
 * the weekly dots and the
 * single weigh-ins; the last weekly point is the lit "now" dot. Y domain fitted to the data and the plan (the line
 * chart's [plotRange]: 12 % margin, at least 2 kg tall, three round intervals), with four round labels and dashed
 * gridlines. A dashed "today" marker splits the past from the hatched time still to come. Decorative: the verdict and
 * figures lines beside it are the text alternative (R-101). The plan line draws first, then the trend 40 ms later
 * (R-97).
 */
@Composable
fun WeightPaceChart(
    data: WeightPaceChartData,
    modifier: Modifier = Modifier,
    valueLabel: (Double) -> String = {
        (
            (it * WeightPaceChartDefaults.Tenth).roundToInt() /
                WeightPaceChartDefaults.Tenth
            ).toString()
    },
) {
    val colors = AppTheme.colors
    val labelStyle = AppTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val planProgress = rememberFillProgress(0)
    val trendProgress = rememberFillProgress(1)
    val palette = PacePalette(
        trend = colors.athleticCyan,
        plan = colors.textSecondary,
        target = colors.textPrimary,
        ring = colors.surfaceControl,
        label = colors.textTertiary,
        grid = colors.textTertiary,
    )
    Canvas(modifier.fillMaxWidth().height(WeightPaceChartDefaults.Height).clearAndSetSemantics {}) {
        drawPace(data, palette, labelStyle, measurer, valueLabel, planProgress.value, trendProgress.value)
    }
}

@Suppress("LongParameterList") // One colour per role of the chart.
private class PacePalette(
    val trend: Color,
    val plan: Color,
    val target: Color,
    val ring: Color,
    val label: Color,
    val grid: Color,
)

/** Maps data coordinates to pixels. */
@Suppress("LongParameterList") // The plot rectangle and the two data ranges, plain values.
private class PaceMapper(
    val plotLeft: Float,
    val plotRight: Float,
    val plotTop: Float,
    val plotBottom: Float,
    val xMin: Float,
    val xMax: Float,
    val yMin: Double,
    val yMax: Double,
) {
    fun x(value: Float): Float =
        if (xMax <= xMin) plotLeft else plotLeft + (value - xMin) / (xMax - xMin) * (plotRight - plotLeft)

    fun y(kg: Double): Float = plotBottom - ((kg - yMin) / (yMax - yMin)).toFloat() * (plotBottom - plotTop)

    fun at(point: WeightPacePoint) = Offset(x(point.x), y(point.kg))
}

@Suppress("LongParameterList") // One draw pass over the data, its palette, its text tools and its progress.
private fun DrawScope.drawPace(
    data: WeightPaceChartData,
    palette: PacePalette,
    labelStyle: TextStyle,
    measurer: TextMeasurer,
    valueLabel: (Double) -> String,
    planProgress: Float,
    trendProgress: Float,
) {
    val kgs = data.weekly.map { it.kg } + data.daily.map { it.kg } + listOfNotNull(data.plan?.endKg)
    val xs = data.weekly.map { it.x } + data.daily.map { it.x } +
        listOfNotNull(data.plan?.startX, data.plan?.endX) + data.xLabels.map { it.x }
    if (kgs.isEmpty() || xs.isEmpty()) return
    val range = plotRange(reference = null, values = kgs, dots = null) ?: return
    val ticks = paceTicks(range)
    val style = labelStyle.copy(color = palette.label)
    val tickTexts = ticks.map { measurer.measure(valueLabel(it), style) }
    val labelRow = measurer.measure("0", style).size.height.toFloat()
    val mapper = PaceMapper(
        plotLeft = tickTexts.maxOf { it.size.width } + WeightPaceChartDefaults.LabelGap.toPx(),
        plotRight = size.width - WeightPaceChartDefaults.PlotEndInset.toPx(),
        plotTop = tickTexts.last().size.height / 2f,
        plotBottom = size.height - labelRow - WeightPaceChartDefaults.XLabelGap.toPx(),
        xMin = xs.min(),
        xMax = xs.max(),
        yMin = range.start,
        yMax = range.endInclusive,
    )
    val todayX = (data.xLabels.firstOrNull { it.emphasized }?.x ?: data.weekly.lastOrNull()?.x)?.let(mapper::x)
    todayX?.let { drawFuture(it, mapper, palette) }
    drawGrid(ticks, tickTexts, mapper, palette)
    todayX?.let { drawToday(it, mapper, palette, trendProgress) }
    drawXLabels(data.xLabels, mapper, style, measurer, palette)
    data.plan?.let { drawPlan(it, mapper, palette, planProgress) }
    drawTrend(data, mapper, palette, trendProgress)
}

/** The gridline values: the [range] cut into [WeightPaceChartDefaults.GridIntervals] equal round steps. */
internal fun paceTicks(range: ClosedFloatingPointRange<Double>): List<Double> {
    val intervals = WeightPaceChartDefaults.GridIntervals
    val unit = (range.endInclusive - range.start) / intervals
    return (0..intervals).map { range.start + unit * it }
}

/** The time still to come (right of today): a faint fill with diagonal hatching, clipped to the plot. */
private fun DrawScope.drawFuture(left: Float, mapper: PaceMapper, palette: PacePalette) {
    if (left >= mapper.plotRight) return
    val height = mapper.plotBottom - mapper.plotTop
    val spacing = WeightPaceChartDefaults.HatchSpacing.toPx()
    clipRect(left, mapper.plotTop, size.width, mapper.plotBottom) {
        drawRect(
            palette.grid.copy(alpha = WeightPaceChartDefaults.FutureAlpha),
            topLeft = Offset(left, mapper.plotTop),
            size = Size(size.width - left, height),
        )
        val hatch = palette.grid.copy(alpha = WeightPaceChartDefaults.HatchAlpha)
        var x = left - height
        while (x < size.width) {
            drawLine(
                hatch,
                Offset(x, mapper.plotBottom),
                Offset(x + height, mapper.plotTop),
                strokeWidth = WeightPaceChartDefaults.GridStroke.toPx(),
            )
            x += spacing
        }
    }
}

/** Dashed gridlines at the round [ticks] with their labels right-aligned in the gutter; the lowest one is solid. */
private fun DrawScope.drawGrid(
    ticks: List<Double>,
    texts: List<TextLayoutResult>,
    mapper: PaceMapper,
    palette: PacePalette,
) {
    val dash = PathEffect.dashPathEffect(
        floatArrayOf(WeightPaceChartDefaults.GridDash.toPx(), WeightPaceChartDefaults.GridGap.toPx()),
    )
    val gap = WeightPaceChartDefaults.LabelGap.toPx()
    ticks.forEachIndexed { index, kg ->
        val y = mapper.y(kg)
        val baseline = index == 0
        drawLine(
            color = palette.grid.copy(
                alpha = if (baseline) WeightPaceChartDefaults.BaselineAlpha else WeightPaceChartDefaults.GridAlpha,
            ),
            start = Offset(mapper.plotLeft, y),
            end = Offset(size.width, y),
            strokeWidth = WeightPaceChartDefaults.GridStroke.toPx(),
            pathEffect = if (baseline) null else dash,
        )
        val text = texts[index]
        drawText(text, topLeft = Offset(mapper.plotLeft - gap - text.size.width, y - text.size.height / 2f))
    }
}

/** The dashed vertical "today" marker in the trend colour. */
private fun DrawScope.drawToday(x: Float, mapper: PaceMapper, palette: PacePalette, progress: Float) {
    if (progress <= 0f) return
    val dash = WeightPaceChartDefaults.TodayDash.toPx()
    drawLine(
        color = palette.trend.copy(alpha = WeightPaceChartDefaults.TodayAlpha * progress),
        start = Offset(x, mapper.plotTop),
        end = Offset(x, mapper.plotBottom),
        strokeWidth = WeightPaceChartDefaults.GridStroke.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
    )
}

/**
 * The x labels under the plot, centred on their x and kept inside the plot's left edge and the canvas. The emphasized
 * (today) label is placed first; any other label that would touch an already placed one is left out.
 */
private fun DrawScope.drawXLabels(
    labels: List<WeightPaceLabel>,
    mapper: PaceMapper,
    style: TextStyle,
    measurer: TextMeasurer,
    palette: PacePalette,
) {
    val gap = WeightPaceChartDefaults.XLabelGap.toPx()
    val placed = mutableListOf<ClosedFloatingPointRange<Float>>()
    labels.sortedByDescending { it.emphasized }.forEach { label ->
        val labelStyle = if (label.emphasized) {
            style.copy(color = palette.trend, fontWeight = FontWeight.W700)
        } else {
            style
        }
        val layout = measurer.measure(label.text, labelStyle)
        val width = layout.size.width.toFloat()
        val maxLeft = (size.width - width).coerceAtLeast(0f)
        val left = (mapper.x(label.x) - width / 2f).coerceIn(mapper.plotLeft.coerceAtMost(maxLeft), maxLeft)
        val right = left + width
        if (placed.none { left < it.endInclusive + gap && right > it.start - gap }) {
            placed += left..right
            drawText(layout, topLeft = Offset(left, size.height - layout.size.height))
        }
    }
}

/**
 * The goal: a flat dashed line at the goal weight across the plot, drawn left to right, ending in the target marker at
 * the deadline. It is a reference level, not a predicted path, so it never competes with the real trend.
 */
private fun DrawScope.drawPlan(plan: WeightPacePlanLine, mapper: PaceMapper, palette: PacePalette, progress: Float) {
    val y = mapper.y(plan.endKg)
    val start = Offset(mapper.plotLeft, y)
    val end = Offset(mapper.x(plan.endX), y)
    val tip = start + (end - start) * progress
    drawLine(
        color = palette.plan,
        start = start,
        end = tip,
        strokeWidth = WeightPaceChartDefaults.PlanStroke.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(WeightPaceChartDefaults.PlanDash.toPx(), WeightPaceChartDefaults.PlanGap.toPx()),
        ),
    )
    if (progress >= 1f) drawTarget(end, palette)
}

/** The goal at the deadline: a bullseye (halo ring, hollow ring and a core dot). */
private fun DrawScope.drawTarget(center: Offset, palette: PacePalette) {
    val stroke = WeightPaceChartDefaults.PlanStroke.toPx()
    val radius = WeightPaceChartDefaults.TargetRadius.toPx()
    drawCircle(
        palette.target.copy(alpha = WeightPaceChartDefaults.TargetHaloAlpha),
        WeightPaceChartDefaults.TargetHaloRadius.toPx(),
        center,
        style = Stroke(stroke),
    )
    drawCircle(palette.ring, radius, center)
    drawCircle(palette.target, radius, center, style = Stroke(stroke))
    drawCircle(palette.target, WeightPaceChartDefaults.TargetCore.toPx(), center)
}

private fun DrawScope.drawTrend(data: WeightPaceChartData, mapper: PaceMapper, palette: PacePalette, progress: Float) {
    if (progress <= 0f) return
    // A band taller than half the domain (an early, uncertain fit) says nothing and only floods the plot.
    val bandSpan = data.band.maxOfOrNull { it.highKg - it.lowKg } ?: 0.0
    if (data.band.size >= 2 && bandSpan <= (mapper.yMax - mapper.yMin) * WeightPaceChartDefaults.BandMaxShare) {
        clipRect(mapper.plotLeft, mapper.plotTop, mapper.plotRight, mapper.plotBottom) {
            drawBand(data.band, mapper, palette.trend, progress)
        }
    }
    val dailyColor = palette.trend.copy(alpha = WeightPaceChartDefaults.DailyAlpha * progress)
    data.daily.forEach {
        drawCircle(dailyColor, WeightPaceChartDefaults.DailyDotRadius.toPx(), mapper.at(it))
    }
    if (data.weekly.isEmpty()) return
    val last = data.weekly.last()
    if (data.weekly.size >= 2) drawTrendLine(data.weekly, mapper, palette, progress)
    val dotAlpha = ((progress - WeightPaceChartDefaults.DotsStart) / (1f - WeightPaceChartDefaults.DotsStart))
        .coerceIn(0f, 1f)
    if (dotAlpha <= 0f) return
    data.weekly.dropLast(1).forEach {
        drawWeeklyDot(
            it,
            mapper,
            palette,
            dotAlpha,
            WeightPaceChartDefaults.WeeklyDotRadius,
            WeightPaceChartDefaults.WeeklyDotRing,
        )
    }
    drawRadialGlow(
        palette.trend,
        mapper.at(last),
        WeightPaceChartDefaults.NowDotRadius.toPx() * WeightPaceChartDefaults.LastGlowFactor,
        WeightPaceChartDefaults.LastGlowAlpha * dotAlpha,
    )
    drawWeeklyDot(
        last,
        mapper,
        palette,
        dotAlpha,
        WeightPaceChartDefaults.NowDotRadius,
        WeightPaceChartDefaults.NowDotRing,
    )
}

/** The trend: a gradient area down to the baseline (once drawn) under a lit gradient stroke. */
private fun DrawScope.drawTrendLine(
    weekly: List<WeightPacePoint>,
    mapper: PaceMapper,
    palette: PacePalette,
    progress: Float,
) {
    val line = Path().apply {
        val first = mapper.at(weekly.first())
        moveTo(first.x, first.y)
        weekly.drop(1).forEach { mapper.at(it).let { p -> lineTo(p.x, p.y) } }
    }
    if (progress >= 1f) {
        val area = Path().apply {
            val first = mapper.at(weekly.first())
            moveTo(first.x, first.y)
            weekly.drop(1).forEach { mapper.at(it).let { p -> lineTo(p.x, p.y) } }
            lineTo(mapper.at(weekly.last()).x, mapper.plotBottom)
            lineTo(mapper.at(weekly.first()).x, mapper.plotBottom)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(
                colors = listOf(
                    palette.trend.copy(alpha = WeightPaceChartDefaults.AreaAlpha),
                    palette.trend.copy(alpha = 0f),
                ),
                startY = mapper.plotTop,
                endY = mapper.plotBottom,
            ),
        )
    }
    drawPath(
        partOf(line, progress),
        AppPaint.horizontal(palette.trend),
        style = Stroke(
            WeightPaceChartDefaults.TrendStroke.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

@Suppress("LongParameterList") // The point, its mapping, colours, fade-in and its two sizes.
private fun DrawScope.drawWeeklyDot(
    point: WeightPacePoint,
    mapper: PaceMapper,
    palette: PacePalette,
    alpha: Float,
    radiusDp: Dp,
    ringDp: Dp,
) {
    val center = mapper.at(point)
    val radius = radiusDp.toPx()
    val ring = ringDp.toPx()
    if (point.partial) {
        drawCircle(palette.ring.copy(alpha = alpha), radius + ring / 2, center)
        drawCircle(palette.trend.copy(alpha = alpha), radius, center, style = Stroke(ring))
    } else {
        drawCircle(palette.ring.copy(alpha = alpha), radius + ring, center)
        drawCircle(palette.trend.copy(alpha = alpha), radius, center)
    }
}

private fun DrawScope.drawBand(band: List<WeightPaceBandPoint>, mapper: PaceMapper, color: Color, progress: Float) {
    val path = Path().apply {
        moveTo(mapper.x(band.first().x), mapper.y(band.first().highKg))
        band.drop(1).forEach { lineTo(mapper.x(it.x), mapper.y(it.highKg)) }
        band.asReversed().forEach { lineTo(mapper.x(it.x), mapper.y(it.lowKg)) }
        close()
    }
    drawPath(path, color.copy(alpha = WeightPaceChartDefaults.BandAlpha * progress))
}

/** The first [fraction] of [path], by length. */
private fun partOf(path: Path, fraction: Float): Path {
    if (fraction >= 1f) return path
    val measure = PathMeasure().apply { setPath(path, false) }
    return Path().apply { measure.getSegment(0f, measure.length * fraction, this, true) }
}

internal val PaceSamplePlan = WeightPacePlanLine(startX = 0f, startKg = 78.0, endX = 12f, endKg = 80.1)

@Suppress("MagicNumber") // Sample weekly averages of the preview and the catalogue.
internal val PaceSampleWeekly = listOf(
    WeightPacePoint(0f, 78.0),
    WeightPacePoint(1f, 78.3),
    WeightPacePoint(2f, 78.5),
    WeightPacePoint(3f, 79.3, partial = true),
)

@Preview
@Composable
private fun WeightPaceChartPreview() {
    AppTheme {
        @Suppress("MagicNumber")
        WeightPaceChart(
            WeightPaceChartData(
                weekly = PaceSampleWeekly,
                daily = listOf(WeightPacePoint(0.2f, 78.1), WeightPacePoint(1.6f, 78.6), WeightPacePoint(2.8f, 79.1)),
                plan = PaceSamplePlan,
                band = listOf(WeightPaceBandPoint(0f, 77.8, 78.2), WeightPaceBandPoint(3f, 78.9, 79.7)),
                xLabels = listOf(
                    WeightPaceLabel(0f, "Sem 1"),
                    WeightPaceLabel(3f, "Hoy 79,3 kg", emphasized = true),
                    WeightPaceLabel(12f, "4 dic"),
                ),
            ),
        )
    }
}
