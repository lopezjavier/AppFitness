package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Result of [CompleteDays.window]: the [logged] days of the period, the [complete] ones among them and the
 * [median] kcal of the 28-day window (null when the window has no logged day).
 */
data class CompleteDaysResult(
    val logged: List<DiaryDaySummary>,
    val complete: List<DiaryDaySummary>,
    val median: Double?,
)

/**
 * Partial-logging filter of planning/07 section 3 (C1), the same rule as the SQL anchor of `coach_intake` (T-180):
 * median of the daily kcal over the 28 days ending at the period's last day (even count: mean of the two middle
 * values); a day under 40 % of that median is partial, exactly 40 % is complete.
 */
object CompleteDays {
    const val WINDOW_DAYS = 28
    private const val PARTIAL_RATIO = 0.4
    private const val MIDDLE = 2

    /**
     * Classifies the days of the period `[periodFrom, lastDay]` (default: the whole window) against the median of the
     * 28 days ending at [lastDay]; days of the period before that window do not enter the median.
     */
    fun window(
        summaries: List<DiaryDaySummary>,
        lastDay: LocalDate,
        periodFrom: LocalDate = lastDay.minus(WINDOW_DAYS - 1, DateTimeUnit.DAY),
    ): CompleteDaysResult {
        val windowFrom = lastDay.minus(WINDOW_DAYS - 1, DateTimeUnit.DAY)
        val inWindow = summaries.filter { it.date in windowFrom..lastDay }
        val median = median(inWindow.map { it.totals.kcal })
        // A long month reaches back past the window: those days are classified against the same median.
        val logged = summaries.filter { it.date in periodFrom..lastDay }.sortedBy { it.date }
        val complete = if (median == null) logged else logged.filter { it.totals.kcal >= median * PARTIAL_RATIO }
        return CompleteDaysResult(logged, complete, median)
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / MIDDLE
        return if (sorted.size % MIDDLE == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / MIDDLE
    }
}
