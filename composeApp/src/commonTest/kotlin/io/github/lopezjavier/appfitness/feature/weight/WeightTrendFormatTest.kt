package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.designsystem.TrendTone
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.usecase.RateTone
import io.github.lopezjavier.appfitness.domain.usecase.TrendPoint
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WeightTrendFormatTest {
    @Test
    fun rateText_negative_usesTheTrueMinusAndTwoDecimals() = assertEquals("−0,45", rateText(-0.45))

    @Test
    fun rateText_positive_hasAPlusSign() = assertEquals("+0,30", rateText(0.30))

    @Test
    fun rateText_zero_hasNoSign() = assertEquals("0,00", rateText(0.0))

    @Test
    fun rateText_keepsTrailingZeros() = assertEquals("−1,20", rateText(-1.2))

    @Test
    fun metricValueText_kilogramsCentimetresAndPercentHaveOneDecimalAndTheirUnit() {
        assertEquals("74,2 kg", metricValueText(74.2, BodyMetric.Weight))
        assertEquals("3,1 kg", metricValueText(3.14, BodyMetric.Bone))
        assertEquals("82,5 cm", metricValueText(82.5, BodyMetric.Waist))
        assertEquals("18,3 %", metricValueText(18.3, BodyMetric.BodyFat))
    }

    @Test
    fun metricValueText_kilocaloriesAreAnInteger() {
        assertEquals("1650 kcal", metricValueText(1649.6, BodyMetric.Bmr))
    }

    @Test
    fun metricAxisText_dropsTheUnitAndKeepsTheMetricPrecision() {
        assertEquals("74,2", metricAxisText(74.2, BodyMetric.Weight))
        assertEquals("1650", metricAxisText(1649.6, BodyMetric.Bmr))
    }

    @Test
    fun toneMapping_coversEveryRateTone() {
        assertEquals(TrendTone.Favorable, RateTone.Favorable.toTrendTone())
        assertEquals(TrendTone.Unfavorable, RateTone.Unfavorable.toTrendTone())
        assertEquals(TrendTone.Caution, RateTone.Caution.toTrendTone())
        assertEquals(TrendTone.Neutral, RateTone.Neutral.toTrendTone())
    }

    @Test
    fun chartSeries_placesEachPointOnItsDayAndLeavesGapsNull() {
        val points = listOf(
            TrendPoint(LocalDate(2026, 10, 1), 80.0, null),
            TrendPoint(LocalDate(2026, 10, 4), 79.0, 79.5),
        )

        val series = chartSeries(points)

        assertEquals(listOf<Double?>(80.0, null, null, 79.0), series.dots)
        assertEquals(listOf<Double?>(null, null, null, 79.5), series.ma7)
        assertEquals(LocalDate(2026, 10, 1), series.start)
    }

    @Test
    fun nearestPoint_picksTheClosestDayWithData() {
        val points = listOf(
            TrendPoint(LocalDate(2026, 10, 1), 80.0, null),
            TrendPoint(LocalDate(2026, 10, 10), 79.0, 79.5),
        )
        val series = chartSeries(points)

        assertEquals(LocalDate(2026, 10, 1), series.pointAt(3)?.date)
        assertEquals(LocalDate(2026, 10, 10), series.pointAt(7)?.date)
    }
}
