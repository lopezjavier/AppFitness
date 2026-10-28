package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Kinds of in-app reminders. [CheckIn] is reserved for the weekly check-in (T-136) and is never produced yet. */
enum class ReminderKind { Weight, Circumference, CheckIn }

/** Kinds the user can silence; mapped 1:1 to the DB enum `notification_kind`. */
enum class NotificationKind { Weight, Circumference, WeeklyCheckIn }

/**
 * A derived, never stored reminder. [dueDate] is the day it became due and [days] the whole days elapsed since the
 * last log (or since the anchor day).
 */
data class Reminder(val kind: ReminderKind, val dueDate: LocalDate, val days: Int) {
    /** Whole weeks elapsed, used by the circumference text. */
    val weeks: Int get() = days / DAYS_PER_WEEK

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}
