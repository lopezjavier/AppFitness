package io.github.lopezjavier.appfitness.feature.routines

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

private const val DAYS_PER_WEEK = 7

/** The figures of the Entreno hero: this week's trained days and workouts and the current weekly streak. */
internal data class TrainingWeek(
    val today: LocalDate? = null,
    val trainedDays: Set<LocalDate> = emptySet(),
    val workouts: Int = 0,
    val streakWeeks: Int = 0,
)

/** Monday of the week of [this]. */
internal fun LocalDate.weekStart(): LocalDate = minus(dayOfWeek.ordinal, DateTimeUnit.DAY)

/**
 * Derives the hero from the workout count per day: days trained since Monday of [today]'s week, how many workouts
 * that is, and the number of consecutive weeks with at least one workout (the current week counts once it has one;
 * an empty current week does not break the streak yet).
 */
internal fun trainingWeekOf(counts: Map<LocalDate, Int>, today: LocalDate): TrainingWeek {
    val start = today.weekStart()
    val thisWeek = counts.filterKeys { it >= start && it <= today }
    val weeks = counts.keys.map { it.weekStart() }.toSet()
    var cursor = if (start in weeks) start else start.minus(DAYS_PER_WEEK, DateTimeUnit.DAY)
    var streak = 0
    while (cursor in weeks) {
        streak++
        cursor = cursor.minus(DAYS_PER_WEEK, DateTimeUnit.DAY)
    }
    return TrainingWeek(today, thisWeek.keys, thisWeek.values.sum(), streak)
}
