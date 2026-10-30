package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.designsystem.TrendTone
import io.github.lopezjavier.appfitness.domain.model.DerivedWeightTrend
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.usecase.rateTone
import io.github.lopezjavier.appfitness.feature.weight.toTrendTone
import kotlin.math.abs

/** Slopes below half a hundredth show as "0,00" (see `rateText`), so they count as flat. */
private const val FLAT_BELOW = 0.005

/**
 * The weight trend as numbers for the Peso `TrendPill`: [slope] in kg/week (null when unknown), [margin] the half
 * width of the interval in kg/week (null when unknown) and [tone] from the goal strategy (Neutral without a slope or
 * without a known goal).
 */
internal data class TrendReading(val slope: Double?, val margin: Double?, val tone: TrendTone) {
    /** True when the slope is a number that displays as zero; a numeric check, not a formatted-string compare. */
    val isFlat: Boolean get() = slope != null && abs(slope) < FLAT_BELOW
}

internal fun trendReading(trend: DerivedWeightTrend, strategy: GoalStrategy?): TrendReading {
    val slope = trend.slopeKgWeek
    val low = trend.intervalLow
    val high = trend.intervalHigh
    val margin = if (low != null && high != null) (high - low) / 2 else null
    val tone = if (slope != null && strategy != null) rateTone(slope, strategy).toTrendTone() else TrendTone.Neutral
    return TrendReading(slope, margin, tone)
}
