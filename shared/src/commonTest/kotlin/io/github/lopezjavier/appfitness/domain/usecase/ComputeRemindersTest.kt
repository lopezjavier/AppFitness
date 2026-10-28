package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.CheckIn
import io.github.lopezjavier.appfitness.domain.model.CheckInDay
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.Reminder
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComputeRemindersTest {
    private val today = LocalDate(2026, 10, 28)
    private val compute = ComputeReminders()

    private fun daysAgo(n: Int) = LocalDate.fromEpochDays(today.toEpochDays() - n)

    private fun run(
        lastWeight: LocalDate? = today,
        lastCircumference: LocalDate? = today,
        first: LocalDate? = null,
        silenced: Set<NotificationKind> = emptySet(),
    ) = compute(today, lastWeight, lastCircumference, first, silenced)

    @Test
    fun weighedYesterday_hasNoWeightReminder() {
        assertTrue(run(lastWeight = daysAgo(1)).isEmpty())
    }

    @Test
    fun weighedToday_hasNoWeightReminder() {
        assertTrue(run(lastWeight = today).isEmpty())
    }

    @Test
    fun twoDaysWithoutWeighing_listsWeightReminderDueLastPlusTwo() {
        val result = run(lastWeight = daysAgo(2))

        assertEquals(listOf(Reminder(ReminderKind.Weight, today, 2)), result)
    }

    @Test
    fun fiveDaysWithoutWeighing_showsRealDaysAndDueDate() {
        val result = run(lastWeight = daysAgo(5))

        assertEquals(listOf(Reminder(ReminderKind.Weight, daysAgo(3), 5)), result)
    }

    @Test
    fun noWeightEverAndNoAnchor_hasNoWeightReminder() {
        assertTrue(run(lastWeight = null, lastCircumference = null).isEmpty())
    }

    @Test
    fun circumference13Days_hasNoReminder() {
        assertTrue(run(lastCircumference = daysAgo(13)).isEmpty())
    }

    @Test
    fun circumference14Days_listsTwoWeeks() {
        val result = run(lastCircumference = daysAgo(14))

        assertEquals(listOf(Reminder(ReminderKind.Circumference, today, 14)), result)
        assertEquals(2, result.single().weeks)
    }

    @Test
    fun circumference15Days_stillTwoWeeks() {
        assertEquals(2, run(lastCircumference = daysAgo(15)).single().weeks)
    }

    @Test
    fun circumference21Days_threeWeeks() {
        assertEquals(3, run(lastCircumference = daysAgo(21)).single().weeks)
    }

    @Test
    fun neverMeasured_anchorsOnFirstDay() {
        assertTrue(run(lastCircumference = null, first = daysAgo(13)).isEmpty())
        val result = run(lastCircumference = null, first = daysAgo(14))
        assertEquals(listOf(Reminder(ReminderKind.Circumference, today, 14)), result)
    }

    @Test
    fun neverMeasuredWithoutAnchor_hasNoCircumferenceReminder() {
        assertTrue(run(lastCircumference = null, first = null).isEmpty())
    }

    @Test
    fun silencedWeight_isSkipped() {
        val result =
            run(lastWeight = daysAgo(3), lastCircumference = daysAgo(15), silenced = setOf(NotificationKind.Weight))

        assertEquals(listOf(ReminderKind.Circumference), result.map { it.kind })
    }

    @Test
    fun silencedCircumference_isSkipped() {
        val result = run(
            lastWeight = daysAgo(3),
            lastCircumference = daysAgo(15),
            silenced = setOf(NotificationKind.Circumference),
        )

        assertEquals(listOf(ReminderKind.Weight), result.map { it.kind })
    }

    @Test
    fun bothSilenced_isEmpty() {
        val both = setOf(NotificationKind.Weight, NotificationKind.Circumference)

        assertTrue(run(lastWeight = daysAgo(9), lastCircumference = daysAgo(30), silenced = both).isEmpty())
    }

    @Test
    fun ordering_isDueDateDescendingThenKind() {
        // Weight due = today - 3; circumference last 30 days ago is due today - 16.
        val result = run(lastWeight = daysAgo(5), lastCircumference = daysAgo(30))

        assertEquals(listOf(ReminderKind.Weight, ReminderKind.Circumference), result.map { it.kind })
    }

    private fun checkInDay(answered: Boolean = false, day: LocalDate = daysAgo(1)) = CheckInDay(
        day,
        day,
        if (answered) CheckIn(day, 3, "") else null,
    )

    private fun runWithCheckIn(
        checkIn: CheckInDay?,
        silenced: Set<NotificationKind> = emptySet(),
        lastWeight: LocalDate? = today,
        lastCircumference: LocalDate? = today,
    ) = compute(today, lastWeight, lastCircumference, null, silenced, checkIn)

    @Test
    fun pendingCheckIn_isListedDueOnItsDay() {
        val result = runWithCheckIn(checkInDay())

        assertEquals(listOf(Reminder(ReminderKind.CheckIn, daysAgo(1), 0)), result)
    }

    @Test
    fun answeredCheckIn_isNotListed() {
        assertTrue(runWithCheckIn(checkInDay(answered = true)).isEmpty())
    }

    @Test
    fun silencedCheckIn_isNotListed() {
        assertTrue(runWithCheckIn(checkInDay(), silenced = setOf(NotificationKind.WeeklyCheckIn)).isEmpty())
    }

    @Test
    fun noCheckInWindow_isNotListed() {
        assertTrue(runWithCheckIn(null).isEmpty())
    }

    @Test
    fun checkIn_sortsByDueDateAmongTheOthers() {
        // Weight due today - 3, circumference due today - 16, check-in due today - 1.
        val result = runWithCheckIn(checkInDay(), lastWeight = daysAgo(5), lastCircumference = daysAgo(30))

        assertEquals(
            listOf(ReminderKind.CheckIn, ReminderKind.Weight, ReminderKind.Circumference),
            result.map { it.kind },
        )
        assertEquals(3, result.size)
    }

    @Test
    fun checkIn_sameDueDate_sortsAfterWeightAndCircumference() {
        val result = runWithCheckIn(checkInDay(day = daysAgo(3)), lastWeight = daysAgo(5))

        assertEquals(listOf(ReminderKind.Weight, ReminderKind.CheckIn), result.map { it.kind })
    }

    @Test
    fun ordering_sameDueDate_weightBeforeCircumference() {
        // Weight 20 days ago -> due today - 18; circumference 32 days ago -> due today - 18.
        val result = run(lastWeight = daysAgo(20), lastCircumference = daysAgo(32))

        assertEquals(listOf(ReminderKind.Weight, ReminderKind.Circumference), result.map { it.kind })
    }
}
