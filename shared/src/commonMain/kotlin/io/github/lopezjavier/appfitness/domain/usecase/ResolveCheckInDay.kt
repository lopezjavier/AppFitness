package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CheckInDay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

private const val DAYS_PER_WEEK = 7

/** Monday of the ISO week containing [this]. */
fun LocalDate.mondayOfWeek(): LocalDate = minus(DatePeriod(days = dayOfWeek.ordinal))

/**
 * The check-in window open at [today]: the latest day D up to today whose ISO weekday ([weekday], 1 Monday .. 7 Sunday)
 * is the chosen one. Null when [firstDay] (the onboarding weight day) is unknown or D is before it. The week is the
 * Monday-Sunday week containing D; the answer is attached by the caller.
 */
class ResolveCheckInDay {
    operator fun invoke(today: LocalDate, weekday: Int, firstDay: LocalDate?): CheckInDay? {
        if (firstDay == null) return null
        val back = (today.dayOfWeek.ordinal + 1 - weekday + DAYS_PER_WEEK) % DAYS_PER_WEEK
        val day = today.minus(DatePeriod(days = back))
        return if (day < firstDay) null else CheckInDay(day, day.mondayOfWeek(), answer = null)
    }
}
