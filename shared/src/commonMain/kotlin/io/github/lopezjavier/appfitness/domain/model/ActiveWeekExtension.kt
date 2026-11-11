package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * The week in progress seen as extendable (T-276, US-134): the picker chooses that week's new last day, between
 * [minDay] and [maxDay]; [selected] is its current last day. The server still takes the block's last day:
 * [blockEndFor] converts a picked day (block end + the days the week moves). Picking the week's original last day
 * moves nothing and restores the dates.
 */
class ActiveWeekExtension private constructor(
    val week: PlanWeek,
    private val blockEnd: LocalDate,
    val minDay: LocalDate,
    val maxDay: LocalDate,
) {
    /** The week's current last day, where the picker starts. */
    val selected: LocalDate get() = week.endDate

    fun contains(day: LocalDate): Boolean = day >= minDay && day <= maxDay

    /** The block's last day to send when the week's last day becomes [day]. */
    fun blockEndFor(day: LocalDate): LocalDate = blockEnd.plus(DatePeriod(days = week.endDate.daysUntil(day)))

    companion object {
        /**
         * Null unless the block can be changed ([BlockPause.extendable]), a [WeekPhase.Current] week exists and the
         * pause carries its block end and limits.
         */
        fun of(weeks: List<PlanWeek>, pause: BlockPause?): ActiveWeekExtension? {
            val current = weeks.firstOrNull { it.phase == WeekPhase.Current }
            val open = pause?.takeIf { it.extendable }
            val end = open?.blockEnd
            val min = open?.minEnd
            val max = open?.maxEnd
            val limits = if (min != null && max != null) min to max else null
            return if (current == null || end == null || limits == null) {
                null
            } else {
                ActiveWeekExtension(
                    week = current,
                    blockEnd = end,
                    minDay = current.endDate.plus(DatePeriod(days = end.daysUntil(limits.first))),
                    maxDay = current.endDate.plus(DatePeriod(days = end.daysUntil(limits.second))),
                )
            }
        }
    }
}
