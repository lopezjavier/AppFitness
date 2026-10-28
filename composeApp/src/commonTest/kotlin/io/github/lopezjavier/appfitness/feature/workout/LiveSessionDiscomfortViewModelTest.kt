package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeDiscomfortRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/** US-67: logging, resolving and showing the discomfort of an exercise in the live session. */
class LiveSessionDiscomfortViewModelTest {
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1))),
            SessionExercise("e2", 1, null, listOf(pendingSet(1))),
        ),
    )
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench"), exerciseRow("e2", "squat")))
    private val connectivity = FakeConnectivityObserver()
    private val clock = MutableClock()
    private val bodyMeasurements = FakeWeightRepository()
    private val discomforts = FakeDiscomfortRepository(now = { clock.current })

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository.history = mapOf("e1" to emptyList(), "e2" to emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(savedState: SavedStateHandle = SavedStateHandle()) = LiveSessionViewModel(
        repository,
        exercises,
        FakeExerciseLibrary(emptyMap()),
        connectivity,
        clock,
        FakeHaptics(),
        FakeRoutineRepository(),
        FakeAlertSound(),
        FakeTrainingPreferencesRepository(),
        bodyMeasurements,
        SaveBodyMeasurement(bodyMeasurements, clock, TimeZone.UTC),
        TimeZone.UTC,
        savedState,
        discomfortRepository = discomforts,
    )

    private fun TestScope.loaded(savedState: SavedStateHandle = SavedStateHandle()): LiveSessionViewModel {
        val vm = newViewModel(savedState)
        advanceUntilIdle()
        return vm
    }

    private fun LiveSessionViewModel.discomfort(position: Int) =
        assertIs<LiveSessionUiState.Active>(uiState.value).exercises[position].discomfort

    private fun TestScope.send(vm: LiveSessionViewModel, event: LiveSessionEvent) {
        vm.onEvent(event)
        runCurrent()
    }

    private fun row(exerciseId: String, ageDays: Int, level: DiscomfortLevel = DiscomfortLevel.Mild) =
        Discomfort("old-$exerciseId-$ageDays", exerciseId, clock.current - ageDays.days, level, null)

    @Test
    fun noDiscomfortByDefault() = runTest {
        val vm = loaded()

        assertNull(vm.discomfort(0))
    }

    @Test
    fun logging_showsTheDiscomfortOnThatExerciseOnlyAndConfirms() = runTest {
        val vm = loaded()

        vm.effects.test {
            send(vm, LiveSessionEvent.LogDiscomfort(0, DiscomfortLevel.Mild, "rodilla"))

            assertEquals(LiveSessionEffect.DiscomfortLogged, awaitItem())
        }
        assertEquals(LiveDiscomfort(DiscomfortLevel.Mild), vm.discomfort(0))
        assertNull(vm.discomfort(1))
    }

    @Test
    fun logging_aHighLevelIsStoredAndShown() = runTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.LogDiscomfort(0, DiscomfortLevel.SharpOrInjury, null))

        assertEquals(LiveDiscomfort(DiscomfortLevel.SharpOrInjury), vm.discomfort(0))
        assertEquals(DiscomfortLevel.SharpOrInjury, discomforts.rows.single().level)
        assertTrue(discomforts.rows.single().severe)
    }

    @Test
    fun logging_aStrongPainNoteDoesNotChangeTheChosenLevel() = runTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.LogDiscomfort(0, DiscomfortLevel.Tension, "dolor fuerte"))

        assertEquals(LiveDiscomfort(DiscomfortLevel.Tension), vm.discomfort(0))
        assertFalse(discomforts.rows.single().severe)
    }

    @Test
    fun resolving_clearsTheDiscomfortAndConfirms() = runTest {
        discomforts.rows = listOf(row("e1", ageDays = 1))
        val vm = loaded()

        vm.effects.test {
            send(vm, LiveSessionEvent.ResolveDiscomfort(0))

            assertEquals(LiveSessionEffect.DiscomfortResolved, awaitItem())
        }
        assertNull(vm.discomfort(0))
        assertTrue(discomforts.rows.all { it.resolvedAt != null })
    }

    @Test
    fun anOpenDiscomfortFromAnEarlierSessionIsShownAtStart() = runTest {
        discomforts.rows = listOf(row("e1", ageDays = 3, level = DiscomfortLevel.SharpOrInjury))

        val vm = loaded()

        assertEquals(LiveDiscomfort(DiscomfortLevel.SharpOrInjury), vm.discomfort(0))
    }

    @Test
    fun sixDayOldIsShownAndEightDayOldIsNot() = runTest {
        discomforts.rows = listOf(row("e1", ageDays = 6), row("e2", ageDays = 8))

        val vm = loaded()

        assertEquals(LiveDiscomfort(DiscomfortLevel.Mild), vm.discomfort(0))
        assertNull(vm.discomfort(1))
    }

    @Test
    fun theHighestLevelAmongOpenRowsIsShown() = runTest {
        discomforts.rows = listOf(
            row("e1", ageDays = 5, level = DiscomfortLevel.SwellingOrNumbness),
            row("e1", ageDays = 1, level = DiscomfortLevel.Tension),
        )

        val vm = loaded()

        assertEquals(LiveDiscomfort(DiscomfortLevel.SwellingOrNumbness), vm.discomfort(0))
    }

    @Test
    fun savingIsFlaggedWhileLoggingAndTheLogCountGrowsOnSuccess() = runTest {
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.LogDiscomfort(0, DiscomfortLevel.Mild, "x"))
        val during = assertIs<LiveSessionUiState.Active>(vm.uiState.value)
        runCurrent()
        val after = assertIs<LiveSessionUiState.Active>(vm.uiState.value)

        assertTrue(during.discomfortSaving)
        assertFalse(after.discomfortSaving)
        assertEquals(during.discomfortLogs + 1, after.discomfortLogs)
    }

    @Test
    fun aFailedLogEndsSavingWithoutCountingAStoredLog() = runTest {
        val vm = loaded()
        discomforts.failing = true

        send(vm, LiveSessionEvent.LogDiscomfort(0, DiscomfortLevel.Mild, "x"))

        val state = assertIs<LiveSessionUiState.Active>(vm.uiState.value)
        assertFalse(state.discomfortSaving)
        assertEquals(0, state.discomfortLogs)
    }

    @Test
    fun resolvingIsFlaggedUntilTheCallEndsAndAFailureClearsIt() = runTest {
        discomforts.rows = listOf(row("e1", ageDays = 1))
        val vm = loaded()
        discomforts.failing = true

        vm.onEvent(LiveSessionEvent.ResolveDiscomfort(0))
        assertEquals(LiveDiscomfort(DiscomfortLevel.Mild, resolving = true), vm.discomfort(0))
        runCurrent()

        assertEquals(LiveDiscomfort(DiscomfortLevel.Mild), vm.discomfort(0))
    }

    @Test
    fun aDiscomfortReadThatFailedIsRetriedWhenBackOnline() = runTest {
        discomforts.rows = listOf(row("e1", ageDays = 1))
        discomforts.failing = true
        val vm = loaded()
        assertNull(vm.discomfort(0))
        discomforts.failing = false

        connectivity.state.value = false
        runCurrent()
        connectivity.state.value = true
        runCurrent()

        assertEquals(LiveDiscomfort(DiscomfortLevel.Mild), vm.discomfort(0))
    }

    @Test
    fun offlineLogAndResolveAreRejectedWithoutCallingTheRepository() = runTest {
        discomforts.rows = listOf(row("e1", ageDays = 1))
        val vm = loaded()
        connectivity.state.value = false
        runCurrent()

        send(vm, LiveSessionEvent.LogDiscomfort(1, DiscomfortLevel.Mild, "x"))
        send(vm, LiveSessionEvent.ResolveDiscomfort(0))

        assertEquals(0, discomforts.logCalls)
        assertEquals(0, discomforts.resolveCalls)
        assertNull(vm.discomfort(1))
        assertEquals(LiveDiscomfort(DiscomfortLevel.Mild), vm.discomfort(0))
    }

    @Test
    fun aFailedLogRejectsAndShowsNothing() = runTest {
        val vm = loaded()
        discomforts.failing = true

        vm.effects.test {
            send(vm, LiveSessionEvent.LogDiscomfort(0, DiscomfortLevel.Mild, "x"))

            assertEquals(LiveSessionEffect.DiscomfortLogFailed, awaitItem())
        }
        assertNull(vm.discomfort(0))
    }

    @Test
    fun aFailedResolveKeepsTheDiscomfort() = runTest {
        discomforts.rows = listOf(row("e1", ageDays = 1))
        val vm = loaded()
        discomforts.failing = true

        vm.effects.test {
            send(vm, LiveSessionEvent.ResolveDiscomfort(0))

            assertEquals(LiveSessionEffect.DiscomfortResolveFailed, awaitItem())
        }
        assertEquals(LiveDiscomfort(DiscomfortLevel.Mild), vm.discomfort(0))
    }

    @Test
    fun aNoteOverTwoHundredCharactersIsRejectedAndNotStored() = runTest {
        val vm = loaded()

        vm.effects.test {
            send(vm, LiveSessionEvent.LogDiscomfort(0, DiscomfortLevel.Mild, "a".repeat(201)))

            assertEquals(LiveSessionEffect.DiscomfortLogFailed, awaitItem())
        }
        assertTrue(discomforts.rows.isEmpty())
    }

    @Test
    fun aFailedDiscomfortLoadDoesNotBreakTheSession() = runTest {
        discomforts.rows = listOf(row("e1", ageDays = 1))
        discomforts.failing = true

        val vm = loaded()

        assertIs<LiveSessionUiState.Active>(vm.uiState.value)
        assertNull(vm.discomfort(0))
    }

    @Test
    fun aRecreatedViewModelReloadsTheOpenDiscomfort() = runTest {
        val savedState = SavedStateHandle()
        val first = loaded(savedState)
        send(first, LiveSessionEvent.LogDiscomfort(0, DiscomfortLevel.SharpOrInjury, "dolor fuerte"))

        val second = loaded(savedState)

        assertEquals(LiveDiscomfort(DiscomfortLevel.SharpOrInjury), second.discomfort(0))
        assertFalse(second.discomfort(1) != null)
    }
}
