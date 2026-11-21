package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.feature.trainer.blockReview
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun utc(date: LocalDate, hour: Int, minute: Int): Instant = date.atTime(hour, minute).toInstant(TimeZone.UTC)

private fun weightAtInstant(at: Instant, source: MeasurementSource = MeasurementSource.Manual) =
    BodyMeasurement("w", at, 80.0, source)

private fun circumferenceAtInstant(at: Instant) = BodyCircumference("c", CircumferenceSite.Waist, 90.0, at, at)

class LoadRemindersTest {
    private val today = LocalDate(2026, 10, 28)

    @Test
    fun weightDay_plusTwoOffset_movesPastMidnightIntoNextLocalDay() = runTest {
        val env = ReminderEnv(today, TimeZone.of("+02:00"))
        env.weight.rows += weightAtInstant(utc(LocalDate(2026, 10, 25), 23, 30))

        val reminders = env.load().getOrThrow()

        assertEquals(2, reminders.single { it.kind == ReminderKind.Weight }.days)
    }

    @Test
    fun weightDay_minusFiveOffset_staysOnPreviousLocalDay() = runTest {
        val env = ReminderEnv(today, TimeZone.of("-05:00"))
        env.weight.rows += weightAtInstant(utc(LocalDate(2026, 10, 25), 23, 30))

        val reminders = env.load().getOrThrow()

        assertEquals(3, reminders.single { it.kind == ReminderKind.Weight }.days)
    }

    @Test
    fun weightDay_afterDstEnds_usesStandardOffset() = runTest {
        // New York leaves DST on 1 Nov 2026: 04:30 UTC on 2 Nov is 23:30 EST on 1 Nov (00:30 if DST still applied).
        val env = ReminderEnv(LocalDate(2026, 11, 5), TimeZone.of("America/New_York"))
        env.weight.rows += weightAtInstant(utc(LocalDate(2026, 11, 2), 4, 30))

        val reminders = env.load().getOrThrow()

        assertEquals(4, reminders.single { it.kind == ReminderKind.Weight }.days)
    }

    @Test
    fun circumferenceDay_usesTheLocalTimeZone() = runTest {
        val at = utc(LocalDate(2026, 10, 13), 23, 30)
        val plusTwo = ReminderEnv(today, TimeZone.of("+02:00")).apply {
            weight.rows += weightAtInstant(utc(today, 12, 0))
            circumferences.rows += circumferenceAtInstant(at)
        }
        val minusFive = ReminderEnv(today, TimeZone.of("-05:00")).apply {
            weight.rows += weightAtInstant(utc(today, 12, 0))
            circumferences.rows += circumferenceAtInstant(at)
        }

        assertEquals(14, plusTwo.load().getOrThrow().single().days)
        assertEquals(15, minusFive.load().getOrThrow().single().days)
    }

    @Test
    fun missingSettingsRow_countsAsEnabled() = runTest {
        val env = ReminderEnv(today)
        env.weight.rows += weightAtInstant(utc(LocalDate(2026, 10, 20), 10, 0))

        val reminders = env.load().getOrThrow()

        assertEquals(listOf(ReminderKind.Weight), reminders.map { it.kind })
    }

    @Test
    fun explicitlyEnabledRow_isListed() = runTest {
        val env = ReminderEnv(today)
        env.settings.values[NotificationKind.Weight] = true
        env.weight.rows += weightAtInstant(utc(LocalDate(2026, 10, 20), 10, 0))

        assertEquals(1, env.load().getOrThrow().size)
    }

    @Test
    fun disabledRow_silencesOnlyThatKind() = runTest {
        val env = ReminderEnv(today)
        env.settings.values[NotificationKind.Weight] = false
        env.weight.rows += weightAtInstant(utc(LocalDate(2026, 10, 20), 10, 0))
        env.circumferences.rows += circumferenceAtInstant(utc(LocalDate(2026, 9, 1), 10, 0))

        val reminders = env.load().getOrThrow()

        assertEquals(listOf(ReminderKind.Circumference), reminders.map { it.kind })
    }

    @Test
    fun failedWeightRead_returnsFailure() = runTest {
        val env = ReminderEnv(today).apply { weight.failReads = true }

        assertTrue(env.load().isFailure)
    }

    @Test
    fun failedCircumferenceRead_returnsFailure() = runTest {
        val env = ReminderEnv(today).apply { circumferences.failReads = true }

        assertTrue(env.load().isFailure)
    }

    @Test
    fun failedSettingsRead_returnsFailure() = runTest {
        val env = ReminderEnv(today).apply { settings.failReads = true }

        assertTrue(env.load().isFailure)
    }

    private fun summary(seen: Boolean = false) = WeekSummary(
        planId = "p1",
        week = 3,
        startDate = LocalDate(2026, 10, 19),
        endDate = LocalDate(2026, 10, 25),
        seen = seen,
        sessionsDone = 3,
        sessionsPlanned = 4,
        groups = emptyList(),
        weight = null,
        completeDays = 5,
        weighInDays = 3,
        changes = emptyList(),
        deload = false,
    )

    private suspend fun weekSummaryReminders(env: ReminderEnv) =
        env.load().getOrThrow().filter { it.kind == ReminderKind.WeekSummary }

    @Test
    fun unseenWeekSummary_isListedDueOnTheWeekEnd() = runTest {
        val env = ReminderEnv(today).apply { summaries.latest = Result.success(summary()) }

        val reminder = weekSummaryReminders(env).single()

        assertEquals(LocalDate(2026, 10, 25), reminder.dueDate)
    }

    @Test
    fun pausedWeekSummary_isNotListedEvenWhenUnseen() = runTest {
        val paused = summary().copy(paused = true, seen = false)
        val env = ReminderEnv(today).apply { summaries.latest = Result.success(paused) }

        assertTrue(weekSummaryReminders(env).isEmpty())
        assertNull(paused.toNotice())
    }

    @Test
    fun seenWeekSummary_isNotListed() = runTest {
        val env = ReminderEnv(today).apply { summaries.latest = Result.success(summary(seen = true)) }

        assertTrue(weekSummaryReminders(env).isEmpty())
    }

    @Test
    fun silencedWeekSummary_isNotListed() = runTest {
        val env = ReminderEnv(today).apply {
            summaries.latest = Result.success(summary())
            settings.values[NotificationKind.WeekSummary] = false
        }

        assertTrue(weekSummaryReminders(env).isEmpty())
    }

    @Test
    fun noWeekSummary_isNotListed() = runTest {
        assertTrue(weekSummaryReminders(ReminderEnv(today)).isEmpty())
    }

    @Test
    fun failedWeekSummaryRead_returnsFailure() = runTest {
        val env = ReminderEnv(today).apply {
            summaries.latest = Result.failure(AppException(AppError.Network))
        }

        assertTrue(env.load().isFailure)
    }

    private suspend fun blockEndReminders(env: ReminderEnv) =
        env.load().getOrThrow().filter { it.kind == ReminderKind.BlockEnd }

    @Test
    fun openBlockReview_isListedDueTheDayAfterTheLastDay() = runTest {
        val env = ReminderEnv(today).apply { reviews.review = Result.success(blockReview()) }

        val reminder = blockEndReminders(env).single()

        assertEquals(blockReview().endedOn.plus(DatePeriod(days = 1)), reminder.dueDate)
    }

    @Test
    fun seenBlockReview_isNotListed() = runTest {
        val env = ReminderEnv(today).apply { reviews.review = Result.success(blockReview(seen = true)) }

        assertTrue(blockEndReminders(env).isEmpty())
    }

    @Test
    fun unseenBlockReview_isListedAndLeavesOnceMarkedSeen() = runTest {
        val env = ReminderEnv(today).apply { reviews.review = Result.success(blockReview(seen = false)) }
        assertEquals(1, blockEndReminders(env).size)

        env.reviews.markSeen("review-1")

        assertTrue(blockEndReminders(env).isEmpty())
    }

    @Test
    fun seenBlockReview_doesNotHideTheOtherReminders() = runTest {
        val env = ReminderEnv(today).apply {
            reviews.review = Result.success(blockReview(seen = true))
            weight.rows += weightAtInstant(utc(LocalDate(2026, 10, 20), 10, 0))
        }

        assertEquals(listOf(ReminderKind.Weight), env.load().getOrThrow().map { it.kind })
    }

    @Test
    fun noOpenBlockReview_isNotListed() = runTest {
        assertTrue(blockEndReminders(ReminderEnv(today)).isEmpty())
    }

    @Test
    fun silencedBlockEnd_isNotListedAndTheReviewIsNotOpened() = runTest {
        val env = ReminderEnv(today).apply {
            reviews.review = Result.success(blockReview())
            settings.values[NotificationKind.BlockEnd] = false
        }

        assertTrue(blockEndReminders(env).isEmpty())
        assertEquals(0, env.reviews.opens)
    }

    @Test
    fun failedBlockReviewRead_hidesOnlyTheBlockEndItem() = runTest {
        val env = ReminderEnv(today).apply {
            reviews.review = Result.failure(AppException(AppError.Network))
            weight.rows += weightAtInstant(utc(LocalDate(2026, 10, 20), 10, 0))
        }

        val result = env.load()

        assertTrue(result.isSuccess)
        assertEquals(listOf(ReminderKind.Weight), result.getOrThrow().map { it.kind })
    }

    @Test
    fun blockEnd_staysListedOnALaterDayAndLeavesOnceTheReviewIsClosed() = runTest {
        val env = ReminderEnv(today).apply { reviews.review = Result.success(blockReview()) }
        assertEquals(1, blockEndReminders(env).size)

        env.clock.day = today.plus(DatePeriod(days = 3))
        assertEquals(1, blockEndReminders(env).size)

        env.reviews.review = Result.success(null)
        assertTrue(blockEndReminders(env).isEmpty())
    }

    @Test
    fun healthConnectOnly_noCircumference_listsTheCircumferenceReminder14DaysAfterTheFirstWeighIn() = runTest {
        // 23:30 UTC on 13 Oct is 01:30 on 14 Oct at +02:00: 14 local days before today (15 in UTC).
        val env = ReminderEnv(today, TimeZone.of("+02:00")).apply {
            weight.rows += weightAtInstant(utc(LocalDate(2026, 10, 13), 23, 30), MeasurementSource.HealthConnect)
            weight.rows += weightAtInstant(utc(today, 6, 0), MeasurementSource.HealthConnect)
        }

        val reminder = env.load().getOrThrow().single()

        assertEquals(ReminderKind.Circumference, reminder.kind)
        assertEquals(today, reminder.dueDate)
        assertEquals(14, reminder.days)
        assertEquals(2, reminder.weeks)
    }

    @Test
    fun earliestWeighInOfAnySource_isTheAnchor_evenWithALaterManualRow() = runTest {
        val manual = weightAtInstant(utc(LocalDate(2026, 10, 18), 8, 0))
        val env = ReminderEnv(today).apply {
            weight.rows += weightAtInstant(utc(LocalDate(2026, 10, 12), 8, 0), MeasurementSource.HealthConnect)
            weight.rows += manual
            weight.rows += weightAtInstant(utc(today, 8, 0))
            weight.onboarding = manual
        }

        val reminder = env.load().getOrThrow().single { it.kind == ReminderKind.Circumference }

        assertEquals(16, reminder.days)
    }

    @Test
    fun failedEarliestWeightRead_returnsFailure() = runTest {
        val env = ReminderEnv(today).apply { weight.failEarliestRead = true }

        assertTrue(env.load().isFailure)
    }

    @Test
    fun healthConnectWeightToday_clearsTheWeightReminder() = runTest {
        val env = ReminderEnv(today)
        env.weight.rows += weightAtInstant(utc(LocalDate(2026, 10, 20), 10, 0))
        env.weight.rows += weightAtInstant(utc(today, 8, 0), MeasurementSource.HealthConnect)

        val reminders = env.load().getOrThrow()

        assertTrue(reminders.none { it.kind == ReminderKind.Weight })
    }
}
