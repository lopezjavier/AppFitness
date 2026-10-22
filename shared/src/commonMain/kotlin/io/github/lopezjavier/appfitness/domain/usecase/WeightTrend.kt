package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** One chart day: the daily value and its MA7 (null when the window has fewer than 2 days). */
data class TrendPoint(val date: LocalDate, val dailyKg: Double, val ma7Kg: Double?)

/** What the trend chart and pill show. [hasEnoughData] is false when no visible day has an MA7. */
data class TrendData(val points: List<TrendPoint>, val rate: WeeklyRate, val hasEnoughData: Boolean)

/**
 * Builds the trend from measurements loaded with [WeightChartRange.window] (fetchStart until endExclusive): points
 * cover only the visible days, while MA7 and the rate also use the lead-in data before them. The fetch always spans
 * at least 14 days so the rate exists even for the 1S range.
 */
class WeightTrend(
    private val dailyWeights: DailyWeights = DailyWeights(),
    private val movingAverage: MovingAverage = MovingAverage(),
    private val weeklyRate: CalculateWeeklyRate = CalculateWeeklyRate(movingAverage),
) {
    operator fun invoke(
        measurements: List<BodyMeasurement>,
        range: WeightChartRange,
        strategy: GoalStrategy,
        timeZone: TimeZone,
        today: LocalDate,
    ): TrendData {
        val daily = dailyWeights(measurements, timeZone)
        val visibleStart = range.window(today).visibleStart
        val points = daily
            .filter { it.date >= visibleStart && it.date <= today }
            .map { TrendPoint(it.date, it.kg, movingAverage.at(daily, it.date)) }
        return TrendData(points, weeklyRate(daily, strategy), points.any { it.ma7Kg != null })
    }
}
