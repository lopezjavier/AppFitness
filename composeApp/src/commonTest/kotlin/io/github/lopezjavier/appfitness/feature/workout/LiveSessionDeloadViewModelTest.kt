package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeDeloadRepository
import io.github.lopezjavier.appfitness.app.FakeProgressionRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.model.DeloadExerciseSuggestion
import io.github.lopezjavier.appfitness.domain.model.ProgressionAction
import io.github.lopezjavier.appfitness.domain.model.ProgressionReason
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** T-165: the deload line of an exercise in a live session of an accepted deload week. */
class LiveSessionDeloadViewModelTest {
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1))),
            SessionExercise("e2", 1, null, listOf(pendingSet(1))),
        ),
    )
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench"), exerciseRow("e2", "squat")))
    private val deloads = FakeDeloadRepository()
    private val progression = FakeProgressionRepository()
    private val preferences = FakeTrainingPreferencesRepository()
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
            deloadRepository = deloads,
        )
        advanceUntilIdle()
        return vm
    }

    private fun LiveSessionViewModel.active() = assertIs<LiveSessionUiState.Active>(uiState.value)

    private fun raise(exerciseId: String) = ProgressionSuggestion(
        exerciseId,
        ProgressionAction.RaiseWeight,
        80.0,
        8,
        ProgressionReason.AllSetsAtMax,
        emptyList(),
    )

    @Test
    fun deloadSuggestionsAreRequestedForTheLoadedSession() = runTest {
        loaded()

        assertEquals(listOf("s1"), deloads.requestedSessions)
    }

    @Test
    fun deloadLine_showsOnlyOnItsExercise() = runTest {
        deloads.suggestions = listOf(DeloadExerciseSuggestion("e1", 5, 3))

        val vm = loaded()

        assertEquals(DeloadExerciseSuggestion("e1", 5, 3), vm.active().exercises[0].deload)
        assertNull(vm.active().exercises[1].deload)
        assertEquals(DeloadExerciseSuggestion("e1", 5, 3), vm.deloadFor(0))
        assertNull(vm.deloadFor(1))
    }

    @Test
    fun deloadLine_hidesTheProgressionRowOfThatExerciseOnly() = runTest {
        progression.suggestions = listOf(raise("e1"), raise("e2"))
        deloads.suggestions = listOf(DeloadExerciseSuggestion("e1", 5, 3))

        val vm = loaded()

        assertNull(vm.active().exercises[0].suggestion)
        assertNotNull(vm.active().exercises[1].suggestion)
    }

    @Test
    fun deloadLine_clearsTheProgressionHelpOfThatExercise() = runTest {
        progression.suggestions = listOf(raise("e1"))
        deloads.suggestions = listOf(DeloadExerciseSuggestion("e1", 5, 3))
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.OpenProgressionHelp(0))
        runCurrent()

        assertNull(vm.active().progressionHelpPosition)
    }

    @Test
    fun outsideADeloadWeek_theProgressionRowStaysAndThereIsNoDeloadLine() = runTest {
        progression.suggestions = listOf(raise("e1"))

        val vm = loaded()

        assertNotNull(vm.active().exercises[0].suggestion)
        assertNull(vm.active().exercises[0].deload)
    }

    @Test
    fun aFailedDeloadRead_omitsTheLineKeepsTheProgressionRowAndTheSessionUsable() = runTest {
        progression.suggestions = listOf(raise("e1"))
        deloads.failing = true

        val vm = loaded()

        assertNull(vm.active().exercises[0].deload)
        assertNotNull(vm.active().exercises[0].suggestion)
    }

    @Test
    fun deloadLine_isDerivedFromTheServerAgainWhenTheViewModelIsRecreated() = runTest {
        deloads.suggestions = listOf(DeloadExerciseSuggestion("e2", 4, 2))
        val savedState = SavedStateHandle()
        loaded(savedState)

        val recreated = loaded(savedState)

        assertEquals(2, recreated.active().exercises[1].deload?.deloadSets)
        assertEquals(2, deloads.requestedSessions.size)
    }
}
