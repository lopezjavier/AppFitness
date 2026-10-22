package io.github.lopezjavier.appfitness.domain.usecase

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** The dates a chart range needs: what is drawn, what must be fetched and the exclusive end of the fetch. */
data class ChartWindow(val visibleStart: LocalDate, val fetchStart: LocalDate, val endExclusive: LocalDate)

/** Weight chart filter (planning/04-business-rules.md §3.4): [days] counted back from today. Default is 3M. */
@Suppress("MagicNumber") // The filter lengths are the definition.
enum class WeightChartRange(val days: Int) {
    Week(7),
    Month(30),
    Quarter(90),
    HalfYear(180),
    Year(365),
    ;

    /**
     * The window for [today]. The fetch starts [MA_LEAD_IN_DAYS] before the earliest day needed, so the MA7 of the
     * first visible day uses earlier data. The weekly rate needs [MIN_RATE_DAYS] of data whatever the range, so the
     * short ranges (1S) fetch at least that many days.
     */
    fun window(today: LocalDate): ChartWindow {
        val visibleStart = today.minus(DatePeriod(days = days))
        val needed = today.minus(DatePeriod(days = maxOf(days, MIN_RATE_DAYS)))
        return ChartWindow(
            visibleStart = visibleStart,
            fetchStart = needed.minus(DatePeriod(days = MA_LEAD_IN_DAYS)),
            endExclusive = today.plus(DatePeriod(days = 1)),
        )
    }

    companion object {
        val Default = Quarter
        const val MA_LEAD_IN_DAYS = 6
        const val MIN_RATE_DAYS = 14
    }
}
