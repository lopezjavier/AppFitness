package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-36: comparing a finished session with its routine and "Actualizar rutina". */
class LiveSessionRoutineUpdateViewModelTest {
    private val routines = FakeRoutineRepository(listOf(routine()))
    private val repository = FakeWorkoutSessionRepository(freshSession())
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench"), exerciseRow("e2", "squat")))
    private val clock = MutableClock()
    private val trainingPreferences = FakeTrainingPreferencesRepository()
    private val bodyMeasurements = FakeWeightRepository()
    private var created: LiveSessionViewModel? = null

    private fun newViewModel(savedState: SavedStateHandle = SavedStateHandle()) = LiveSessionViewModel(
        repository,
        exercises,
        FakeExerciseLibrary(emptyMap()),
        FakeConnectivityObserver(),
        clock,
        FakeHaptics(),
        routines,
        FakeAlertSound(),
        trainingPreferences,
        bodyMeasurements,
        SaveBodyMeasurement(bodyMeasurements, clock, TimeZone.UTC),
        TimeZone.UTC,
        savedState,
    ).also { created = it }

    private fun runSessionTest(body: suspend TestScope.() -> Unit) = runTest {
        body()
        created?.onEvent(LiveSessionEvent.RestSkip)
        runCurrent()
    }

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
        val vm = newViewModel(savedState)
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.log(vm: LiveSessionViewModel, position: Int, index: Int, weight: String, reps: String) {
        vm.onEvent(LiveSessionEvent.WeightChanged(position, index, weight))
        vm.onEvent(LiveSessionEvent.RepsChanged(position, index, reps))
        vm.onEvent(LiveSessionEvent.SetChecked(position, index, true))
        vm.onEvent(LiveSessionEvent.SetRpeChosen(position, index, 8.0))
        runCurrent()
    }

    /** Logs exactly what the routine planned. */
    private fun TestScope.logAsPlanned(vm: LiveSessionViewModel) {
        log(vm, 0, 1, "60", "10")
        log(vm, 0, 2, "60", "10")
        log(vm, 1, 1, "100", "5")
    }

    private fun TestScope.finish(vm: LiveSessionViewModel) {
        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()
    }

    private fun LiveSessionViewModel.finished() = assertIs<LiveSessionUiState.Finished>(uiState.value)

    @Test
    fun finishingWithTheRoutinesValues_asksNothing() = runSessionTest {
        val vm = loaded()
        logAsPlanned(vm)

        finish(vm)

        assertNull(vm.finished().updateRoutineSheet)
        assertTrue(routines.updatedFromSession.isEmpty())
    }

    @Test
    fun finishingWithAnAddedSetAndAChangedRest_opensTheSheetWithItsSummary() = runSessionTest {
        val vm = loaded()
        logAsPlanned(vm)
        vm.onEvent(LiveSessionEvent.AddSet(0))
        runCurrent()
        log(vm, 0, 3, "60", "10")
        vm.onEvent(LiveSessionEvent.SetExerciseRest(1, 45))
        runCurrent()

        finish(vm)

        val sheet = vm.finished().updateRoutineSheet
        assertEquals(RoutineChangeSummary(setsAdded = 1, restsChanged = 1), sheet?.summary)
        assertEquals("s1", sheet?.sessionId)
        assertTrue(routines.updatedFromSession.isEmpty())
    }

    @Test
    fun updatingTheRoutine_callsTheRpcAndClosesTheSheet() = runSessionTest {
        val vm = loaded()
        logAsPlanned(vm)
        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, 30))
        runCurrent()
        finish(vm)

        vm.onEvent(LiveSessionEvent.UpdateRoutine)
        advanceUntilIdle()

        assertEquals(listOf("s1"), routines.updatedFromSession)
        assertNull(vm.finished().updateRoutineSheet)
    }

    @Test
    fun keepingTheRoutine_neverCallsTheRpc() = runSessionTest {
        val vm = loaded()
        logAsPlanned(vm)
        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, 30))
        runCurrent()
        finish(vm)

        vm.onEvent(LiveSessionEvent.KeepRoutine)
        advanceUntilIdle()

        assertTrue(routines.updatedFromSession.isEmpty())
        assertNull(vm.finished().updateRoutineSheet)
    }

    @Test
    fun withAutoUpdateOn_theRoutineIsUpdatedWithoutTheSheet() = runSessionTest {
        trainingPreferences.result = Result.success(TrainingPreferences(autoUpdateRoutine = true))
        val vm = loaded()
        logAsPlanned(vm)
        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, 30))
        runCurrent()

        finish(vm)

        assertEquals(listOf("s1"), routines.updatedFromSession)
        assertNull(vm.finished().updateRoutineSheet)
    }

    @Test
    fun withAutoUpdateOnAndNoDifference_theRoutineIsNotTouched() = runSessionTest {
        trainingPreferences.result = Result.success(TrainingPreferences(autoUpdateRoutine = true))
        val vm = loaded()
        logAsPlanned(vm)

        finish(vm)

        assertTrue(routines.updatedFromSession.isEmpty())
    }

    @Test
    fun withTrackingOff_nothingIsCompared() = runSessionTest {
        trainingPreferences.result = Result.success(TrainingPreferences(autoUpdateRoutine = true))
        routines.routines[0] = routine().copy(trackAsRoutine = false)
        val vm = loaded()
        logAsPlanned(vm)
        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, 30))
        runCurrent()

        finish(vm)

        assertNull(vm.finished().updateRoutineSheet)
        assertTrue(routines.updatedFromSession.isEmpty())
    }

    @Test
    fun aSessionWithoutARoutine_isNotCompared() = runSessionTest {
        repository.session = freshSession().copy(routineId = null)
        val vm = loaded()
        logAsPlanned(vm)

        finish(vm)

        assertNull(vm.finished().updateRoutineSheet)
        assertTrue(routines.updatedFromSession.isEmpty())
    }

    @Test
    fun aFailedUpdate_keepsTheSheetWithTheFailureAndAllowsARetry() = runSessionTest {
        val vm = loaded()
        logAsPlanned(vm)
        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, 30))
        runCurrent()
        finish(vm)
        routines.updateFailure = AppError.Network

        vm.onEvent(LiveSessionEvent.UpdateRoutine)
        advanceUntilIdle()

        val failed = vm.finished().updateRoutineSheet
        assertEquals(true, failed?.failed)
        assertEquals(false, failed?.loading)

        routines.updateFailure = null
        vm.onEvent(LiveSessionEvent.UpdateRoutine)
        advanceUntilIdle()

        assertNull(vm.finished().updateRoutineSheet)
        assertEquals(listOf("s1"), routines.updatedFromSession)
    }

    @Test
    fun aFailedRoutineLookup_doesNotBlockFinishing() = runSessionTest {
        val vm = loaded()
        logAsPlanned(vm)
        routines.failure = AppError.Network

        finish(vm)

        assertEquals(listOf("s1"), repository.finished)
        assertNull(vm.finished().updateRoutineSheet)
    }

    @Test
    fun aFailedPreferencesRead_asksWithTheSheet() = runSessionTest {
        trainingPreferences.result = Result.failure(AppException(AppError.Network))
        val vm = loaded()
        logAsPlanned(vm)
        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, 30))
        runCurrent()

        finish(vm)

        assertTrue(routines.updatedFromSession.isEmpty())
        assertTrue(vm.finished().updateRoutineSheet != null)
    }

    @Test
    fun aFailedAutoUpdate_doesNotBlockFinishing() = runSessionTest {
        trainingPreferences.result = Result.success(TrainingPreferences(autoUpdateRoutine = true))
        routines.updateFailure = AppError.Network
        val vm = loaded()
        logAsPlanned(vm)
        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, 30))
        runCurrent()

        finish(vm)

        assertNull(vm.finished().updateRoutineSheet)
    }

    @Test
    fun changingTheRest_isSavedOnTheSession() = runSessionTest {
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.SetExerciseRest(1, 75))
        runCurrent()

        assertEquals(listOf(1 to 75), repository.restWrites)
    }

    @Test
    fun aRestChangedOffline_isSentWhenTheConnectionReturns() = runSessionTest {
        val connectivity = FakeConnectivityObserver()
        val vm = LiveSessionViewModel(
            repository, exercises, FakeExerciseLibrary(emptyMap()), connectivity, clock, FakeHaptics(), routines,
            FakeAlertSound(), trainingPreferences, bodyMeasurements,
            SaveBodyMeasurement(bodyMeasurements, clock, TimeZone.UTC), TimeZone.UTC, SavedStateHandle(),
        ).also { created = it }
        advanceUntilIdle()
        connectivity.state.value = false
        runCurrent()

        vm.onEvent(LiveSessionEvent.SetExerciseRest(1, 75))
        runCurrent()
        assertTrue(repository.restWrites.isEmpty())
        connectivity.state.value = true
        runCurrent()

        assertEquals(listOf(1 to 75), repository.restWrites)
    }

    @Test
    fun restsSavedOnTheSession_comeBackAfterTheViewModelIsRecreated() = runSessionTest {
        repository.session = freshSession().copy(exerciseRests = mapOf(0 to 45))

        val vm = loaded()

        val state = assertIs<LiveSessionUiState.Active>(vm.uiState.value)
        assertEquals(listOf(45, 120), state.exercises.map { it.restSeconds })
    }

    @Test
    fun theSheetComesBackAfterRecreationFromTheSameSavedState() = runSessionTest {
        val savedState = SavedStateHandle()
        val vm = loaded(savedState)
        logAsPlanned(vm)
        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, 30))
        runCurrent()
        finish(vm)
        val sheet = vm.finished().updateRoutineSheet
        // The server now holds the session as completed and has no in-progress one.
        repository.completed += repository.session!!.copy(status = SessionStatus.Completed, endedAt = clock.now())
        repository.session = null

        val recreated = loaded(savedState)

        val restored = recreated.finished()
        assertEquals(sheet, restored.updateRoutineSheet)
        recreated.onEvent(LiveSessionEvent.UpdateRoutine)
        advanceUntilIdle()
        assertEquals(listOf("s1"), routines.updatedFromSession)
        assertFalse(recreated.finished().updateRoutineSheet != null)
    }

    private companion object {
        fun freshSession() = sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1), pendingSet(2))),
            SessionExercise("e2", 1, null, listOf(pendingSet(1))),
        )

        fun routine() = Routine(
            id = "r1",
            name = "Torso",
            notes = null,
            archived = false,
            exercises = listOf(
                RoutineExercise("e1", 0, null, List(2) { PlannedSet(60.0, 10) }, restSeconds = 90),
                RoutineExercise("e2", 1, null, listOf(PlannedSet(100.0, 5)), restSeconds = 120),
            ),
        )
    }
}
