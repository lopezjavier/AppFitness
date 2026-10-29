package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.DerivedWeightTrend
import kotlin.test.Test
import kotlin.test.assertEquals

class TrendReadingTest {
    private fun trend(slope: Double?, low: Double? = null, high: Double? = null) = DerivedWeightTrend(
        slopeKgWeek = slope,
        intervalLow = low,
        intervalHigh = high,
        weeks = 3,
        lowConfidence = false,
        provisional = false,
        confirmed = true,
        reask = false,
        uncertainty = null,
        missing = emptyList(),
    )

    @Test
    fun nullSlope_isUnknown() = assertEquals(TrendReading.Unknown, trendReading(trend(null)))

    @Test
    fun negativeSlope_isFallingWithItsMagnitudeAndNoSign() =
        assertEquals(TrendReading.Falling("0,3"), trendReading(trend(-0.3, -0.5, -0.1)))

    @Test
    fun positiveSlope_isRising() = assertEquals(TrendReading.Rising("1,2"), trendReading(trend(1.2)))

    @Test
    fun slopeRoundingToZero_isFlat_evenWhenNegative() {
        assertEquals(TrendReading.Flat, trendReading(trend(0.0)))
        assertEquals(TrendReading.Flat, trendReading(trend(-0.04)))
    }

    @Test
    fun intervalSpanningZero_beatsTheVerb() =
        assertEquals(TrendReading.Unclear, trendReading(trend(-0.3, low = -2.7, high = 2.1)))

    @Test
    fun intervalNotSpanningZero_keepsTheVerb() =
        assertEquals(TrendReading.Rising("0,5"), trendReading(trend(0.5, low = 0.1, high = 0.9)))

    @Test
    fun intervalOfZeroWidth_keepsTheVerb() =
        assertEquals(TrendReading.Falling("0,4"), trendReading(trend(-0.4, low = -0.4, high = -0.4)))

    @Test
    fun flatBeatsAnIntervalSpanningZero() =
        assertEquals(TrendReading.Flat, trendReading(trend(0.0, low = -1.0, high = 1.0)))
}
