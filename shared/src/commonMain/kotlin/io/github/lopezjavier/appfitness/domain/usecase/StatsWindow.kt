package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

private const val LAST_DAYS = 6

/** Inclusive date window of the statistics; [from] null means no lower bound. */
data class StatsWindow(val from: LocalDate?, val to: LocalDate) {
    /** Equal-length window right before this one; null when there is no lower bound. */
    fun previous(): StatsWindow? {
        val start = from ?: return null
        val length = start.daysUntil(to) + 1
        return StatsWindow(start.minus(length, DateTimeUnit.DAY), start.minus(1, DateTimeUnit.DAY))
    }

    companion object {
        fun forRange(range: ProgressRange, today: LocalDate) = StatsWindow(range.from(today), today)

        /** The last 7 days including [today]. */
        fun lastSevenDays(today: LocalDate) = StatsWindow(today.minus(LAST_DAYS, DateTimeUnit.DAY), today)
    }
}
