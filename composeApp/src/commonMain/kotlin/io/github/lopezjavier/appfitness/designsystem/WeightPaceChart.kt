@file:Suppress("MatchingDeclarationName", "ktlint:standard:property-naming") // Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/** A weekly average at [x] (weeks from the window start, fractional allowed); a [partial] week is drawn hollow. */
data class WeightPacePoint(val x: Float, val kg: Double, val partial: Boolean = false)

/** The 95 % interval of the fit at [x]. */
data class WeightPaceBandPoint(val x: Float, val lowKg: Double, val highKg: Double)

/** The plan line: a straight segment from the weight at plan start to the outcome at the deadline. */
data class WeightPacePlanLine(val startX: Float, val startKg: Double, val endX: Float, val endKg: Double)

/** An x axis label at [x]; the [emphasized] one ("Hoy 79,3 kg") is `athleticCyan` W700. */
data class WeightPaceLabel(val x: Float, val text: String, val emphasized: Boolean = false)

/**
 * Plain data of [WeightPaceChart]. [weekly] is sorted by x; the trend runs from its first to its last point and is
 * never projected. [daily] are the single weigh-ins as (x, kg) pairs. A null [plan] or fewer than two [weekly] points
 * leave out that line. [band] is optional and never reaches beyond the trend's x range.
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
    val Height: Dp = 140.dp
    val PlanStroke: Dp = 1.5.dp
    val PlanDash: Dp = 6.dp
    val PlanGap: Dp = 4.dp
    val PlanEndRadius: Dp = 3.dp
    val TrendStroke: Dp = 2.5.dp
    val WeeklyDotRadius: Dp = 2.5.dp
    val WeeklyDotRing: Dp = 1.5.dp
    val DailyDotRadius: Dp = 1.5.dp
    val LabelGap: Dp = 4.dp
    val PlotEndInset: Dp = 8.dp
    const val DailyAlpha: Float = 0.3f
    const val BandAlpha: Float = 0.12f
    const val AreaAlpha: Float = 0.20f
    const val LastGlowFactor: Float = 5f
    const val LastGlowAlpha: Float = 0.40f
    const val RangePadding: Double = 0.1
    const val MinRangeKg: Double = 2.0
    const val DotsStart: Float = 0.9f
    const val Tenth: Double = 10.0
}

/**
 * The weight against the plan on the Trainer root: a dashed [WeightPaceChartData.plan] line (expected pace) and the
 * solid real trend through the weekly averages, with the 95 % band, the weekly dots and the single weigh-ins. Y range =
 * data and plan padded by 10 % and at least 2 kg tall, with two labels (min, max) from [valueLabel]; no gridlines.
 * Decorative: the verdict and figures lines beside it are the text alternative (R-101). The plan line draws first,
 * then the trend and band 40 ms later (R-97).
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
        ring = colors.surfaceControl,
        label = colors.textTertiary,
    )
    Canvas(modifier.fillMaxWidth().height(WeightPaceChartDefaults.Height).clearAndSetSemantics {}) {
        drawPace(data, palette, labelStyle, measurer, valueLabel, planProgress.value, trendProgress.value)
    }
}

private class PacePalette(val trend: Color, val plan: Color, val ring: Color, val label: Color)

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
    val kgs = data.weekly.map { it.kg } + data.daily.map { it.kg } +
        listOfNotNull(data.plan?.startKg, data.plan?.endKg)
    val xs = data.weekly.map { it.x } + data.daily.map { it.x } +
        listOfNotNull(data.plan?.startX, data.plan?.endX) + data.xLabels.map { it.x }
    if (kgs.isEmpty() || xs.isEmpty()) return
    val (yMin, yMax) = paceYRange(kgs)
    val style = labelStyle.copy(color = palette.label)
    val minText = measurer.measure(valueLabel(yMin), style)
    val maxText = measurer.measure(valueLabel(yMax), style)
    val labelRow = measurer.measure("0", style).size.height.toFloat()
    val gap = WeightPaceChartDefaults.LabelGap.toPx()
    val mapper = PaceMapper(
        plotLeft = max(minText.size.width, maxText.size.width) + gap,
        plotRight = size.width - WeightPaceChartDefaults.PlotEndInset.toPx(),
        plotTop = maxText.size.height / 2f,
        plotBottom = size.height - labelRow - gap,
        xMin = xs.min(),
        xMax = xs.max(),
        yMin = yMin,
        yMax = yMax,
    )
    drawText(maxText, topLeft = Offset(0f, mapper.y(yMax) - maxText.size.height / 2f))
    drawText(minText, topLeft = Offset(0f, mapper.y(yMin) - minText.size.height / 2f))
    drawXLabels(data.xLabels, mapper, style, measurer, palette)
    data.plan?.let { drawPlan(it, mapper, palette, planProgress) }
    drawTrend(data, mapper, palette, trendProgress)
}

/** The padded y range: data and plan padded by 10 %, at least 2 kg tall. */
internal fun paceYRange(kgs: List<Double>): Pair<Double, Double> {
    val low = kgs.min()
    val high = kgs.max()
    val pad = (high - low) * WeightPaceChartDefaults.RangePadding
    val padLow = low - pad
    val padHigh = high + pad
    val missing = WeightPaceChartDefaults.MinRangeKg - (padHigh - padLow)
    return if (missing > 0) (padLow - missing / 2) to (padHigh + missing / 2) else padLow to padHigh
}

private fun DrawScope.drawXLabels(
    labels: List<WeightPaceLabel>,
    mapper: PaceMapper,
    style: TextStyle,
    measurer: TextMeasurer,
    palette: PacePalette,
) {
    labels.forEach { label ->
        val labelStyle = if (label.emphasized) {
            style.copy(color = palette.trend, fontWeight = FontWeight.W700)
        } else {
            style
        }
        val layout = measurer.measure(label.text, labelStyle)
        val left = (mapper.x(label.x) - layout.size.width / 2f)
            .coerceIn(0f, max(0f, size.width - layout.size.width))
        drawText(layout, topLeft = Offset(left, size.height - layout.size.height))
    }
}

private fun DrawScope.drawPlan(plan: WeightPacePlanLine, mapper: PaceMapper, palette: PacePalette, progress: Float) {
    val start = Offset(mapper.x(plan.startX), mapper.y(plan.startKg))
    val end = Offset(mapper.x(plan.endX), mapper.y(plan.endKg))
    val tip = start + (end - start) * progress
    drawLine(
        color = palette.plan,
        start = start,
        end = tip,
        strokeWidth = WeightPaceChartDefaults.PlanStroke.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(WeightPaceChartDefaults.PlanDash.toPx(), WeightPaceChartDefaults.PlanGap.toPx()),
        ),
    )
    if (progress >= 1f) {
        // Hollow end circle: surface fill then the stroke ring.
        val radius = WeightPaceChartDefaults.PlanEndRadius.toPx()
        drawCircle(palette.ring, radius, end)
        drawCircle(palette.plan, radius, end, style = Stroke(WeightPaceChartDefaults.PlanStroke.toPx()))
    }
}

private fun DrawScope.drawTrend(data: WeightPaceChartData, mapper: PaceMapper, palette: PacePalette, progress: Float) {
    if (progress <= 0f) return
    if (data.band.size >= 2) drawBand(data.band, mapper, palette.trend, progress)
    val dailyColor = palette.trend.copy(alpha = WeightPaceChartDefaults.DailyAlpha * progress)
    data.daily.forEach {
        drawCircle(dailyColor, WeightPaceChartDefaults.DailyDotRadius.toPx(), mapper.at(it))
    }
    if (data.weekly.size < 2) return
    val line = Path().apply {
        val first = mapper.at(data.weekly.first())
        moveTo(first.x, first.y)
        data.weekly.drop(1).forEach { mapper.at(it).let { p -> lineTo(p.x, p.y) } }
    }
    if (progress >= 1f) {
        val first = mapper.at(data.weekly.first())
        val last = mapper.at(data.weekly.last())
        val area = Path().apply {
            moveTo(first.x, first.y)
            data.weekly.drop(1).forEach { mapper.at(it).let { p -> lineTo(p.x, p.y) } }
            lineTo(last.x, mapper.plotBottom)
            lineTo(first.x, mapper.plotBottom)
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
        drawRadialGlow(
            palette.trend,
            last,
            WeightPaceChartDefaults.WeeklyDotRadius.toPx() * WeightPaceChartDefaults.LastGlowFactor,
            WeightPaceChartDefaults.LastGlowAlpha,
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
    val dotAlpha = ((progress - WeightPaceChartDefaults.DotsStart) / (1f - WeightPaceChartDefaults.DotsStart))
        .coerceIn(0f, 1f)
    if (dotAlpha > 0f) data.weekly.forEach { drawWeeklyDot(it, mapper, palette, dotAlpha) }
}

private fun DrawScope.drawWeeklyDot(point: WeightPacePoint, mapper: PaceMapper, palette: PacePalette, alpha: Float) {
    val center = mapper.at(point)
    val radius = WeightPaceChartDefaults.WeeklyDotRadius.toPx()
    val ring = WeightPaceChartDefaults.WeeklyDotRing.toPx()
    if (point.partial) {
        drawCircle(palette.ring.copy(alpha = alpha), radius + ring / 2, center)
        drawCircle(
            palette.trend.copy(alpha = alpha),
            radius,
            center,
            style = Stroke(ring),
        )
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
