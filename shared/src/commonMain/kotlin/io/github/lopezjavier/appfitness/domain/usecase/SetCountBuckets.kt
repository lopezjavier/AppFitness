package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Pure bucketing of weekly set counts for the set-count chart (weekly up to 6M, monthly for 1A and "Todo"). */
object SetCountBuckets {
    /**
     * One point per Monday from the week of the window start (or of the first point for "Todo") to the week of
     * [today], zeros where the server returned nothing. Empty for "Todo" without data.
     */
    fun weekly(points: List<SetCountPoint>, window: StatsWindow, today: LocalDate): List<SetCountPoint> {
        val first = window.from?.let(::mondayOf) ?: points.minOfOrNull { it.weekStart } ?: return emptyList()
        val last = mondayOf(today)
        val bySet = points.groupBy { it.weekStart }.mapValues { (_, v) -> v.sumOf { it.sets } }
        return generateSequence(first) { it.plus(1, DateTimeUnit.WEEK) }
            .takeWhile { it <= last }
            .map { SetCountPoint(it, bySet[it] ?: 0) }
            .toList()
    }

    /**
     * Groups weeks into calendar months by the week's Monday (a week spanning two months goes to the month of its
     * Monday). Points are dated the first of the month, ordered, with empty months in between filled with 0.
     */
    fun monthly(points: List<SetCountPoint>): List<SetCountPoint> {
        if (points.isEmpty()) return emptyList()
        val byMonth = points.groupBy { LocalDate(it.weekStart.year, it.weekStart.month, 1) }
            .mapValues { (_, v) -> v.sumOf { it.sets } }
        val first = byMonth.keys.min()
        val last = byMonth.keys.max()
        return generateSequence(first) { it.plus(1, DateTimeUnit.MONTH) }
            .takeWhile { it <= last }
            .map { SetCountPoint(it, byMonth[it] ?: 0) }
            .toList()
    }

    /** Weekly buckets for 1M, 3M and 6M, monthly ones (up to the current month) for 1A and "Todo". */
    fun bucketsFor(range: ProgressRange, points: List<SetCountPoint>, window: StatsWindow, today: LocalDate) =
        when (range) {
            ProgressRange.OneMonth, ProgressRange.ThreeMonths, ProgressRange.SixMonths ->
                weekly(points, window, today)

            ProgressRange.OneYear, ProgressRange.AllTime -> monthly(weekly(points, window, today))
        }

    private fun mondayOf(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)
}
