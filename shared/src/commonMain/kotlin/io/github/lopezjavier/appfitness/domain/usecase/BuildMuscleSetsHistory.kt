package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MuscleVolumeHistory
import io.github.lopezjavier.appfitness.domain.model.WeeklyMuscleVolumeRow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.until

private const val WEEKS = 8

/**
 * Turns the sparse `weekly_muscle_volume` rows into consecutive ISO weeks (Mon-Sun) from the first recorded week (at
 * most [WEEKS] back) to the week of `today` (UTC date, like the RPC). Missing weeks are zero; groups without volume in
 * the window are dropped; no rows give an empty history.
 */
object BuildMuscleVolumeHistory {
    /** Monday of the week containing [date]. */
    private fun mondayOf(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)

    /** First day of the window: the `p_from` argument of the RPC. */
    fun windowStart(today: LocalDate): LocalDate = mondayOf(today).minus(WEEKS - 1, DateTimeUnit.WEEK)

    /**
     * Lower bound to query the RPC with: far enough in the past to detect the first session even when it is older
     * than the [WEEKS]-week cap.
     */
    val FIRST_QUERY_DATE: LocalDate = LocalDate(2000, 1, 1)

    operator fun invoke(rows: List<WeeklyMuscleVolumeRow>, today: LocalDate): MuscleVolumeHistory {
        val firstWeek = rows.minOfOrNull { it.weekStart } ?: return MuscleVolumeHistory(emptyList(), emptyMap())
        val currentWeek = mondayOf(today)
        val start = minOf(maxOf(firstWeek, windowStart(today)), currentWeek)
        val count = start.until(currentWeek, DateTimeUnit.WEEK).toInt() + 1
        val weeks = List(count) { start.plus(it, DateTimeUnit.WEEK) }
        val series = rows.groupBy { it.muscleGroup }
            .toSortedMap(compareBy { it.ordinal })
            .mapValues { (_, groupRows) ->
                weeks.map { week -> groupRows.filter { it.weekStart == week }.sumOf { it.totalVolume } }
            }
            .filterValues { values -> values.any { it > 0.0 } }
        return MuscleVolumeHistory(weeks, series)
    }
}
