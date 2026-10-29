package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeProgressionRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.model.ProgressionAction
import io.github.lopezjavier.appfitness.domain.model.ProgressionReason
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
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
import kotlin.test.assertIs
import kotlin.test.assertNull

/** US-70: the progression suggestion shown per exercise in the live session. */
class LiveSessionProgressionViewModelTest {
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1))),
            SessionExercise("e2", 1, null, listOf(pendingSet(1))),
        ),
    )
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench"), exerciseRow("e2", "squat")))
    private val progression = FakeProgressionRepository()
    private val preferences = FakeTrainingPreferencesRepository().apply {
        result = Result.success(TrainingPreferences(plateKg = listOf(25.0, 2.5)))
    }
    private val bodyMeasurements = FakeWeightRepository()
    private val clock = MutableClock()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository.history = mapOf("e1" to emptyList(), "e2" to emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun suggestion(
        exerciseId: String,
        action: ProgressionAction,
        base: Double = 80.0,
        reason: ProgressionReason = ProgressionReason.BelowMax,
    ) = ProgressionSuggestion(exerciseId, action, base, 8, reason, emptyList())

    private fun TestScope.loaded(savedState: SavedStateHandle = SavedStateHandle()): LiveSessionViewModel {
        val vm = LiveSessionViewModel(
            repository,
            exercises,
            FakeExerciseLibrary(emptyMap()),
            FakeConnectivityObserver(),
            clock,
            FakeHaptics(),
            FakeRoutineRepository(),
            FakeAlertSound(),
            preferences,
            bodyMeasurements,
            SaveBodyMeasurement(bodyMeasurements, clock, TimeZone.UTC),
            TimeZone.UTC,
            savedState,
            progressionRepository = progression,
        )
        advanceUntilIdle()
        return vm
    }

    private fun LiveSessionViewModel.active() = assertIs<LiveSessionUiState.Active>(uiState.value)

    private fun TestScope.send(vm: LiveSessionViewModel, event: LiveSessionEvent) {
        vm.onEvent(event)
        runCurrent()
    }

    @Test
    fun suggestionsAreRequestedForTheLoadedSession() = runTest {
        loaded()

        assertEquals(listOf("s1"), progression.requestedSessions)
    }

    @Test
    fun addRep_showsTheBaseWeightOnlyOnItsExercise() = runTest {
        progression.suggestions = listOf(suggestion("e1", ProgressionAction.AddRep))

        val vm = loaded()

        assertEquals(80.0, vm.active().exercises[0].suggestion?.weightKg)
        assertEquals(8, vm.active().exercises[0].suggestion?.goalReps)
        assertNull(vm.active().exercises[1].suggestion)
    }

    @Test
    fun raise_appliesTwiceTheSmallestEnabledPlate() = runTest {
        progression.suggestions = listOf(suggestion("e1", ProgressionAction.RaiseWeight))

        assertEquals(85.0, loaded().active().exercises[0].suggestion?.weightKg)
    }

    @Test
    fun lower_dropsFivePercentRoundedToTheStep() = runTest {
        progression.suggestions = listOf(suggestion("e1", ProgressionAction.LowerWeight))

        assertEquals(75.0, loaded().active().exercises[0].suggestion?.weightKg)
    }

    @Test
    fun aFailedRead_omitsTheLineAndKeepsTheSessionUsable() = runTest {
        progression.failing = true

        val vm = loaded()

        assertNull(vm.active().exercises[0].suggestion)
        assertNull(vm.active().exercises[1].suggestion)
    }

    @Test
    fun ignoringTheSuggestion_leavesThePlannedValuesAndSetsUntouched() = runTest {
        progression.suggestions = listOf(suggestion("e1", ProgressionAction.RaiseWeight))
        val vm = loaded()
        val before = vm.active().exercises[0].sets

        send(vm, LiveSessionEvent.WeightChanged(0, 1, "70"))
        send(vm, LiveSessionEvent.RepsChanged(0, 1, "5"))

        val row = vm.active().exercises[0].sets.single()
        assertEquals("70", row.weight)
        assertEquals("5", row.reps)
        assertEquals(before.single().setIndex, row.setIndex)
        assertEquals(85.0, vm.active().exercises[0].suggestion?.weightKg)
    }

    @Test
    fun helpSheet_opensForAnExerciseWithSuggestionAndCloses() = runTest {
        progression.suggestions = listOf(suggestion("e1", ProgressionAction.AddRep))
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenProgressionHelp(0))
        assertEquals(0, vm.active().progressionHelpPosition)

        send(vm, LiveSessionEvent.DismissProgressionHelp)
        assertNull(vm.active().progressionHelpPosition)
    }

    @Test
    fun helpSheet_doesNotOpenWithoutASuggestion() = runTest {
        progression.suggestions = listOf(suggestion("e1", ProgressionAction.AddRep))
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenProgressionHelp(1))

        assertNull(vm.active().progressionHelpPosition)
    }

    @Test
    fun helpSheet_survivesRecreationFromTheSameSavedState() = runTest {
        progression.suggestions = listOf(suggestion("e1", ProgressionAction.AddRep))
        val savedState = SavedStateHandle()
        send(loaded(savedState), LiveSessionEvent.OpenProgressionHelp(0))

        val recreated = loaded(savedState)

        assertEquals(0, recreated.active().progressionHelpPosition)
    }
}
