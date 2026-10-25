package io.github.lopezjavier.appfitness.domain.usecase

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/** ISO weekday numbers (Monday = 1 ... Sunday = 7), as stored in `meal_defaults.weekday`. */
object IsoWeekday {
    const val MONDAY = 1
    const val SUNDAY = 7

    /** Every day of the week in order, Monday first. */
    val ALL: List<Int> = (MONDAY..SUNDAY).toList()

    fun of(date: LocalDate): Int = date.dayOfWeek.isoDayNumber
}
