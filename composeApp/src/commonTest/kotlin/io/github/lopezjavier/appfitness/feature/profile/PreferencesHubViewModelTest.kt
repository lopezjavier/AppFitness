package io.github.lopezjavier.appfitness.feature.profile

import io.github.lopezjavier.appfitness.app.FakeClock
import io.github.lopezjavier.appfitness.app.FakeNotificationSettingsRepository
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.feature.diary.DefaultMealSlots
import io.github.lopezjavier.appfitness.feature.diary.FakeMealSlotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The values the Preferencias hub shows under its rows (C-153): read, kept on a failed read, read again on resume. */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesHubViewModelTest {
    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", null, true)))
    private val settings = FakeNotificationSettingsRepository(NotificationKind.entries.first() to false)
    private val slots = FakeMealSlotRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        profile.biometrics = Result.success(
            SavedBiometrics(Sex.Male, LocalDate(1995, 3, 1), 180, ActivityLevel.Moderate),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PreferencesHubViewModel(
        profileRepository = profile,
        notificationSettings = settings,
        mealSlots = slots,
        clock = FakeClock(LocalDate(2026, 10, 14)),
    )

    @Test
    fun summary_readsEveryRowValue() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val summary = viewModel.summary.value
        assertEquals(PersonalSummary(31, 180, ActivityLevel.Moderate), summary.personal)
        assertEquals(DefaultMealSlots.size, summary.mealCount)
        assertEquals(PreferencesHubSummary.REMINDER_TOTAL - 1, summary.remindersOn)
    }

    @Test
    fun aFailedRead_leavesOnlyThatValueEmpty() = runTest {
        settings.failReads = true
        val viewModel = viewModel()
        advanceUntilIdle()

        val summary = viewModel.summary.value
        assertNull(summary.remindersOn)
        assertEquals(DefaultMealSlots.size, summary.mealCount)
    }

    @Test
    fun aFailedRefresh_keepsTheLastKnownValue() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        settings.failReads = true

        viewModel.onEvent(PreferencesHubEvent.Refresh)
        advanceUntilIdle()

        assertEquals(PreferencesHubSummary.REMINDER_TOTAL - 1, viewModel.summary.value.remindersOn)
    }

    @Test
    fun refresh_readsTheSavedValuesAgain() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        settings.values[NotificationKind.entries.first()] = true

        viewModel.onEvent(PreferencesHubEvent.Refresh)
        advanceUntilIdle()

        assertEquals(PreferencesHubSummary.REMINDER_TOTAL, viewModel.summary.value.remindersOn)
    }
}
