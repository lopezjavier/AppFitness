package io.github.lopezjavier.appfitness.domain.usecase

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** 7-day moving average (planning/04-business-rules.md §3.2, ADR-49). */
class MovingAverage {
    /**
     * MA7 at [date]: the average of the daily values in `[date - 6, date]`, or null when no day in the window has
     * data (a single day is enough). [daily] may be in any order.
     */
    fun at(daily: List<DailyWeight>, date: LocalDate): Double? {
        val start = date.minus(DatePeriod(days = WINDOW_DAYS - 1))
        val inWindow = daily.filter { it.date >= start && it.date <= date }
        return if (inWindow.isNotEmpty()) inWindow.map { it.kg }.average() else null
    }

    private companion object {
        const val WINDOW_DAYS = 7
    }
}
