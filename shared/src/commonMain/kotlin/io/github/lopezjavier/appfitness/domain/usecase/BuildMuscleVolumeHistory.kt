package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MuscleVolumeHistory
import io.github.lopezjavier.appfitness.domain.model.WeeklyMuscleVolumeRow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val WEEKS = 8

/**
 * Turns the sparse `weekly_muscle_volume` rows into [WEEKS] consecutive ISO weeks (Mon-Sun) ending at the week of
 * `today` (UTC date, like the RPC). Missing weeks are zero; groups without volume in the window are dropped.
 */
object BuildMuscleVolumeHistory {
    /** Monday of the week containing [date]. */
    private fun mondayOf(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)

    /** First day of the window: the `p_from` argument of the RPC. */
    fun windowStart(today: LocalDate): LocalDate = mondayOf(today).minus(WEEKS - 1, DateTimeUnit.WEEK)

    operator fun invoke(rows: List<WeeklyMuscleVolumeRow>, today: LocalDate): MuscleVolumeHistory {
        val start = windowStart(today)
        val weeks = List(WEEKS) { start.plus(it, DateTimeUnit.WEEK) }
        val series = rows.groupBy { it.muscleGroup }
            .toSortedMap(compareBy { it.ordinal })
            .mapValues { (_, groupRows) ->
                weeks.map { week -> groupRows.filter { it.weekStart == week }.sumOf { it.totalVolume } }
            }
            .filterValues { values -> values.any { it > 0.0 } }
        return MuscleVolumeHistory(weeks, series)
    }
}
