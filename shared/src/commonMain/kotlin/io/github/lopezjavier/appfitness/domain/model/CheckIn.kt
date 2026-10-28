package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate

/** Maximum length of the check-in notes (DB check `char_length <= 1000`). */
const val CHECK_IN_NOTES_MAX = 1000

/** The user's answer for the Monday-Sunday week starting on [weekStart] (a Monday). [energy] is 1-5. */
data class CheckIn(val weekStart: LocalDate, val energy: Int, val notes: String)

/**
 * The check-in window currently open: [day] is the check-in day (latest day up to today with the chosen weekday),
 * [weekStart] the Monday of its ISO week and [answer] the stored check-in of that week, if any.
 */
data class CheckInDay(val day: LocalDate, val weekStart: LocalDate, val answer: CheckIn?)

/** Training totals of the check-in week and of the week before it. */
data class PeriodTraining(val current: TrainingSummary, val previous: TrainingSummary)

/** A personal record first set in the check-in week (single-session records only). */
data class WeekRecord(
    val exerciseId: String,
    val recordType: PersonalRecordType,
    val value: Double,
    val sessionDate: LocalDate,
)

/** Weight change between the average of the check-in week and the week before. */
sealed interface WeeklyWeightTrend {
    /** [kgDelta] is this week's average minus last week's (negative = lost weight). */
    data class Delta(val kgDelta: Double) : WeeklyWeightTrend

    /** Either week has no weigh-in. */
    data object NotEnoughData : WeeklyWeightTrend
}

/** The closed-week figures shown above the check-in form. */
data class WeeklySummary(
    val training: PeriodTraining,
    val records: List<WeekRecord>,
    val weightTrend: WeeklyWeightTrend,
)

/** What the reminder load derives: the pending reminders and the check-in window (answered or not). */
data class ReminderSnapshot(val reminders: List<Reminder>, val checkIn: CheckInDay?)
