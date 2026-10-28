package io.github.lopezjavier.appfitness.feature.notifications

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.app.ReminderEnv
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.CheckIn
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val TODAY = LocalDate(2026, 10, 28)

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCenterViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun day(daysAgo: Int) = LocalDate.fromEpochDays(TODAY.toEpochDays() - daysAgo)

    /** Weight last logged 3 days ago and circumferences 21 days ago: both reminders are pending. */
    private fun env() = ReminderEnv(TODAY).apply {
        weight.rows += BodyMeasurement("w", day(3).atStartOfDayIn(TimeZone.UTC), 80.0, MeasurementSource.Manual)
        circumferences.rows += BodyCircumference(
            "c",
            CircumferenceSite.Waist,
            90.0,
            day(21).atStartOfDayIn(TimeZone.UTC),
            day(21).atStartOfDayIn(TimeZone.UTC),
        )
    }

    private class Fixture(
        val env: ReminderEnv,
        val coordinator: ReminderCoordinator,
        val viewModel: NotificationCenterViewModel,
    )

    private fun TestScope.fixture(env: ReminderEnv = env()): Fixture {
        val coordinator = ReminderCoordinator(env.load, backgroundScope)
        val viewModel = NotificationCenterViewModel(coordinator, env.settings, env.clock)
        return Fixture(env, coordinator, viewModel)
    }

    private suspend fun ReceiveTurbine<NotificationCenterUiState>.content(): NotificationCenterUiState.Content {
        var item = awaitItem()
        while (item !is NotificationCenterUiState.Content) item = awaitItem()
        return item
    }

    @Test
    fun loading_listsThePendingReminders() = runTest {
        val f = fixture()

        f.viewModel.uiState.test {
            val content = content()
            assertEquals(
                setOf(ReminderKind.Weight, ReminderKind.Circumference),
                content.reminders.map { it.kind }.toSet(),
            )
            assertEquals(TODAY, content.today)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSilencedKind_isNotListed() = runTest {
        val env = env().apply { settings.values[NotificationKind.Weight] = false }
        val f = fixture(env)

        f.viewModel.uiState.test {
            assertEquals(listOf(ReminderKind.Circumference), content().reminders.map { it.kind })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refreshAfterSilencingInPreferencias_dropsTheReminder() = runTest {
        val f = fixture()

        f.viewModel.uiState.test {
            assertEquals(2, content().reminders.size)
            f.env.settings.values[NotificationKind.Weight] = false
            f.viewModel.onEvent(NotificationCenterEvent.Refresh)
            var last = content()
            while (last.reminders.size != 1) last = content()
            assertEquals(ReminderKind.Circumference, last.reminders.single().kind)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun silencedCheckIn_hidesTheReminderAndTheAnsweredRow() = runTest {
        val env = checkInEnv().apply { settings.values[NotificationKind.WeeklyCheckIn] = false }
        env.checkIns.rows[LocalDate(2026, 10, 19)] = CheckIn(LocalDate(2026, 10, 19), 4, "ok")
        val f = fixture(env)

        f.viewModel.uiState.test {
            val content = content()
            assertTrue(content.reminders.isEmpty())
            assertEquals(null, content.checkIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Both measurement reminders cleared and the onboarding day known: only the Sunday 25 Oct check-in is open. */
    private fun checkInEnv() = ReminderEnv(TODAY).apply {
        weight.rows += BodyMeasurement("w", TODAY.atStartOfDayIn(TimeZone.UTC), 80.0, MeasurementSource.Manual)
        weight.onboarding = BodyMeasurement("o", day(20).atStartOfDayIn(TimeZone.UTC), 80.0, MeasurementSource.Manual)
        circumferences.rows += BodyCircumference(
            "c",
            CircumferenceSite.Waist,
            90.0,
            TODAY.atStartOfDayIn(TimeZone.UTC),
            TODAY.atStartOfDayIn(TimeZone.UTC),
        )
    }

    @Test
    fun pendingCheckIn_isListedAsAReminderWithItsWindow() = runTest {
        val f = fixture(checkInEnv())

        f.viewModel.uiState.test {
            val content = content()
            assertEquals(listOf(ReminderKind.CheckIn), content.reminders.map { it.kind })
            assertEquals(null, content.checkIn?.answer)
            assertEquals(LocalDate(2026, 10, 19), content.checkIn?.weekStart)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun answeredCheckIn_isReachableButNotCounted() = runTest {
        val env = checkInEnv()
        env.checkIns.rows[LocalDate(2026, 10, 19)] = CheckIn(LocalDate(2026, 10, 19), 4, "ok")
        val f = fixture(env)

        f.viewModel.uiState.test {
            val content = content()
            assertTrue(content.reminders.isEmpty())
            assertEquals(4, content.checkIn?.answer?.energy)
            assertTrue(f.coordinator.reminders.value.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failedRead_showsErrorAndRetryRecovers() = runTest {
        val env = env().apply { settings.failReads = true }
        val f = fixture(env)

        f.viewModel.uiState.test {
            var item = awaitItem()
            while (item !is NotificationCenterUiState.Error) item = awaitItem()
            env.settings.failReads = false
            f.viewModel.onEvent(NotificationCenterEvent.Retry)
            assertIs<NotificationCenterUiState.Content>(content())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun quietRefreshFailure_keepsTheContentOnScreen() = runTest {
        val f = fixture()

        f.viewModel.uiState.test {
            content()
            f.env.settings.failReads = true
            f.viewModel.onEvent(NotificationCenterEvent.Refresh)
            advanceUntilIdle()
            val state = f.viewModel.uiState.value
            assertIs<NotificationCenterUiState.Content>(state)
            assertEquals(2, state.reminders.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun loggingAWeight_clearsItsReminderOnRefresh() = runTest {
        val f = fixture()

        f.viewModel.uiState.test {
            content()
            f.env.weight.rows +=
                BodyMeasurement("w2", TODAY.atStartOfDayIn(TimeZone.UTC), 79.0, MeasurementSource.Manual)
            f.viewModel.onEvent(NotificationCenterEvent.Refresh)
            var last = content()
            while (last.reminders.size != 1) last = content()
            assertEquals(ReminderKind.Circumference, last.reminders.single().kind)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
