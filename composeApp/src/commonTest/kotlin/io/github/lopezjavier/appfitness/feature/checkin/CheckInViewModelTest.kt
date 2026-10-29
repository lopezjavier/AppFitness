package io.github.lopezjavier.appfitness.feature.checkin

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.app.ReminderEnv
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.CHECK_IN_NOTES_MAX
import io.github.lopezjavier.appfitness.domain.model.CheckIn
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NotificationKind
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.domain.model.WeekRecord
import io.github.lopezjavier.appfitness.domain.usecase.LoadCheckInSummary
import io.github.lopezjavier.appfitness.feature.analytics.stats.FakeTrainingStatsRepository
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Wednesday 28 Oct 2026: the Sunday check-in day is 25 Oct, of the week starting Monday 19 Oct.
private val WEDNESDAY = LocalDate(2026, 10, 28)
private val CHECK_IN_DAY = LocalDate(2026, 10, 25)
private val WEEK = LocalDate(2026, 10, 19)

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun at(day: LocalDate) = day.atStartOfDayIn(TimeZone.UTC)

    /** Weight and circumferences are up to date and the onboarding day is known: only the check-in is pending. */
    private fun env(today: LocalDate = WEDNESDAY) = ReminderEnv(today).apply {
        weight.rows += BodyMeasurement("w", at(today), 80.0, MeasurementSource.Manual)
        weight.onboarding = BodyMeasurement("o", at(LocalDate(2026, 10, 1)), 80.0, MeasurementSource.Manual)
        circumferences.rows += BodyCircumference("c", CircumferenceSite.Waist, 90.0, at(today), at(today))
    }

    private class Fixture(
        val env: ReminderEnv,
        val stats: FakeTrainingStatsRepository,
        val coordinator: ReminderCoordinator,
        val connectivity: FakeConnectivityObserver,
        val viewModel: CheckInViewModel,
    ) {
        val ready get() = assertIs<CheckInUiState.Ready>(viewModel.uiState.value)
    }

    private fun TestScope.fixture(
        env: ReminderEnv = env(),
        handle: SavedStateHandle = SavedStateHandle(),
        exercises: FakeExerciseRepository? = null,
    ): Fixture {
        val stats = FakeTrainingStatsRepository().apply {
            summaryFor = { w ->
                if (w.from == WEEK) TrainingSummary(4, 40, 12_400.0, 0) else TrainingSummary(3, 30, 11_600.0, 0)
            }
        }
        val connectivity = FakeConnectivityObserver(initial = true)
        val coordinator = ReminderCoordinator(env.load, backgroundScope)
        val viewModel = CheckInViewModel(
            coordinator,
            env.checkIns,
            LoadCheckInSummary(stats, env.weight, TimeZone.UTC),
            connectivity,
            handle,
            exercises,
            FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press de banca"))),
        )
        return Fixture(env, stats, coordinator, connectivity, viewModel)
    }

    @Test
    fun opening_loadsTheWindowAndTheSummaryWithAnEmptyForm() = runTest {
        val f = fixture()
        advanceUntilIdle()

        val ready = f.ready
        assertEquals(CHECK_IN_DAY, ready.window.day)
        assertEquals(WEEK, ready.window.weekStart)
        assertEquals(null, ready.energy)
        assertEquals("", ready.notes)
        assertFalse(ready.isEditing)
        val summary = assertIs<CheckInSummaryState.Loaded>(ready.summary).summary
        assertEquals(12_400.0, summary.training.current.volume)
        assertEquals(11_600.0, summary.training.previous.volume)
    }

    @Test
    fun anAnsweredCheckIn_opensPrefilled() = runTest {
        val env = env().apply { checkIns.rows[WEEK] = CheckIn(WEEK, 4, "tired legs") }
        val f = fixture(env)
        advanceUntilIdle()

        assertEquals(4, f.ready.energy)
        assertEquals("tired legs", f.ready.notes)
        assertTrue(f.ready.isEditing)
        assertFalse(f.ready.canSave, "unchanged")
    }

    @Test
    fun guardar_needsAnEnergy_butNotNotes() = runTest {
        val f = fixture()
        advanceUntilIdle()
        assertFalse(f.ready.canSave)

        f.viewModel.onEvent(CheckInEvent.SetNotes("only notes"))
        assertFalse(f.ready.canSave, "notes alone are not enough")
        f.viewModel.onEvent(CheckInEvent.SelectEnergy(3))
        assertTrue(f.ready.canSave)
        f.viewModel.onEvent(CheckInEvent.SetNotes(""))
        assertTrue(f.ready.canSave, "notes are optional")
    }

    @Test
    fun anEnergyOutsideOneToFive_isIgnored() = runTest {
        val f = fixture()
        advanceUntilIdle()

        f.viewModel.onEvent(CheckInEvent.SelectEnergy(0))
        f.viewModel.onEvent(CheckInEvent.SelectEnergy(6))

        assertEquals(null, f.ready.energy)
    }

    @Test
    fun saving_storesTheAnswerOfTheWeek_refreshesTheCenterAndEmitsSaved() = runTest {
        val f = fixture()
        advanceUntilIdle()
        assertEquals(1, f.coordinator.reminders.value.size)

        f.viewModel.onEvent(CheckInEvent.SelectEnergy(4))
        f.viewModel.onEvent(CheckInEvent.SetNotes("good week"))
        f.viewModel.effects.test {
            f.viewModel.onEvent(CheckInEvent.Save)
            advanceUntilIdle()
            assertEquals(CheckInEffect.Saved, awaitItem())
        }

        assertEquals(listOf(CheckIn(WEEK, 4, "good week")), f.env.checkIns.saves)
        assertTrue(f.coordinator.reminders.value.isEmpty(), "the reminder is gone")
        assertEquals(4, f.coordinator.checkIn.value?.answer?.energy, "still reachable, answered")
        assertFalse(f.ready.isSaving)
    }

    @Test
    fun offline_disablesGuardar() = runTest {
        val f = fixture()
        advanceUntilIdle()
        f.viewModel.onEvent(CheckInEvent.SelectEnergy(2))
        assertTrue(f.ready.canSave)

        f.connectivity.state.value = false
        advanceUntilIdle()

        assertTrue(f.ready.isOffline)
        assertFalse(f.ready.canSave)
        f.viewModel.onEvent(CheckInEvent.Save)
        advanceUntilIdle()
        assertTrue(f.env.checkIns.saves.isEmpty())
    }

    @Test
    fun aFailedSave_keepsTheDraftAndEmitsAnEffect() = runTest {
        val f = fixture()
        advanceUntilIdle()
        f.env.checkIns.failWrites = true
        f.viewModel.onEvent(CheckInEvent.SelectEnergy(5))
        f.viewModel.onEvent(CheckInEvent.SetNotes("kept"))

        f.viewModel.effects.test {
            f.viewModel.onEvent(CheckInEvent.Save)
            advanceUntilIdle()
            assertEquals(CheckInEffect.SaveFailed(offline = true), awaitItem())
        }

        assertEquals(5, f.ready.energy)
        assertEquals("kept", f.ready.notes)
        assertFalse(f.ready.isSaving)
        assertTrue(f.ready.canSave, "can try again")
        assertEquals(1, f.coordinator.reminders.value.size, "the reminder stays")
    }

    @Test
    fun aValidationFailure_isNotReportedAsOffline() = runTest {
        val f = fixture()
        advanceUntilIdle()
        f.env.checkIns.failWrites = true
        f.env.checkIns.saveError = AppError.Validation
        f.viewModel.onEvent(CheckInEvent.SelectEnergy(5))

        f.viewModel.effects.test {
            f.viewModel.onEvent(CheckInEvent.Save)
            advanceUntilIdle()
            assertEquals(CheckInEffect.SaveFailed(offline = false), awaitItem())
        }
    }

    @Test
    fun notesOverTheLimit_blockGuardar() = runTest {
        val f = fixture()
        advanceUntilIdle()
        f.viewModel.onEvent(CheckInEvent.SelectEnergy(3))

        f.viewModel.onEvent(CheckInEvent.SetNotes("x".repeat(CHECK_IN_NOTES_MAX + 1)))
        assertTrue(f.ready.notesTooLong)
        assertFalse(f.ready.canSave)

        f.viewModel.onEvent(CheckInEvent.SetNotes("x".repeat(CHECK_IN_NOTES_MAX)))
        assertFalse(f.ready.notesTooLong)
        assertTrue(f.ready.canSave)
    }

    @Test
    fun aSummaryFailure_doesNotBlockTheForm_andRetryRecovers() = runTest {
        val env = env()
        val f = fixture(env)
        f.stats.recordsFailure = AppError.Network
        advanceUntilIdle()
        assertEquals(CheckInSummaryState.Failed, f.ready.summary)

        f.viewModel.onEvent(CheckInEvent.SelectEnergy(3))
        assertTrue(f.ready.canSave, "the form still works")

        f.stats.recordsFailure = null
        f.viewModel.onEvent(CheckInEvent.RetrySummary)
        advanceUntilIdle()

        assertIs<CheckInSummaryState.Loaded>(f.ready.summary)
        assertEquals(3, f.ready.energy, "the draft survives the retry")
    }

    private fun withRecords(f: Fixture) {
        f.stats.recordsFor = {
            listOf(
                WeekRecord("a", PersonalRecordType.HeaviestLift, 100.0, CHECK_IN_DAY),
                WeekRecord("c1", PersonalRecordType.BestSet, 30.0, CHECK_IN_DAY),
            )
        }
    }

    @Test
    fun recordNames_resolveCatalogueExercisesThroughTheLibrary_andCustomOnesFromTheirRow() = runTest {
        val rows = FakeExerciseRepository(
            listOf(exerciseRow("a", "bench"), exerciseRow("c1", null, name = "Hand grip", custom = true)),
        )
        val f = fixture(exercises = rows)
        withRecords(f)
        advanceUntilIdle()

        assertEquals(mapOf("a" to "Press de banca", "c1" to "Hand grip"), f.ready.exerciseNames)
    }

    @Test
    fun recordNames_whenTheExercisesCannotBeRead_stayEmptyAndTheSummaryLoads() = runTest {
        val f = fixture(exercises = FakeExerciseRepository(failure = AppError.Network))
        withRecords(f)
        advanceUntilIdle()

        assertEquals(emptyMap(), f.ready.exerciseNames)
        assertIs<CheckInSummaryState.Loaded>(f.ready.summary)
    }

    @Test
    fun noOpenCheckIn_isALoadFailure_andRetryRecovers() = runTest {
        val env = env().apply { settings.values[NotificationKind.WeeklyCheckIn] = false }
        val f = fixture(env)
        advanceUntilIdle()
        assertEquals(CheckInUiState.LoadFailed, f.viewModel.uiState.value)

        env.settings.values.clear()
        f.viewModel.onEvent(CheckInEvent.Retry)
        advanceUntilIdle()

        assertIs<CheckInUiState.Ready>(f.viewModel.uiState.value)
    }

    @Test
    fun reopeningOnTheMonday_showsTheSavedAnswerAndTheClosedWeekFigures() = runTest {
        val monday = LocalDate(2026, 10, 26)
        val env = env(monday).apply { checkIns.rows[WEEK] = CheckIn(WEEK, 2, "slept badly") }
        val f = fixture(env)
        advanceUntilIdle()

        assertEquals(CHECK_IN_DAY, f.ready.window.day)
        assertEquals(2, f.ready.energy)
        assertEquals(
            listOf(FakeTrainingStatsRepository.Window(WEEK, CHECK_IN_DAY)),
            f.stats.recordCalls,
            "the figures are the closed Monday-Sunday week, not the week in progress",
        )
        assertEquals(12_400.0, assertIs<CheckInSummaryState.Loaded>(f.ready.summary).summary.training.current.volume)
    }

    @Test
    fun editingOnTheMonday_updatesTheSameWeek() = runTest {
        val monday = LocalDate(2026, 10, 26)
        val env = env(monday).apply { checkIns.rows[WEEK] = CheckIn(WEEK, 2, "slept badly") }
        val f = fixture(env)
        advanceUntilIdle()

        f.viewModel.onEvent(CheckInEvent.SelectEnergy(4))
        assertTrue(f.ready.canSave)
        f.viewModel.onEvent(CheckInEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf(CheckIn(WEEK, 4, "slept badly")), env.checkIns.saves)
        assertEquals(1, env.checkIns.rows.size, "one row per week")
        assertEquals(4, f.ready.window.answer?.energy)
    }

    @Test
    fun anUnsavedDraft_survivesARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val env = env()
        val first = fixture(env, handle)
        advanceUntilIdle()
        first.viewModel.onEvent(CheckInEvent.SelectEnergy(5))
        first.viewModel.onEvent(CheckInEvent.SetNotes("draft"))

        val second = fixture(env, handle)
        advanceUntilIdle()

        assertEquals(5, second.ready.energy)
        assertEquals("draft", second.ready.notes)
        assertTrue(second.ready.canSave)
        assertIs<CheckInSummaryState.Loaded>(second.ready.summary)
    }

    @Test
    fun aDraftEqualToTheSavedAnswer_isNotKept() = runTest {
        val handle = SavedStateHandle()
        val env = env().apply { checkIns.rows[WEEK] = CheckIn(WEEK, 3, "same") }
        val first = fixture(env, handle)
        advanceUntilIdle()
        first.viewModel.onEvent(CheckInEvent.SelectEnergy(5))
        first.viewModel.onEvent(CheckInEvent.SelectEnergy(3))

        val second = fixture(env, handle)
        advanceUntilIdle()

        assertEquals(3, second.ready.energy)
        assertFalse(second.ready.isDirty)
    }
}
