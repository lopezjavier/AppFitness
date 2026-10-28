package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CheckInDay
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.Reminder
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private const val WEIGHT_AFTER_DAYS = 2
private const val CIRCUMFERENCE_AFTER_DAYS = 14

/**
 * Derives the pending reminders (US-58). Weight is due once [WEIGHT_AFTER_DAYS] days passed since the last weigh-in;
 * circumferences after [CIRCUMFERENCE_AFTER_DAYS] days since the last measurement, or since [firstDay] (the
 * onboarding weight day) when none was ever taken. The weekly check-in is due on its check-in day while unanswered.
 * Silenced kinds are skipped. Newest due date first, then by kind.
 */
class ComputeReminders {
    operator fun invoke(
        today: LocalDate,
        lastWeightDay: LocalDate?,
        lastCircumferenceDay: LocalDate?,
        firstDay: LocalDate?,
        silenced: Set<NotificationKind>,
        checkIn: CheckInDay? = null,
    ): List<Reminder> {
        val reminders = buildList {
            if (checkIn != null && checkIn.answer == null && NotificationKind.WeeklyCheckIn !in silenced) {
                add(Reminder(ReminderKind.CheckIn, checkIn.day, days = 0))
            }
            if (NotificationKind.Weight !in silenced) {
                due(ReminderKind.Weight, today, lastWeightDay, WEIGHT_AFTER_DAYS)?.let(::add)
            }
            if (NotificationKind.Circumference !in silenced) {
                due(ReminderKind.Circumference, today, lastCircumferenceDay ?: firstDay, CIRCUMFERENCE_AFTER_DAYS)
                    ?.let(::add)
            }
        }
        return reminders.sortedWith(compareByDescending<Reminder> { it.dueDate }.thenBy { it.kind })
    }

    private fun due(kind: ReminderKind, today: LocalDate, anchor: LocalDate?, afterDays: Int): Reminder? {
        val days = anchor?.let { (today.toEpochDays() - it.toEpochDays()).toInt() }
        return if (anchor == null || days == null || days < afterDays) {
            null
        } else {
            Reminder(kind, anchor.plus(DatePeriod(days = afterDays)), days)
        }
    }
}
