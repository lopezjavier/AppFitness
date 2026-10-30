package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.designsystem.TrendTone
import io.github.lopezjavier.appfitness.domain.model.DerivedWeightTrend
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun nullSlope_hasNoSlopeAndIsNeutral() {
        val reading = trendReading(trend(null, -1.0, 1.0), GoalStrategy.Deficit)

        assertNull(reading.slope)
        assertEquals(TrendTone.Neutral, reading.tone)
        assertFalse(reading.isFlat)
    }

    @Test
    fun slopeIsCarriedAsANumber() {
        assertEquals(-0.3, trendReading(trend(-0.3, -0.5, -0.1), GoalStrategy.Deficit).slope)
        assertEquals(1.2, trendReading(trend(1.2), GoalStrategy.Surplus).slope)
    }

    @Test
    fun margin_isHalfTheIntervalWidth() =
        assertEquals(1.2, trendReading(trend(-0.3, low = -1.5, high = 0.9), GoalStrategy.Deficit).margin!!, 1e-9)

    @Test
    fun margin_isNullWhenAnEndIsUnknown() {
        assertNull(trendReading(trend(-0.3), GoalStrategy.Deficit).margin)
        assertNull(trendReading(trend(-0.3, low = -0.5), GoalStrategy.Deficit).margin)
        assertNull(trendReading(trend(-0.3, high = -0.1), GoalStrategy.Deficit).margin)
    }

    @Test
    fun margin_ofAZeroWidthIntervalIsZero() =
        assertEquals(0.0, trendReading(trend(-0.4, low = -0.4, high = -0.4), GoalStrategy.Deficit).margin)

    @Test
    fun intervalSpanningZero_stillKeepsTheSignedSlope() {
        val reading = trendReading(trend(-0.3, low = -2.7, high = 2.1), GoalStrategy.Deficit)

        assertEquals(-0.3, reading.slope)
        assertEquals(2.4, reading.margin!!, 1e-9)
    }

    @Test
    fun tone_followsTheGoalStrategy() {
        assertEquals(TrendTone.Favorable, trendReading(trend(-0.4), GoalStrategy.Deficit).tone)
        assertEquals(TrendTone.Unfavorable, trendReading(trend(0.4), GoalStrategy.Deficit).tone)
        assertEquals(TrendTone.Favorable, trendReading(trend(0.4), GoalStrategy.Surplus).tone)
        assertEquals(TrendTone.Unfavorable, trendReading(trend(-0.4), GoalStrategy.Surplus).tone)
        assertEquals(TrendTone.Favorable, trendReading(trend(0.1), GoalStrategy.Maintenance).tone)
        assertEquals(TrendTone.Caution, trendReading(trend(0.5), GoalStrategy.Maintenance).tone)
    }

    @Test
    fun tone_isNeutralWhenTheGoalIsUnknown() = assertEquals(TrendTone.Neutral, trendReading(trend(-0.4), null).tone)

    @Test
    fun isFlat_whenTheSlopeRoundsToZeroInTheTwoDecimalDisplay() {
        assertTrue(trendReading(trend(0.0), null).isFlat)
        assertTrue(trendReading(trend(-0.004), null).isFlat)
        assertFalse(trendReading(trend(-0.005), null).isFlat)
        assertFalse(trendReading(trend(0.01), null).isFlat)
    }
}
