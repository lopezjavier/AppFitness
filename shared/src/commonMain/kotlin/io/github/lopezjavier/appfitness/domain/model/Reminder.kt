package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Kinds of in-app reminders. [CheckIn] is the weekly check-in, due on the user's chosen weekday. */
enum class ReminderKind { Weight, Circumference, CheckIn, WeekSummary, BlockEnd }

/** Kinds the user can silence; mapped 1:1 to the DB enum `notification_kind`. */
enum class NotificationKind { Weight, Circumference, WeeklyCheckIn, WeekSummary, BlockEnd }

/**
 * A derived, never stored reminder. [dueDate] is the day it became due and [days] the whole days elapsed since the
 * last log (or since the anchor day). [week] is the plan week of a [ReminderKind.WeekSummary] reminder, null otherwise.
 */
data class Reminder(val kind: ReminderKind, val dueDate: LocalDate, val days: Int, val week: Int? = null) {
    /** Whole weeks elapsed, used by the circumference text. */
    val weeks: Int get() = days / DAYS_PER_WEEK

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}

/** What the notification center needs of an open end-of-block review: the block's last day (US-109). */
data class BlockEndNotice(val endedOn: LocalDate)
