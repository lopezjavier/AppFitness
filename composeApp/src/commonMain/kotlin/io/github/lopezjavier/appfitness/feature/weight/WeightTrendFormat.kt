package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.designsystem.TrendTone
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.model.MetricUnit
import io.github.lopezjavier.appfitness.domain.usecase.RateTone
import io.github.lopezjavier.appfitness.domain.usecase.TrendPoint
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.roundToInt

private const val HUNDRED = 100
private const val MINUS = '−'

/** The signed weekly rate with a decimal comma, two decimals and the true minus sign ("−0,45", "+0,30"). */
internal fun rateText(value: Double): String {
    val hundredths = (abs(value) * HUNDRED).roundToInt()
    val sign = when {
        hundredths == 0 -> ""
        value < 0 -> MINUS.toString()
        else -> "+"
    }
    return "$sign${hundredths / HUNDRED},${(hundredths % HUNDRED).toString().padStart(2, '0')}"
}

/** A metric value without its unit: one decimal with a comma, an integer for the BMR (kcal). */
internal fun metricAxisText(value: Double, metric: BodyMetric): String =
    if (metric.unit == MetricUnit.Kcal) value.roundToInt().toString() else decimalText(value)

/** A metric value with its unit ("74,2 kg", "82,5 cm", "18,3 %", "1650 kcal"). */
internal fun metricValueText(value: Double, metric: BodyMetric): String {
    val unit = when (metric.unit) {
        MetricUnit.Kg -> "kg"
        MetricUnit.Cm -> "cm"
        MetricUnit.Percent -> "%"
        MetricUnit.Kcal -> "kcal"
    }
    return "${metricAxisText(value, metric)} $unit"
}

internal fun RateTone.toTrendTone(): TrendTone = when (this) {
    RateTone.Favorable -> TrendTone.Favorable
    RateTone.Unfavorable -> TrendTone.Unfavorable
    RateTone.Caution -> TrendTone.Caution
    RateTone.Neutral -> TrendTone.Neutral
}

/**
 * The trend as one value per calendar day from the first to the last point, so a point sits on its date and days
 * without a reading are gaps ([dots] and [ma7] are null there). Index 0 is [start].
 */
internal class ChartSeries(
    val start: LocalDate,
    val dots: List<Double?>,
    val ma7: List<Double?>,
    private val points: List<TrendPoint>,
) {
    fun dateAt(index: Int): LocalDate = start.plus(DatePeriod(days = index))

    /** The point with data closest to the chart [index] (a touch can land on a day without a reading). */
    fun pointAt(index: Int): TrendPoint? = points.minByOrNull { abs(start.daysUntil(it.date) - index) }
}

internal fun chartSeries(points: List<TrendPoint>): ChartSeries {
    val start = points.first().date
    val days = start.daysUntil(points.last().date) + 1
    val dots = MutableList<Double?>(days) { null }
    val ma7 = MutableList<Double?>(days) { null }
    points.forEach {
        val index = start.daysUntil(it.date)
        dots[index] = it.dailyKg
        ma7[index] = it.ma7Kg
    }
    return ChartSeries(start, dots, ma7, points)
}
