package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlin.math.abs
import kotlin.math.round

/** How the weekly rate reads against the goal; [Neutral] when there is no rate. */
enum class RateTone { Favorable, Unfavorable, Caution, Neutral }

/** The weekly rate in kg/week with 2 decimals, or null (not enough data). */
data class WeeklyRate(val value: Double?, val tone: RateTone)

/** `MA7(last day with data) - MA7(that day - 7)` coloured by the goal (planning/04-business-rules.md §3.3). */
class CalculateWeeklyRate(private val movingAverage: MovingAverage = MovingAverage()) {
    operator fun invoke(daily: List<DailyWeight>, strategy: GoalStrategy): WeeklyRate {
        val last = daily.maxOfOrNull { it.date }
        val current = last?.let { movingAverage.at(daily, it) }
        val previous = last?.let { movingAverage.at(daily, it.minus(DatePeriod(days = RATE_SPAN_DAYS))) }
        if (current == null || previous == null) return NOT_ENOUGH
        val rate = round((current - previous) * HUNDRED) / HUNDRED
        return WeeklyRate(rate, toneOf(rate, strategy))
    }

    private fun toneOf(rate: Double, strategy: GoalStrategy): RateTone = when (strategy) {
        GoalStrategy.Deficit -> if (rate < 0) RateTone.Favorable else RateTone.Unfavorable
        GoalStrategy.Surplus -> if (rate > 0) RateTone.Favorable else RateTone.Unfavorable
        GoalStrategy.Maintenance -> if (abs(rate) <= MAINTENANCE_BAND) RateTone.Favorable else RateTone.Caution
    }

    private companion object {
        const val MAINTENANCE_BAND = 0.20
        const val RATE_SPAN_DAYS = 7
        const val HUNDRED = 100.0
        val NOT_ENOUGH = WeeklyRate(null, RateTone.Neutral)
    }
}
