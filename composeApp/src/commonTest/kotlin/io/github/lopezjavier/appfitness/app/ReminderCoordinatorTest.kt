package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.CheckIn
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.Reminder
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import io.github.lopezjavier.appfitness.domain.repository.NotificationSettingsRepository
import io.github.lopezjavier.appfitness.domain.usecase.ComputeReminders
import io.github.lopezjavier.appfitness.domain.usecase.LoadReminders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private val TODAY = LocalDate(2026, 10, 28)

private fun day(daysAgo: Int) = LocalDate.fromEpochDays(TODAY.toEpochDays() - daysAgo)

private fun weightAt(
    daysAgo: Int,
    source: MeasurementSource = MeasurementSource.Manual,
    id: String = "w$daysAgo",
): BodyMeasurement = BodyMeasurement(id, day(daysAgo).atStartOfDayIn(TimeZone.UTC), 80.0, source)

private fun circumferenceAt(daysAgo: Int) = BodyCircumference(
    "c$daysAgo",
    CircumferenceSite.Waist,
    90.0,
    day(daysAgo).atStartOfDayIn(TimeZone.UTC),
    day(daysAgo).atStartOfDayIn(TimeZone.UTC),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderCoordinatorTest {
    private fun TestScope.coordinator(env: ReminderEnv) = ReminderCoordinator(env.load, backgroundScope)

    @Test
    fun refresh_listsWeightReminderAfterTwoDays() = runTest {
        val env = ReminderEnv(TODAY).apply { weight.rows += weightAt(3) }
        val coordinator = coordinator(env)

        coordinator.refresh()

        assertEquals(listOf(Reminder(ReminderKind.Weight, day(1), 3)), coordinator.reminders.value)
    }

    @Test
    fun weighingAfterRefresh_clearsTheReminderOnNextRefresh() = runTest {
        val env = ReminderEnv(TODAY).apply { weight.rows += weightAt(3) }
        val coordinator = coordinator(env)
        coordinator.refresh()

        env.weight.rows += weightAt(0, id = "today")
        coordinator.refresh()

        assertTrue(coordinator.reminders.value.isEmpty())
    }

    @Test
    fun healthConnectWeight_clearsTheReminder() = runTest {
        val env = ReminderEnv(TODAY).apply { weight.rows += weightAt(5) }
        val coordinator = coordinator(env)
        coordinator.refresh()

        env.weight.rows += weightAt(0, MeasurementSource.HealthConnect, "hc")
        coordinator.refresh()

        assertTrue(coordinator.reminders.value.isEmpty())
    }

    @Test
    fun savingCircumferences_clearsTheCircumferenceReminder() = runTest {
        val env = ReminderEnv(TODAY).apply {
            weight.rows += weightAt(0)
            circumferences.rows += circumferenceAt(20)
        }
        val coordinator = coordinator(env)
        coordinator.refresh()
        assertEquals(listOf(ReminderKind.Circumference), coordinator.reminders.value.map { it.kind })

        env.circumferences.rows += circumferenceAt(0)
        coordinator.refresh()

        assertTrue(coordinator.reminders.value.isEmpty())
    }

    @Test
    fun silencedKind_isNotListed() = runTest {
        val env = ReminderEnv(TODAY).apply {
            weight.rows += weightAt(9)
            settings.values[NotificationKind.Weight] = false
        }
        val coordinator = coordinator(env)

        coordinator.refresh()

        assertTrue(coordinator.reminders.value.isEmpty())
    }

    @Test
    fun failedRefresh_keepsLastRemindersAndReportsFailure() = runTest {
        val env = ReminderEnv(TODAY).apply { weight.rows += weightAt(4) }
        val coordinator = coordinator(env)
        coordinator.refresh()
        val before = coordinator.reminders.value

        env.settings.failReads = true
        val result = coordinator.refresh()

        assertTrue(result.isFailure)
        assertEquals(before, coordinator.reminders.value)
    }

    @Test
    fun requestRefresh_updatesInTheBackground() = runTest {
        val env = ReminderEnv(TODAY).apply { weight.rows += weightAt(4) }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = ReminderCoordinator(env.load, scope)

        coordinator.requestRefresh()
        advanceUntilIdle()

        assertEquals(1, coordinator.reminders.value.size)
    }

    @Test
    fun staleSlowRefresh_doesNotOverwriteNewerResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val settings = object : NotificationSettingsRepository {
            override suspend fun getAll(): Result<Map<NotificationKind, Boolean>> {
                if (++calls == 1) {
                    gate.await()
                    // The first (slow) read sees the weight reminder silenced: an outdated answer.
                    return Result.success(mapOf(NotificationKind.Weight to false))
                }
                return Result.success(emptyMap())
            }

            override suspend fun setEnabled(kind: NotificationKind, enabled: Boolean): Result<Unit> =
                Result.success(Unit)
        }
        val env = ReminderEnv(TODAY).apply { weight.rows += weightAt(4) }
        val load = LoadReminders(
            env.weight,
            env.circumferences,
            settings,
            env.profile,
            env.checkIns,
            env.clock,
            env.timeZone,
            ComputeReminders(),
        )
        val coordinator = ReminderCoordinator(load, backgroundScope)
        val slow = launch { coordinator.refresh() }
        runCurrent()

        coordinator.refresh()
        gate.complete(Unit)
        slow.join()

        assertEquals(1, coordinator.reminders.value.size)
    }

    @Test
    fun clear_emptiesTheList() = runTest {
        val env = ReminderEnv(TODAY).apply { weight.rows += weightAt(4) }
        val coordinator = coordinator(env)
        coordinator.refresh()

        coordinator.clear()

        assertTrue(coordinator.reminders.value.isEmpty())
    }

    private fun checkInEnv() = ReminderEnv(TODAY).apply {
        weight.rows += weightAt(0)
        weight.onboarding = weightAt(20, id = "onb")
        circumferences.rows += circumferenceAt(0)
    }

    @Test
    fun refresh_exposesTheUnansweredCheckInAsReminderAndWindow() = runTest {
        val coordinator = coordinator(checkInEnv())

        coordinator.refresh()

        assertEquals(listOf(ReminderKind.CheckIn), coordinator.reminders.value.map { it.kind })
        assertEquals(LocalDate(2026, 10, 25), coordinator.checkIn.value?.day)
    }

    @Test
    fun answeredCheckIn_staysInTheWindowButLeavesTheCount() = runTest {
        val env = checkInEnv()
        val coordinator = coordinator(env)
        coordinator.refresh()

        env.checkIns.save(CheckIn(LocalDate(2026, 10, 19), 5, "")).getOrThrow()
        coordinator.refresh()

        assertTrue(coordinator.reminders.value.isEmpty())
        assertEquals(5, coordinator.checkIn.value?.answer?.energy)
    }

    @Test
    fun clear_alsoForgetsTheCheckInWindow() = runTest {
        val coordinator = coordinator(checkInEnv())
        coordinator.refresh()

        coordinator.clear()

        assertEquals(null, coordinator.checkIn.value)
    }

    @Test
    fun failedRefresh_keepsTheCheckInWindow() = runTest {
        val env = checkInEnv()
        val coordinator = coordinator(env)
        coordinator.refresh()
        val before = coordinator.checkIn.value

        env.checkIns.failReads = true
        coordinator.refresh()

        assertEquals(before, coordinator.checkIn.value)
    }

    @Test
    fun localDayBoundary_usesTheDeviceTimeZone() = runTest {
        // 23:30 UTC on 25 Oct is already 26 Oct in +02:00; with today = 28 Oct that is exactly two days.
        val zone = TimeZone.of("Europe/Madrid")
        val env = ReminderEnv(TODAY, zone)
        val instant: Instant = LocalDate(2026, 10, 25).atTime(23, 30).toInstant(TimeZone.UTC)
        env.weight.rows += BodyMeasurement("w", instant, 80.0, MeasurementSource.Manual)
        val coordinator = coordinator(env)

        coordinator.refresh()

        assertEquals(2, coordinator.reminders.value.single().days)
    }

    @Test
    fun neverMeasured_circumferenceAnchorsOnOnboardingDay() = runTest {
        val env = ReminderEnv(TODAY).apply {
            weight.rows += weightAt(0)
            weight.onboarding = weightAt(15, id = "onb")
            settings.values[NotificationKind.WeeklyCheckIn] = false
        }
        val coordinator = coordinator(env)

        coordinator.refresh()

        assertEquals(listOf(ReminderKind.Circumference), coordinator.reminders.value.map { it.kind })
    }
}
