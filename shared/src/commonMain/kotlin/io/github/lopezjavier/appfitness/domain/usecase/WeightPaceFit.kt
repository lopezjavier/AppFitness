package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import io.github.lopezjavier.appfitness.domain.model.PlanOutcome
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlin.math.abs
import kotlin.math.sqrt

/** The card's verdict (planning/07-coach-design.md §5 (3)); the Spanish text belongs to the UI. */
enum class WeightPaceState { NoData, Soon, OnPace, Slower, Faster, Stable, Rising, Falling }

/** A weekly average: [index] is the week from the window start (0-based), [days] the weigh-in days behind it. */
data class WeeklyWeight(val index: Int, val kg: Double, val days: Int, val partial: Boolean)

/** A daily value at [weeks] from the window start (fractional). */
data class DailyWeightPoint(val weeks: Double, val kg: Double)

/** The plan line in weeks from the window start: from the clipped plan start to the outcome at the deadline. */
data class PlanPaceLine(val startWeeks: Double, val startKg: Double, val endWeeks: Double, val endKg: Double)

/**
 * Result of [WeightPaceFit.compute]. The slope and its 95 % interval (kg per week) exist from two weekly averages
 * (the interval from three); [state] says what the card may claim. [windowStart] is a Monday and [windowWeeks] the
 * number of ISO weeks from it to the week of today. The trend is never projected beyond the last weekly average.
 */
data class WeightPace(
    val state: WeightPaceState,
    val planRateKgWeek: Double,
    val slopeKgWeek: Double?,
    val slopeLowKgWeek: Double?,
    val slopeHighKgWeek: Double?,
    val weekly: List<WeeklyWeight>,
    val daily: List<DailyWeightPoint>,
    val planLine: PlanPaceLine?,
    val windowStart: LocalDate,
    val windowWeeks: Int,
    /** Cut, first 6 weeks of the phase run and [WeightPaceState.Faster]: the neutral "es habitual al empezar" line. */
    val earlyCutNote: Boolean,
    /** The weeks in the current phase run, when known (the window is clipped to them). */
    val weeksInPhase: Int?,
)

/** Pure fit of the weight against the plan (least squares on ISO-week averages, t interval; `04` §7.4, `07` §5). */
object WeightPaceFit {
    const val MAX_WINDOW_WEEKS = 8
    const val EARLY_CUT_WEEKS = 6
    private const val MIN_WEEKS_FOR_VERDICT = 4
    private const val MIN_WEEKS_FOR_SLOPE = 2
    private const val MIN_DAYS_IN_LATEST_WEEK = 3
    private const val DAYS_PER_WEEK = 7
    private const val EPSILON = 1e-9
    private const val NORMAL_95 = 1.96

    // Two-sided 95 % t values for df 1..6 (04 §7.4); more weeks than the window never occur.
    private val T95 = doubleArrayOf(12.706, 4.303, 3.182, 2.776, 2.571, 2.447)

    /**
     * [daily] are the daily weights (any range; only the window counts), [rateKgWeek] the plan's signed rate. The window
     * is the weeks of the current phase run ([weeksInPhase] null means the full 8), at most 8, ending the week of
     * [today]. [outcome] and [planStart] give the dashed plan line; no outcome means no line.
     */
    @Suppress("LongParameterList", "LongMethod") // One flat pure computation, every input is a plain value.
    fun compute(
        daily: List<DailyWeight>,
        today: LocalDate,
        rateKgWeek: Double,
        phase: Phase,
        weeksInPhase: PhaseWeeks?,
        planStart: LocalDate,
        outcome: PlanOutcome?,
    ): WeightPace {
        val phaseWeeks = weeksInPhase?.weeks?.takeIf { weeksInPhase.phase == phase }
        val windowWeeks = (phaseWeeks ?: MAX_WINDOW_WEEKS).coerceIn(1, MAX_WINDOW_WEEKS)
        val currentMonday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
        val windowStart = currentMonday.minus(DatePeriod(days = (windowWeeks - 1) * DAYS_PER_WEEK))
        val inWindow = daily.filter { it.date >= windowStart && it.date <= today }
        val weekly = inWindow
            .groupBy { windowStart.daysUntil(it.date) / DAYS_PER_WEEK }
            .map { (index, days) ->
                WeeklyWeight(index, days.map { it.kg }.average(), days.size, partial = index == windowWeeks - 1)
            }
            .sortedBy { it.index }
        val fit = fitOf(weekly)
        val state = stateOf(weekly, fit, rateKgWeek, windowWeeks)
        return WeightPace(
            state = state,
            planRateKgWeek = rateKgWeek,
            slopeKgWeek = fit?.slope,
            slopeLowKgWeek = fit?.low,
            slopeHighKgWeek = fit?.high,
            weekly = weekly,
            daily = inWindow.sortedBy { it.date }
                .map { DailyWeightPoint(windowStart.daysUntil(it.date).toDouble() / DAYS_PER_WEEK, it.kg) },
            planLine = planLineOf(outcome, rateKgWeek, planStart, windowStart),
            windowStart = windowStart,
            windowWeeks = windowWeeks,
            earlyCutNote = phase == Phase.Cut && state == WeightPaceState.Faster &&
                (phaseWeeks ?: Int.MAX_VALUE) <= EARLY_CUT_WEEKS,
            weeksInPhase = phaseWeeks,
        )
    }

    private class Fit(val slope: Double, val low: Double?, val high: Double?)

    private fun fitOf(weekly: List<WeeklyWeight>): Fit? {
        val n = weekly.size
        if (n < MIN_WEEKS_FOR_SLOPE) return null
        val meanX = weekly.map { it.index.toDouble() }.average()
        val meanY = weekly.map { it.kg }.average()
        val sxx = weekly.sumOf { (it.index - meanX) * (it.index - meanX) }
        val slope = weekly.sumOf { (it.index - meanX) * (it.kg - meanY) } / sxx
        if (n == MIN_WEEKS_FOR_SLOPE) return Fit(slope, null, null)
        val intercept = meanY - slope * meanX
        val sse = weekly.sumOf { (it.kg - (intercept + slope * it.index)).let { r -> r * r } }
        val df = n - 2
        val t = T95.getOrElse(df - 1) { NORMAL_95 }
        val half = t * sqrt(sse / df) / sqrt(sxx)
        return Fit(slope, slope - half, slope + half)
    }

    @Suppress("ReturnCount") // One guard per state of 07 §5 (2).
    private fun stateOf(weekly: List<WeeklyWeight>, fit: Fit?, rate: Double, windowWeeks: Int): WeightPaceState {
        if (fit == null) return WeightPaceState.NoData
        val low = fit.low
        val high = fit.high
        // The latest complete week is the one before the current (last) window week.
        val latestComplete = weekly.lastOrNull { it.index < windowWeeks - 1 }
        val gated = weekly.size < MIN_WEEKS_FOR_VERDICT ||
            latestComplete == null || latestComplete.days < MIN_DAYS_IN_LATEST_WEEK
        if (gated || low == null || high == null) return WeightPaceState.Soon
        if (abs(rate) < EPSILON) {
            return when {
                low > 0.0 -> WeightPaceState.Rising
                high < 0.0 -> WeightPaceState.Falling
                else -> WeightPaceState.Stable
            }
        }
        val containsRate = rate in low..high
        val containsZero = 0.0 in low..high
        val intervalAbove = low > rate
        return when {
            containsRate && !containsZero -> WeightPaceState.OnPace
            containsRate -> WeightPaceState.Soon
            // Above a cut's rate (less negative) is slower; above a gain's rate is faster; below mirrors it.
            intervalAbove == (rate < 0.0) -> WeightPaceState.Slower
            else -> WeightPaceState.Faster
        }
    }

    private fun planLineOf(
        outcome: PlanOutcome?,
        rate: Double,
        planStart: LocalDate,
        windowStart: LocalDate,
    ): PlanPaceLine? {
        if (outcome == null) return null
        val endWeeks = windowStart.daysUntil(outcome.deadline).toDouble() / DAYS_PER_WEEK
        val startWeeks = (windowStart.daysUntil(planStart).toDouble() / DAYS_PER_WEEK).coerceAtLeast(0.0)
        if (endWeeks <= startWeeks) return null
        // planned(w) = outcome - rate * (weeks to the deadline), straight to the deadline.
        return PlanPaceLine(
            startWeeks = startWeeks,
            startKg = outcome.targetValue - rate * (endWeeks - startWeeks),
            endWeeks = endWeeks,
            endKg = outcome.targetValue,
        )
    }
}
