package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun healthConnectWeightToday_clearsTheWeightReminder() = runTest {
        val env = ReminderEnv(today)
        env.weight.rows += weightAtInstant(utc(LocalDate(2026, 10, 20), 10, 0))
        env.weight.rows += weightAtInstant(utc(today, 8, 0), MeasurementSource.HealthConnect)

        val reminders = env.load().getOrThrow()

        assertTrue(reminders.none { it.kind == ReminderKind.Weight })
    }
}
