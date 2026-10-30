package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import kotlinx.datetime.daysUntil
import kotlin.math.abs
import kotlin.math.round

/** How the weekly rate reads against the goal; [Neutral] when there is no rate. */
enum class RateTone { Favorable, Unfavorable, Caution, Neutral }

/** The weekly rate in kg/week with 2 decimals, or null (not enough data). */
data class WeeklyRate(val value: Double?, val tone: RateTone)

private const val MAINTENANCE_BAND = 0.20

/** How a weekly [rate] (kg/week) reads against the goal [strategy] (shared by the Weight and Trainer rates). */
fun rateTone(rate: Double, strategy: GoalStrategy): RateTone = when (strategy) {
    GoalStrategy.Deficit -> if (rate < 0) RateTone.Favorable else RateTone.Unfavorable
    GoalStrategy.Surplus -> if (rate > 0) RateTone.Favorable else RateTone.Unfavorable
    GoalStrategy.Maintenance -> if (abs(rate) <= MAINTENANCE_BAND) RateTone.Favorable else RateTone.Caution
}

/**
 * Rate between the last two days with data, scaled to 7 days: `(w_last - w_prev) / daysBetween * 7`, coloured by the
 * goal (planning/04-business-rules.md §3.3, ADR-49). Fewer than 2 days with data means no rate.
 */
class CalculateWeeklyRate {
    operator fun invoke(daily: List<DailyWeight>, strategy: GoalStrategy): WeeklyRate {
        val lastTwo = daily.distinctBy { it.date }.sortedBy { it.date }.takeLast(2)
        if (lastTwo.size < 2) return NOT_ENOUGH
        val (previous, last) = lastTwo
        val daysBetween = previous.date.daysUntil(last.date)
        val rate = round((last.kg - previous.kg) / daysBetween * RATE_SPAN_DAYS * HUNDRED) / HUNDRED
        return WeeklyRate(rate, rateTone(rate, strategy))
    }

    private companion object {
        const val RATE_SPAN_DAYS = 7.0
        const val HUNDRED = 100.0
        val NOT_ENOUGH = WeeklyRate(null, RateTone.Neutral)
    }
}
