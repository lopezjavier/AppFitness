package io.github.lopezjavier.appfitness.domain.usecase

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** 7-day moving average (planning/04-business-rules.md §3.2). */
class MovingAverage {
    /**
     * MA7 at [date]: the average of the daily values in `[date - 6, date]`, or null when fewer than 2 days have data.
     * [daily] may be in any order.
     */
    fun at(daily: List<DailyWeight>, date: LocalDate): Double? {
        val start = date.minus(DatePeriod(days = WINDOW_DAYS - 1))
        val inWindow = daily.filter { it.date >= start && it.date <= date }
        return if (inWindow.size >= MIN_DAYS) inWindow.map { it.kg }.average() else null
    }

    private companion object {
        const val WINDOW_DAYS = 7
        const val MIN_DAYS = 2
    }
}
