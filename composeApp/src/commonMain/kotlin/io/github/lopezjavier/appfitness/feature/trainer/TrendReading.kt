package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.domain.model.DerivedWeightTrend
import kotlin.math.abs

/** What the weight trend says in words; the interval only decides whether the direction can be trusted. */
internal sealed interface TrendReading {
    data object Unknown : TrendReading

    data object Flat : TrendReading

    data object Unclear : TrendReading

    data class Falling(val kgPerWeek: String) : TrendReading

    data class Rising(val kgPerWeek: String) : TrendReading
}

internal fun trendReading(trend: DerivedWeightTrend): TrendReading {
    val slope = trend.slopeKgWeek ?: return TrendReading.Unknown
    val magnitude = AppNumberFormat.decimal(abs(slope))
    val low = trend.intervalLow
    val high = trend.intervalHigh
    return when {
        magnitude == "0" -> TrendReading.Flat
        low != null && high != null && low < 0 && high > 0 -> TrendReading.Unclear
        slope < 0 -> TrendReading.Falling(magnitude)
        else -> TrendReading.Rising(magnitude)
    }
}
