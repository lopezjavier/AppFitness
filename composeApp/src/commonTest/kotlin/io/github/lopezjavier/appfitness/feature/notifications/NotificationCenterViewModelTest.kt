package io.github.lopezjavier.appfitness.feature.notifications

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.app.ReminderEnv
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
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
        val connectivity: FakeConnectivityObserver,
    )

    private fun TestScope.fixture(env: ReminderEnv = env()): Fixture {
        val connectivity = FakeConnectivityObserver(initial = true)
        val coordinator = ReminderCoordinator(env.load, backgroundScope)
        val viewModel = NotificationCenterViewModel(coordinator, env.settings, connectivity, env.clock)
        return Fixture(env, coordinator, viewModel, connectivity)
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
            assertTrue(content.weightEnabled && content.circumferenceEnabled)
            assertEquals(TODAY, content.today)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun silencingWeight_removesItsReminderAndLowersTheCount() = runTest {
        val f = fixture()

        f.viewModel.uiState.test {
            content()
            f.viewModel.onEvent(NotificationCenterEvent.SetEnabled(NotificationKind.Weight, false))
            var last = content()
            while (last.weightEnabled || last.reminders.size != 1) last = content()
            assertEquals(ReminderKind.Circumference, last.reminders.single().kind)
            advanceUntilIdle()
            assertEquals(1, f.coordinator.reminders.value.size)
            assertEquals(false, f.env.settings.values[NotificationKind.Weight])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun silencingThenEnablingAgain_restoresTheReminder() = runTest {
        val f = fixture()

        f.viewModel.uiState.test {
            content()
            f.viewModel.onEvent(NotificationCenterEvent.SetEnabled(NotificationKind.Weight, false))
            advanceUntilIdle()
            f.viewModel.onEvent(NotificationCenterEvent.SetEnabled(NotificationKind.Weight, true))
            advanceUntilIdle()
            var last = content()
            while (last.reminders.size != 2) last = content()
            assertTrue(last.weightEnabled)
            assertEquals(2, f.coordinator.reminders.value.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failedToggle_rollsTheSwitchBackAndEmitsAnEffect() = runTest {
        val f = fixture()
        f.env.settings.failWrites = true

        f.viewModel.uiState.test {
            content()
            f.viewModel.effects.test {
                f.viewModel.onEvent(NotificationCenterEvent.SetEnabled(NotificationKind.Weight, false))
                advanceUntilIdle()
                assertEquals(NotificationCenterEffect.ToggleFailed, awaitItem())
            }
            advanceUntilIdle()
            val state = f.viewModel.uiState.value
            assertIs<NotificationCenterUiState.Content>(state)
            assertTrue(state.weightEnabled)
            assertEquals(2, state.reminders.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun offline_isReportedSoTheSwitchesCanBeDisabled() = runTest {
        val f = fixture()
        f.connectivity.state.value = false

        f.viewModel.uiState.test {
            var last = content()
            while (!last.isOffline) last = content()
            assertEquals(2, last.reminders.size)
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
