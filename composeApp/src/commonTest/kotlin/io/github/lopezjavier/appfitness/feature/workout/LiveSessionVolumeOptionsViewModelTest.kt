package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** US-32: per-hand volume (exercise flag) and the body-weight update from the exercise menu. */
class LiveSessionVolumeOptionsViewModelTest {
    private val repository = FakeWorkoutSessionRepository()
    private val clock = MutableClock()
    private val weights = FakeWeightRepository()
    private val exercises = FakeExerciseRepository(
        listOf(
            exerciseRow("curl", null, equipment = Equipment.Dumbbell, name = "Curl", custom = true),
            exerciseRow("pullup", null, equipment = Equipment.Bodyweight, name = "Dominadas", custom = true)
                .copy(metricType = ExerciseMetricType.Bodyweight),
        ),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository.session = sessionOf(
            SessionExercise(
                "curl",
                0,
                null,
                listOf(WorkoutSet(1, SetType.Working, SetEntry.WeightReps(20.0, 10), SESSION_START)),
            ),
            SessionExercise(
                "pullup",
                1,
                null,
                listOf(WorkoutSet(1, SetType.Working, SetEntry.Bodyweight(0.0, 10), SESSION_START)),
            ),
        )
        repository.history = mapOf("curl" to emptyList(), "pullup" to emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(savedState: SavedStateHandle = SavedStateHandle()) = LiveSessionViewModel(
        repository,
        exercises,
        FakeExerciseLibrary(),
        FakeConnectivityObserver(),
        clock,
        FakeHaptics(),
        FakeRoutineRepository(),
        FakeAlertSound(),
        FakeTrainingPreferencesRepository(),
        weights,
        SaveBodyMeasurement(weights, clock, TimeZone.UTC),
        TimeZone.UTC,
        savedState,
    )

    private fun TestScope.loaded(savedState: SavedStateHandle = SavedStateHandle()): LiveSessionViewModel {
        val vm = newViewModel(savedState)
        advanceUntilIdle()
        return vm
    }

    private fun LiveSessionViewModel.active() = assertIs<LiveSessionUiState.Active>(uiState.value)

    private fun TestScope.send(vm: LiveSessionViewModel, vararg events: LiveSessionEvent) {
        events.forEach(vm::onEvent)
        runCurrent()
    }

    private fun TestScope.summaryVolumes(vm: LiveSessionViewModel): List<Double> {
        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()
        return assertIs<LiveSessionUiState.Finished>(vm.uiState.value).summary.summary.exercises.map { it.volume }
    }

    private fun weighIn(id: String, at: String, kg: Double, source: MeasurementSource) =
        BodyMeasurement(id, Instant.parse(at), kg, source)

    @Test
    fun stateStartsWithThePerHandExercisesAndTheLatestBodyWeight() = runTest {
        exercises.rows = exercises.rows.map { if (it.id == "curl") it.copy(perHand = true) else it }
        weights.rows += weighIn("w1", "2026-10-18T08:00:00Z", 74.0, MeasurementSource.Manual)

        val state = loaded().active()

        assertEquals(setOf("curl"), state.perHandIds)
        assertEquals(74.0, state.bodyWeightKg)
        assertFalse(state.todayHasHealthConnectWeight)
    }

    @Test
    fun anUnreadableCatalogOrWeightLeavesTheDefaultsWithoutBlocking() = runTest {
        exercises.failure = AppError.Network
        weights.failReads = true

        val state = loaded().active()

        assertTrue(state.perHandIds.isEmpty())
        assertNull(state.bodyWeightKg)
    }

    @Test
    fun aPerHandExerciseDoublesItsVolumeInTheSummaryOnly() = runTest {
        exercises.rows = exercises.rows.map { if (it.id == "curl") it.copy(perHand = true) else it }
        val vm = loaded()

        assertEquals(listOf(400.0, 0.0), summaryVolumes(vm))
    }

    @Test
    fun withoutTheFlagTheLoggedWeightCountsOnce() = runTest {
        val vm = loaded()

        assertEquals(listOf(200.0, 0.0), summaryVolumes(vm))
    }

    @Test
    fun savingTheBodyWeightStoresTodaysManualWeightAndFeedsTheSummary() = runTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.SaveBodyWeight("74,0"))

        val save = weights.saves.single()
        assertEquals(74.0, save.weightKg)
        assertEquals(clock.now(), save.measuredAt)
        assertEquals(74.0, vm.active().bodyWeightKg)
        assertNull(vm.active().bodyWeightDraft)
        assertEquals(listOf(200.0, 740.0), summaryVolumes(vm))
    }

    @Test
    fun anInvalidBodyWeightIsRejectedWithoutSaving() = runTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.BodyWeightDraftChanged("abc"), LiveSessionEvent.SaveBodyWeight("abc"))

        assertTrue(weights.saves.isEmpty())
        assertTrue(vm.active().bodyWeightInvalid)
        assertEquals("abc", vm.active().bodyWeightDraft)
    }

    @Test
    fun editingTheBodyWeightDraftClearsTheError() = runTest {
        val vm = loaded()
        send(vm, LiveSessionEvent.SaveBodyWeight("abc"))

        send(vm, LiveSessionEvent.BodyWeightDraftChanged("74"))

        assertFalse(vm.active().bodyWeightInvalid)
    }

    @Test
    fun aHealthConnectWeightTodayBlocksTheUpdate() = runTest {
        weights.rows += weighIn("hc1", "2026-10-19T07:00:00Z", 75.0, MeasurementSource.HealthConnect)
        val vm = loaded()
        assertTrue(vm.active().todayHasHealthConnectWeight)

        send(vm, LiveSessionEvent.SaveBodyWeight("74,0"))

        assertTrue(weights.saves.isEmpty())
    }

    @Test
    fun aHealthConnectWeightOfAnotherDayDoesNotBlockTheUpdate() = runTest {
        weights.rows += weighIn("hc1", "2026-10-18T07:00:00Z", 75.0, MeasurementSource.HealthConnect)
        val vm = loaded()
        assertFalse(vm.active().todayHasHealthConnectWeight)

        send(vm, LiveSessionEvent.SaveBodyWeight("74,0"))

        assertEquals(1, weights.saves.size)
    }

    @Test
    fun aFailedBodyWeightSaveKeepsTheDraftAndTellsTheUser() = runTest {
        val vm = loaded()
        weights.saveFailure = IllegalStateException("offline")
        send(vm, LiveSessionEvent.BodyWeightDraftChanged("74,0"))

        vm.effects.test {
            vm.onEvent(LiveSessionEvent.SaveBodyWeight("74,0"))
            advanceUntilIdle()

            assertEquals(LiveSessionEffect.OptionSaveFailed, awaitItem())
        }
        assertEquals("74,0", vm.active().bodyWeightDraft)
        assertNull(vm.active().bodyWeightKg)
        assertFalse(vm.active().bodyWeightInvalid)
    }

    @Test
    fun dismissingTheSheetDropsTheBodyWeightDraft() = runTest {
        val vm = loaded()
        send(vm, LiveSessionEvent.BodyWeightDraftChanged("74"))

        send(vm, LiveSessionEvent.DismissVolumeDrafts)

        assertNull(vm.active().bodyWeightDraft)
    }

    @Test
    fun theDraftSurvivesRecreationFromTheSameSavedState() = runTest {
        val handle = SavedStateHandle()
        val first = loaded(handle)
        send(first, LiveSessionEvent.BodyWeightDraftChanged("74,5"))

        val second = loaded(handle)

        assertEquals("74,5", second.active().bodyWeightDraft)
    }

    @Test
    fun aSavedWeightClearsItsDraftFromTheSavedState() = runTest {
        val handle = SavedStateHandle()
        val first = loaded(handle)
        send(first, LiveSessionEvent.BodyWeightDraftChanged("74"))
        send(first, LiveSessionEvent.SaveBodyWeight("74"))

        val second = loaded(handle)

        assertNull(second.active().bodyWeightDraft)
    }
}
