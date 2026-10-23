package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeExerciseOptionsRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.SessionSummary
import io.github.lopezjavier.appfitness.domain.usecase.SupersetLabel
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass") // One class per view model: session, ghost data and rest timer share the same setup.
class LiveSessionViewModelTest {
    private val repository = FakeWorkoutSessionRepository(twoExercises())
    private val exercises =
        FakeExerciseRepository(
            listOf(
                exerciseRow("e1", "bench"),
                exerciseRow("e2", "squat"),
                exerciseRow("e3", null, name = "Mi ejercicio", custom = true),
            ),
        )
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press banca")))
    private val connectivity = FakeConnectivityObserver()
    private val clock = MutableClock()
    private val haptics = FakeHaptics()
    private val alertSound = FakeAlertSound()
    private val routines = FakeRoutineRepository(listOf(routineWithRests(0, 0)))

    private var created: LiveSessionViewModel? = null

    private val trainingPreferences = FakeTrainingPreferencesRepository()
    private val bodyMeasurements = FakeWeightRepository()

    private fun newViewModel(savedState: SavedStateHandle = SavedStateHandle()) = LiveSessionViewModel(
        repository,
        exercises,
        library,
        connectivity,
        clock,
        haptics,
        routines,
        alertSound,
        trainingPreferences,
        bodyMeasurements,
        FakeExerciseOptionsRepository(),
        SaveBodyMeasurement(bodyMeasurements, clock, TimeZone.UTC),
        TimeZone.UTC,
        savedState,
    ).also { created = it }

    /** Stops a rest ticker left running, which would never let the virtual scheduler go idle (frozen clock). */
    private fun runSessionTest(body: suspend TestScope.() -> Unit) = runTest {
        body()
        created?.onEvent(LiveSessionEvent.RestSkip)
        runCurrent()
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.loaded(): LiveSessionViewModel {
        val vm = newViewModel()
        advanceUntilIdle()
        return vm
    }

    private fun LiveSessionViewModel.active() = assertIs<LiveSessionUiState.Active>(uiState.value)

    private fun LiveSessionViewModel.row(position: Int, setIndex: Int) =
        active().exercises[position].sets.first { it.setIndex == setIndex }

    private fun TestScope.type(vm: LiveSessionViewModel, position: Int, index: Int, weight: String, reps: String) {
        vm.onEvent(LiveSessionEvent.WeightChanged(position, index, weight))
        vm.onEvent(LiveSessionEvent.RepsChanged(position, index, reps))
        runCurrent() // Not advanceUntilIdle: a running rest ticker never goes idle on a virtual clock.
    }

    private fun TestScope.check(vm: LiveSessionViewModel, position: Int, index: Int, checked: Boolean = true) {
        vm.onEvent(LiveSessionEvent.SetChecked(position, index, checked))
        runCurrent()
    }

    @Test
    fun startsLoading_thenShowsTheSessionWithThreeRowsPerExercise() = runSessionTest {
        val vm = newViewModel()
        assertEquals(LiveSessionUiState.Loading, vm.uiState.value)

        advanceUntilIdle()

        val state = vm.active()
        assertEquals("Torso", state.name)
        assertEquals(SESSION_START, state.startedAt)
        assertEquals(listOf(3, 3), state.exercises.map { it.sets.size })
        assertEquals(listOf("Press banca", null), state.exercises.map { it.name })
        assertTrue(state.exercises.flatMap { it.sets }.none { it.completed || it.pendingSave })
    }

    @Test
    fun openingTheTypePicker_exposesTheTarget_andDismissingChangesNothing() = runSessionTest {
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.OpenTypePicker(0, 2))
        assertEquals(TypePickerTarget(0, 2), vm.active().typePicker)

        vm.onEvent(LiveSessionEvent.DismissTypePicker)
        assertNull(vm.active().typePicker)
        assertEquals(SetType.Working, vm.row(0, 2).type)
        assertTrue(repository.upserts.isEmpty())
    }

    @Test
    fun assigningAType_toAPendingSet_updatesTheRowAndQueuesAWriteWithTheType() = runSessionTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.OpenTypePicker(0, 1))

        vm.onEvent(LiveSessionEvent.SetTypeChosen(0, 1, SetType.WarmUp))
        runCurrent()

        assertEquals(SetType.WarmUp, vm.row(0, 1).type)
        assertNull(vm.active().typePicker)
        val write = repository.upserts.single()
        assertEquals(SetType.WarmUp, write.setType)
        assertEquals(1, write.setIndex)
        assertNull(write.completedAt)
        assertEquals(SetEntry.WeightReps(0.0, 0), write.entry)
    }

    @Test
    fun theAssignedType_survivesAReload() = runSessionTest {
        val first = loaded()
        first.onEvent(LiveSessionEvent.SetTypeChosen(0, 1, SetType.Drop))
        runCurrent()

        val reloaded = loaded()

        assertEquals(SetType.Drop, reloaded.row(0, 1).type)
    }

    @Test
    fun removingAType_setsWorkingAgain() = runSessionTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.SetTypeChosen(0, 1, SetType.Failure))
        runCurrent()

        vm.onEvent(LiveSessionEvent.SetTypeChosen(0, 1, SetType.Working))
        runCurrent()

        assertEquals(SetType.Working, vm.row(0, 1).type)
        assertEquals(listOf(SetType.Failure, SetType.Working), repository.upserts.map { it.setType })
    }

    @Test
    fun changingTheTypeOfACompletedSet_keepsItCompletedWithItsValues() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)
        val completedAt = repository.upserts.single().completedAt

        vm.onEvent(LiveSessionEvent.SetTypeChosen(0, 1, SetType.WarmUp))
        runCurrent()

        val write = repository.upserts.last()
        assertEquals(SetType.WarmUp, write.setType)
        assertEquals(SetEntry.WeightReps(60.0, 12), write.entry)
        assertEquals(completedAt, write.completedAt)
        assertTrue(vm.row(0, 1).completed)
        assertEquals(SetType.WarmUp, vm.row(0, 1).type)
    }

    @Test
    fun theTypeChosenOffline_isQueuedWithItsTypeAndSentOnReconnect() = runSessionTest {
        val vm = loaded()
        connectivity.state.value = false
        runCurrent()

        vm.onEvent(LiveSessionEvent.SetTypeChosen(0, 1, SetType.WarmUp))
        runCurrent()

        assertTrue(repository.upserts.isEmpty())
        assertTrue(vm.row(0, 1).pendingSave)

        connectivity.state.value = true
        advanceUntilIdle()

        assertEquals(listOf(SetType.WarmUp), repository.upserts.map { it.setType })
    }

    @Test
    fun theOpenPicker_isRestoredFromTheSameSavedState() = runSessionTest {
        val handle = SavedStateHandle()
        val first = newViewModel(handle)
        advanceUntilIdle()
        first.onEvent(LiveSessionEvent.OpenTypePicker(1, 2))
        runCurrent()

        val second = newViewModel(handle)
        advanceUntilIdle()

        assertEquals(TypePickerTarget(1, 2), second.active().typePicker)
    }

    @Test
    fun uncheckedDrafts_areRestoredOverTheReloadedSession() = runSessionTest {
        val handle = SavedStateHandle()
        val first = newViewModel(handle)
        advanceUntilIdle()
        type(first, 0, 2, "72,5", "9")

        val second = newViewModel(handle)
        advanceUntilIdle()

        assertEquals("72,5", second.row(0, 2).weight)
        assertEquals("9", second.row(0, 2).reps)
    }

    @Test
    fun aDraftNeverOverridesACompletedSetOfTheServer() = runSessionTest {
        val handle = SavedStateHandle()
        val first = newViewModel(handle)
        advanceUntilIdle()
        type(first, 0, 1, "10", "5")
        repository.session = sessionOf(
            SessionExercise("e1", 0, null, listOf(doneSet(1, 60.0, 12), pendingSet(2), pendingSet(3))),
            SessionExercise("e2", 1, null, listOf(pendingSet(1))),
        )

        val second = newViewModel(handle)
        advanceUntilIdle()

        assertEquals("60", second.row(0, 1).weight)
        assertEquals("12", second.row(0, 1).reps)
    }

    private fun warmUpAndWorkingSession() {
        repository.session = sessionOf(
            SessionExercise(
                "e1",
                0,
                null,
                listOf(
                    WorkoutSet(1, SetType.WarmUp, SetEntry.WeightReps(40.0, 10), SESSION_START),
                    doneSet(2, 80.0, 8),
                ),
            ),
        )
        repository.history = mapOf("e1" to emptyList())
    }

    private fun TestScope.finishedSummary(): SessionSummary {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()
        return assertIs<LiveSessionUiState.Finished>(vm.uiState.value).summary.summary
    }

    @Test
    fun finishingSummary_excludesWarmUpsByDefault() = runSessionTest {
        warmUpAndWorkingSession()

        val summary = finishedSummary()

        assertEquals(1, summary.completedSets)
        assertEquals(640.0, summary.exercises.single().volume)
    }

    @Test
    fun finishingSummary_countsWarmUpsWhenThePreferenceIsOn() = runSessionTest {
        warmUpAndWorkingSession()
        trainingPreferences.result = Result.success(TrainingPreferences(warmUpIncluded = true))

        val summary = finishedSummary()

        assertEquals(2, summary.completedSets)
        assertEquals(1040.0, summary.exercises.single().volume)
    }

    @Test
    fun finishingSummary_aFailedPreferenceReadFallsBackToExcludingWarmUps() = runSessionTest {
        warmUpAndWorkingSession()
        trainingPreferences.result = Result.failure(AppException(AppError.Network))

        val summary = finishedSummary()

        assertEquals(1, summary.completedSets)
    }

    @Test
    fun customExercisesShowTheirOwnName() = runSessionTest {
        repository.session = sessionOf(SessionExercise("e3", 0, null, listOf(pendingSet(1))))

        val vm = loaded()

        assertEquals("Mi ejercicio", vm.active().exercises.single().name)
    }

    @Test
    fun supersetExercisesCarryTheirLabel() = runSessionTest {
        repository.session = sessionOf(
            SessionExercise("e1", 0, 4, listOf(pendingSet(1))),
            SessionExercise("e2", 1, 4, listOf(pendingSet(1))),
        )

        val vm = loaded()

        assertEquals(
            listOf(SupersetLabel('A', 1), SupersetLabel('A', 2)),
            vm.active().exercises.map { it.supersetLabel },
        )
    }

    @Test
    fun noInProgressSession_isNoSession() = runSessionTest {
        repository.session = null

        val vm = loaded()

        assertEquals(LiveSessionUiState.NoSession, vm.uiState.value)
    }

    @Test
    fun loadFailure_isAnErrorThatRetryRecovers() = runSessionTest {
        repository.getFailure = AppError.Network
        val vm = loaded()
        assertEquals(LiveSessionUiState.Error, vm.uiState.value)

        repository.getFailure = null
        vm.onEvent(LiveSessionEvent.Retry)
        assertEquals(LiveSessionUiState.Loading, vm.uiState.value)
        advanceUntilIdle()

        assertIs<LiveSessionUiState.Active>(vm.uiState.value)
    }

    @Test
    fun exerciseNamesFailing_stillShowsTheSessionWithoutNames() = runSessionTest {
        exercises.failure = AppError.Network

        val vm = loaded()

        assertEquals(listOf(null, null), vm.active().exercises.map { it.name })
    }

    @Test
    fun resumingRestoresTheCheckedRows() = runSessionTest {
        repository.session = sessionOf(
            SessionExercise("e1", 0, null, listOf(doneSet(1, 60.0, 12), doneSet(2, 87.5, 8), pendingSet(3))),
        )

        val vm = loaded()

        val sets = vm.active().exercises.single().sets
        assertEquals(listOf(true, true, false), sets.map { it.completed })
        assertEquals("60" to "12", sets[0].weight to sets[0].reps)
        assertEquals("87,5" to "8", sets[1].weight to sets[1].reps)
        assertEquals("" to "", sets[2].weight to sets[2].reps)
    }

    @Test
    fun checkingASet_ticksOnce_andUpsertsItCompletedNow() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        clock.advance(30.seconds)

        check(vm, 0, 1)

        assertEquals(1, haptics.ticks)
        val write = repository.upserts.single()
        assertEquals(Triple("e1", 0, 1), Triple(write.exerciseId, write.exercisePosition, write.setIndex))
        assertEquals(SetEntry.WeightReps(60.0, 12), write.entry)
        assertEquals(clock.current, write.completedAt)
        assertTrue(vm.row(0, 1).completed)
        assertFalse(vm.row(0, 1).pendingSave)
    }

    @Test
    fun weightAcceptsACommaOrADot() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "87,5", "8")
        type(vm, 0, 2, "87.5", "8")

        check(vm, 0, 1)
        check(vm, 0, 2)

        assertEquals(listOf(87.5, 87.5), repository.upserts.map { (it.entry as SetEntry.WeightReps).weightKg })
    }

    @Test
    fun editingADraftDoesNotSaveAnything() = runSessionTest {
        val vm = loaded()

        type(vm, 0, 1, "60", "12")

        assertTrue(repository.upserts.isEmpty())
        assertEquals("60" to "12", vm.row(0, 1).weight to vm.row(0, 1).reps)
    }

    @Test
    fun uncheckingASet_upsertsItPendingAndMakesItEditable() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)

        check(vm, 0, 1, checked = false)
        type(vm, 0, 1, "62,5", "12")

        assertNull(repository.upserts.last().completedAt)
        assertEquals(1, haptics.ticks)
        assertFalse(vm.row(0, 1).completed)
        assertEquals("62,5", vm.row(0, 1).weight)
    }

    @Test
    fun aCompletedRowIgnoresEdits() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)

        type(vm, 0, 1, "99", "99")

        assertEquals("60" to "12", vm.row(0, 1).weight to vm.row(0, 1).reps)
    }

    @Test
    fun checkingWithEmptyReps_isRefusedWithoutHapticOrUpsert() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "")

        check(vm, 0, 1)

        assertTrue(repository.upserts.isEmpty())
        assertEquals(0, haptics.ticks)
        assertFalse(vm.row(0, 1).completed)
        assertTrue(vm.row(0, 1).invalid)
    }

    @Test
    fun checkingWithZeroRepsOrNoWeight_isRefused() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "0")
        type(vm, 0, 2, "", "10")

        check(vm, 0, 1)
        check(vm, 0, 2)

        assertTrue(repository.upserts.isEmpty())
    }

    @Test
    fun aZeroWeightWithRepsIsValid() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "0", "10")

        check(vm, 0, 1)

        assertEquals(SetEntry.WeightReps(0.0, 10), repository.upserts.single().entry)
    }

    @Test
    fun editingARefusedRowClearsTheInvalidMark() = runSessionTest {
        val vm = loaded()
        check(vm, 0, 1)

        type(vm, 0, 1, "60", "10")

        assertFalse(vm.row(0, 1).invalid)
    }

    @Test
    fun addingASet_appendsTheNextIndexWithThePreviousValuesAsPlaceholders() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 3, "87,5", "8")

        vm.onEvent(LiveSessionEvent.AddSet(0))
        advanceUntilIdle()

        val added = vm.active().exercises[0].sets.last()
        assertEquals(4, added.setIndex)
        assertEquals("87,5" to "8", added.weightPlaceholder to added.repsPlaceholder)
        assertEquals("" to "", added.weight to added.reps)
        val write = repository.upserts.single()
        assertEquals(4, write.setIndex)
        assertEquals(SetEntry.WeightReps(0.0, 0), write.entry)
        assertNull(write.completedAt)
    }

    @Test
    fun addingAnExercise_asksForThePicker_thenAppendsItAtTheEndWithOneEmptySet() = runSessionTest {
        val vm = loaded()

        vm.effects.test {
            vm.onEvent(LiveSessionEvent.AddExercise)
            assertEquals(LiveSessionEffect.OpenPicker, awaitItem())
        }
        vm.onEvent(LiveSessionEvent.ExerciseSelected("e3"))
        advanceUntilIdle()

        val added = vm.active().exercises.last()
        assertEquals(2, added.position)
        assertEquals("Mi ejercicio", added.name)
        assertEquals(listOf(1), added.sets.map { it.setIndex })
        val write = repository.upserts.single()
        assertEquals(Triple("e3", 2, 1), Triple(write.exerciseId, write.exercisePosition, write.setIndex))
        assertNull(write.supersetGroup)
        assertNull(write.completedAt)
    }

    @Test
    fun offlineCheck_marksTheRowPendingWithoutCallingTheServer() = runSessionTest {
        connectivity.state.value = false
        val vm = loaded()
        type(vm, 0, 1, "60", "12")

        check(vm, 0, 1)

        assertTrue(repository.upserts.isEmpty())
        assertTrue(vm.row(0, 1).completed)
        assertTrue(vm.row(0, 1).pendingSave)
        assertEquals(1, vm.active().queuedCount)
        assertEquals(1, haptics.ticks)
    }

    @Test
    fun reconnecting_flushesTheQueueOnceAndClearsTheMarker() = runSessionTest {
        connectivity.state.value = false
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)

        connectivity.state.value = true
        advanceUntilIdle()

        assertEquals(1, repository.upserts.size)
        assertEquals(SetEntry.WeightReps(60.0, 12), repository.upserts.single().entry)
        assertFalse(vm.row(0, 1).pendingSave)
        assertEquals(0, vm.active().queuedCount)
    }

    @Test
    fun anUpsertFailureWhileOnline_queuesTheSetAndTheNextReconnectSendsIt() = runSessionTest {
        repository.upsertFailure = AppError.Network
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)
        assertTrue(vm.row(0, 1).pendingSave)
        assertTrue(vm.row(0, 1).completed)

        repository.upsertFailure = null
        connectivity.state.value = false
        advanceUntilIdle()
        connectivity.state.value = true
        advanceUntilIdle()

        assertEquals(1, repository.upserts.size)
        assertFalse(vm.row(0, 1).pendingSave)
    }

    @Test
    fun anUpsertFailureWhileOnline_isRetriedWithBackoffAndClearsTheMarkerWithoutAConnectivityChange() = runSessionTest {
        repository.upsertFailure = AppError.Network
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        vm.onEvent(LiveSessionEvent.SetChecked(0, 1, true))
        runCurrent()
        assertTrue(vm.row(0, 1).pendingSave)

        repository.upsertFailure = null
        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(1, repository.upserts.size)
        assertFalse(vm.row(0, 1).pendingSave)
    }

    @Test
    fun retriesAreBounded_soAPersistentFailureStaysPendingAfterThem() = runSessionTest {
        repository.upsertFailure = AppError.Network
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)
        advanceTimeBy(60_000)
        runCurrent()

        repository.upsertFailure = null
        advanceTimeBy(60_000)
        runCurrent()

        assertTrue(repository.upserts.isEmpty())
        assertTrue(vm.row(0, 1).pendingSave)
    }

    @Test
    fun aLaterSuccessfulWriteAlsoSendsTheQueuedOnes() = runSessionTest {
        repository.upsertFailure = AppError.Network
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)
        repository.upsertFailure = null
        type(vm, 0, 2, "60", "12")

        check(vm, 0, 2)

        assertEquals(listOf(1, 2), repository.upserts.map { it.setIndex })
        assertEquals(0, vm.active().queuedCount)
    }

    @Test
    fun twoEditsOfTheSameQueuedSet_sendOnlyTheLast() = runSessionTest {
        connectivity.state.value = false
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)
        check(vm, 0, 1, checked = false)

        connectivity.state.value = true
        advanceUntilIdle()

        val write = repository.upserts.single()
        assertNull(write.completedAt)
    }

    @Test
    fun requestingFinish_countsTheIncompleteSets() = runSessionTest {
        val vm = loaded()
        completeBenchSets(vm)

        vm.onEvent(LiveSessionEvent.RequestFinish)

        assertEquals(FinishSheet(pendingCount = 3), vm.active().finishSheet)
    }

    @Test
    fun dismissingTheFinishSheet_closesItWithoutFinishing() = runSessionTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.RequestFinish)

        vm.onEvent(LiveSessionEvent.DismissFinish)

        assertNull(vm.active().finishSheet)
        assertTrue(repository.finished.isEmpty())
    }

    @Test
    fun confirmingFinish_finishesAndShowsTheSummary() = runSessionTest {
        repository.history = mapOf("e1" to emptyList(), "e2" to emptyList())
        val vm = loaded()
        completeBenchSets(vm)
        clock.advance(3150.seconds)

        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()

        assertEquals(listOf("s1"), repository.finished)
        val summary = assertIs<LiveSessionUiState.Finished>(vm.uiState.value).summary
        assertEquals(3150L, summary.summary.durationSeconds)
        assertEquals(3, summary.summary.completedSets)
        assertEquals(2120.0, summary.summary.exercises[0].volume)
        assertEquals(mapOf(0 to "Press banca", 1 to null), summary.exerciseNames)
    }

    @Test
    fun summaryMarksAFirstEverSessionAsARecord_andNotALowerOneRepMax() = runSessionTest {
        repository.history = mapOf(
            "e1" to listOf(ExerciseHistoryPoint("old", LocalDate(2026, 10, 1), 500.0, 0.0)),
            "e2" to emptyList(),
        )
        repository.session = sessionOf(
            SessionExercise("e1", 0, null, listOf(doneSet(1, 60.0, 12))),
            SessionExercise("e2", 1, null, listOf(doneSet(1, 100.0, 5))),
        )
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()

        val exercises = assertIs<LiveSessionUiState.Finished>(vm.uiState.value).summary.summary.exercises
        assertEquals(listOf(false, true), exercises.map { it.isPersonalRecord })
    }

    @Test
    fun historyFailure_stillShowsTheSummaryWithoutRecords() = runSessionTest {
        repository.historyFailure = AppError.Network
        val vm = loaded()
        completeBenchSets(vm)

        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()

        val exercises = assertIs<LiveSessionUiState.Finished>(vm.uiState.value).summary.summary.exercises
        assertTrue(exercises.none { it.isPersonalRecord })
    }

    @Test
    fun finishFailure_keepsTheStateAndReportsIt() = runSessionTest {
        repository.finishFailure = AppError.Network
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.RequestFinish)

        vm.effects.test {
            vm.onEvent(LiveSessionEvent.ConfirmFinish)
            advanceUntilIdle()
            assertEquals(LiveSessionEffect.ActionFailed, awaitItem())
        }

        val state = vm.active()
        assertFalse(state.busy)
        assertEquals(FinishSheet(6), state.finishSheet)
    }

    @Test
    fun finishingOffline_isBlocked() = runSessionTest {
        connectivity.state.value = false
        val vm = loaded()
        assertTrue(vm.active().actionsBlocked)

        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.RequestDiscard)

        assertNull(vm.active().finishSheet)
        assertFalse(vm.active().discardConfirm)
    }

    @Test
    fun finishingFlushesTheQueueFirst_andKeepsTheStateIfItCannotBeSent() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        repository.upsertFailure = AppError.Network
        check(vm, 0, 1)
        vm.onEvent(LiveSessionEvent.RequestFinish)

        vm.effects.test {
            vm.onEvent(LiveSessionEvent.ConfirmFinish)
            advanceUntilIdle()
            assertEquals(LiveSessionEffect.ActionFailed, awaitItem())
        }

        assertTrue(repository.finished.isEmpty())
        assertIs<LiveSessionUiState.Active>(vm.uiState.value)
    }

    @Test
    fun confirmingDiscard_discardsAndCloses() = runSessionTest {
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.RequestDiscard)
        assertTrue(vm.active().discardConfirm)
        vm.effects.test {
            vm.onEvent(LiveSessionEvent.ConfirmDiscard)
            advanceUntilIdle()
            assertEquals(LiveSessionEffect.Closed, awaitItem())
        }

        assertEquals(listOf("s1"), repository.discarded)
    }

    @Test
    fun dismissingDiscard_keepsTheSession() = runSessionTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.RequestDiscard)

        vm.onEvent(LiveSessionEvent.DismissDiscard)

        assertFalse(vm.active().discardConfirm)
        assertTrue(repository.discarded.isEmpty())
    }

    @Test
    fun discardFailure_keepsTheStateAndReportsIt() = runSessionTest {
        repository.discardFailure = AppError.Network
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.RequestDiscard)

        vm.effects.test {
            vm.onEvent(LiveSessionEvent.ConfirmDiscard)
            advanceUntilIdle()
            assertEquals(LiveSessionEffect.ActionFailed, awaitItem())
        }

        assertFalse(vm.active().busy)
        assertIs<LiveSessionUiState.Active>(vm.uiState.value)
    }

    @Test
    fun done_closesTheFlow() = runSessionTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()

        vm.effects.test {
            vm.onEvent(LiveSessionEvent.Done)
            assertEquals(LiveSessionEffect.Closed, awaitItem())
        }
    }

    @Test
    fun theStopwatchCountsFromTheStartWithTheInjectedClock() = runSessionTest {
        val vm = loaded()
        clock.advance(90.seconds)

        vm.elapsedSeconds.test {
            assertEquals(90L, awaitItem())
            clock.advance(5.seconds)
            advanceTimeBy(1_001)
            assertEquals(95L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.completeBenchSets(vm: LiveSessionViewModel) {
        type(vm, 0, 1, "60", "12")
        type(vm, 0, 2, "87,5", "8")
        type(vm, 0, 3, "87,5", "8")
        check(vm, 0, 1)
        check(vm, 0, 2)
        check(vm, 0, 3)
    }

    private fun replaceRoutine(vararg rests: Int) {
        routines.routines.clear()
        routines.routines += routineWithRests(*rests)
    }

    // --- Ghost data (US-12) ---

    @Test
    fun ghostIsLoadedOncePerExerciseAtLoad() = runSessionTest {
        repository.session = sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1))),
            SessionExercise("e1", 1, null, listOf(pendingSet(1))),
            SessionExercise("e2", 2, null, listOf(pendingSet(1))),
        )

        loaded()

        assertEquals(listOf("e1", "e2"), repository.lastPerformanceCalls)
    }

    @Test
    fun ghostFeedsThePreviousTextAndThePlaceholders_andMissingSetsHaveNone() = runSessionTest {
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 80.0, 10), PreviousSet(2, 85.0, 8)))
        routines.routines.clear()

        val vm = loaded()

        val first = vm.row(0, 1)
        assertEquals("80 kg × 10", first.previous)
        assertEquals("80" to "10", first.weightPlaceholder to first.repsPlaceholder)
        assertEquals("85 kg × 8", vm.row(0, 2).previous)
        assertNull(vm.row(0, 3).previous)
        assertNull(vm.row(1, 1).previous)
        assertEquals("" to "", first.weight to first.reps)
    }

    @Test
    fun ghostFailure_isTolerated() = runSessionTest {
        repository.lastPerformanceFailure = AppError.Network

        val vm = loaded()

        assertNull(vm.row(0, 1).previous)
        assertEquals(6, vm.active().exercises.sumOf { it.sets.size })
    }

    @Test
    fun usePrevious_fillsTheDraftsAndTheSetIsWrittenOnCheck() = runSessionTest {
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 80.0, 10), PreviousSet(2, 82.5, 8)))
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.UsePrevious(0, 2))
        runCurrent()

        assertEquals("82,5" to "8", vm.row(0, 2).weight to vm.row(0, 2).reps)
        assertTrue(repository.upserts.isEmpty())
        check(vm, 0, 2)
        assertEquals(SetEntry.WeightReps(82.5, 8), repository.upserts.single().entry)
    }

    @Test
    fun usePrevious_withoutGhostOrOnACompletedSet_changesNothing() = runSessionTest {
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 80.0, 10)))
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)

        vm.onEvent(LiveSessionEvent.UsePrevious(0, 1))
        vm.onEvent(LiveSessionEvent.UsePrevious(0, 3))
        vm.onEvent(LiveSessionEvent.UsePrevious(1, 1))

        assertEquals("60" to "12", vm.row(0, 1).weight to vm.row(0, 1).reps)
        assertEquals("" to "", vm.row(0, 3).weight to vm.row(0, 3).reps)
    }

    @Test
    fun addingAnExercise_loadsItsGhost() = runSessionTest {
        repository.previous = mapOf("e3" to listOf(PreviousSet(1, 40.0, 12)))
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.ExerciseSelected("e3"))
        advanceUntilIdle()

        assertEquals(listOf("e1", "e2", "e3"), repository.lastPerformanceCalls)
        assertEquals("40 kg × 12", vm.row(2, 1).previous)
    }

    // --- Rest timer (US-13) ---

    private fun TestScope.timerVm(vararg rests: Int): LiveSessionViewModel {
        replaceRoutine(*rests)
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        return vm
    }

    @Test
    fun noTimerRunsBeforeASetIsChecked() = runSessionTest {
        val vm = timerVm(120, 120)

        assertNull(vm.active().restTimer)
    }

    @Test
    fun checkingASet_startsTheRoutineRest() = runSessionTest {
        val vm = timerVm(90, 120)

        check(vm, 0, 1)

        assertEquals(RestTimerUi(90.seconds, 90.seconds), vm.active().restTimer)
        assertEquals(listOf(90, 120), vm.active().exercises.map { it.restSeconds })
    }

    @Test
    fun zeroRest_startsNoTimer() = runSessionTest {
        val vm = timerVm(0, 120)

        check(vm, 0, 1)

        assertNull(vm.active().restTimer)
    }

    @Test
    fun countdownIsRecomputedFromTheClock_evenAfterABackgroundGap() = runSessionTest {
        val vm = timerVm(120, 120)
        check(vm, 0, 1)

        clock.advance(100.seconds)
        vm.onEvent(LiveSessionEvent.Resume)

        assertEquals(20.seconds, vm.active().restTimer?.remaining)
    }

    @Test
    fun theTickerRefreshesTheRemainingTimeEverySecond() = runSessionTest {
        val vm = timerVm(120, 120)
        check(vm, 0, 1)

        clock.advance(1.seconds)
        advanceTimeBy(1_001)

        assertEquals(119.seconds, vm.active().restTimer?.remaining)
    }

    @Test
    fun add30_extendsWhatIsLeft_andGrowsTheTotalWhenExceeded() = runSessionTest {
        val vm = timerVm(60, 120)
        check(vm, 0, 1)
        clock.advance(40.seconds)

        vm.onEvent(LiveSessionEvent.RestAdd30)

        assertEquals(RestTimerUi(50.seconds, 60.seconds), vm.active().restTimer)
        vm.onEvent(LiveSessionEvent.RestAdd30)
        assertEquals(RestTimerUi(80.seconds, 80.seconds), vm.active().restTimer)
    }

    @Test
    fun skip_removesTheTimerWithoutAlert() = runSessionTest {
        val vm = timerVm(60, 120)
        check(vm, 0, 1)

        vm.onEvent(LiveSessionEvent.RestSkip)
        clock.advance(120.seconds)
        advanceTimeBy(5_000)

        assertNull(vm.active().restTimer)
        assertEquals(0, haptics.alerts)
        assertEquals(0, alertSound.plays)
    }

    @Test
    fun atZero_alertsOnce_andTheTimerDisappears() = runSessionTest {
        val vm = timerVm(30, 120)
        check(vm, 0, 1)

        clock.advance(29.seconds)
        advanceTimeBy(1_001)
        assertEquals(0, haptics.alerts)
        assertEquals(1.seconds, vm.active().restTimer?.remaining)
        clock.advance(1.seconds)
        advanceTimeBy(1_001)
        advanceTimeBy(5_000)

        assertNull(vm.active().restTimer)
        assertEquals(1, haptics.alerts)
        assertEquals(1, alertSound.plays)
    }

    @Test
    fun restEndingInTheBackground_resolvesTheBarWithoutAlert() = runSessionTest {
        val vm = timerVm(30, 120)
        check(vm, 0, 1)
        vm.onEvent(LiveSessionEvent.Pause)

        clock.advance(31.seconds)
        advanceTimeBy(1_001)

        assertNull(vm.active().restTimer)
        assertEquals(0, haptics.alerts)
        assertEquals(0, alertSound.plays)
    }

    @Test
    fun checkingAnotherSet_restartsTheTimerWithThatExercisesRest() = runSessionTest {
        val vm = timerVm(60, 90)
        check(vm, 0, 1)
        clock.advance(20.seconds)
        type(vm, 1, 1, "40", "10")

        check(vm, 1, 1)

        assertEquals(RestTimerUi(90.seconds, 90.seconds), vm.active().restTimer)
    }

    @Test
    fun uncheckingASet_doesNotStopTheTimer() = runSessionTest {
        val vm = timerVm(60, 120)
        check(vm, 0, 1)

        check(vm, 0, 1, checked = false)

        assertEquals(60.seconds, vm.active().restTimer?.remaining)
    }

    @Test
    fun supersetMembers_useTheirOwnRest_transitionThenBetweenRounds() = runSessionTest {
        repository.session = sessionOf(
            SessionExercise("e1", 0, 1, listOf(pendingSet(1))),
            SessionExercise("e2", 1, 1, listOf(pendingSet(1))),
        )
        replaceRoutine(0, 90)
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        type(vm, 1, 1, "40", "10")

        check(vm, 0, 1)
        assertNull(vm.active().restTimer)
        check(vm, 1, 1)

        assertEquals(90.seconds, vm.active().restTimer?.remaining)
    }

    @Test
    fun exercisesAddedDuringTheSession_rest120Seconds() = runSessionTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.ExerciseSelected("e3"))
        advanceUntilIdle()
        type(vm, 2, 1, "40", "10")

        check(vm, 2, 1)

        assertEquals(120.seconds, vm.active().restTimer?.remaining)
        assertEquals(120, vm.active().exercises[2].restSeconds)
    }

    @Test
    fun aRoutineReadFailure_fallsBackTo120Seconds() = runSessionTest {
        routines.failure = AppError.Network
        val vm = loaded()
        type(vm, 0, 1, "60", "12")

        check(vm, 0, 1)

        assertEquals(120.seconds, vm.active().restTimer?.remaining)
    }

    @Test
    fun aSessionOverride_appliesToFollowingSetsOnly_andNeverSavesTheRoutine() = runSessionTest {
        val vm = timerVm(60, 120)
        check(vm, 0, 1)
        assertEquals(60.seconds, vm.active().restTimer?.remaining)

        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, 90))
        assertEquals(60.seconds, vm.active().restTimer?.remaining)
        type(vm, 0, 2, "60", "12")
        check(vm, 0, 2)

        assertEquals(90.seconds, vm.active().restTimer?.remaining)
        assertEquals(90, vm.active().exercises[0].restSeconds)
        assertTrue(routines.saved.isEmpty())
    }

    @Test
    fun aRestOverride_isClampedToTheRoutineBounds() = runSessionTest {
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, 9_999))
        assertEquals(600, vm.active().exercises[0].restSeconds)
        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, -5))
        assertEquals(0, vm.active().exercises[0].restSeconds)
    }

    @Test
    fun finishing_removesTheTimer() = runSessionTest {
        val vm = timerVm(60, 120)
        check(vm, 0, 1)

        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        runCurrent()
        clock.advance(120.seconds)
        advanceTimeBy(5_000)

        assertEquals(0, haptics.alerts)
    }

    // --- Planned targets (US-29, ADR-50) ---

    private fun plannedRoutine(vararg sets: PlannedSet) = Routine(
        id = "r1",
        name = "Torso",
        notes = null,
        archived = false,
        exercises = listOf(RoutineExercise("e1", 0, null, sets.toList(), restSeconds = 90)),
    )

    private fun plannedSession() = sessionOf(
        SessionExercise(
            "e1",
            0,
            null,
            listOf(
                WorkoutSet(1, SetType.WarmUp, SetEntry.WeightReps(0.0, 0), null),
                pendingSet(2),
                pendingSet(3),
                pendingSet(4),
            ),
        ),
    )

    private fun planWarmUpAndThreeWorking() {
        routines.routines.clear()
        routines.routines += plannedRoutine(
            PlannedSet(40.0, 10, SetType.WarmUp),
            PlannedSet(80.0, 8),
            PlannedSet(80.0, 8),
            PlannedSet(80.0, 8),
        )
        repository.session = plannedSession()
    }

    @Test
    fun plannedWeightAndReps_areShownAsPlaceholders_notAsValues() = runSessionTest {
        planWarmUpAndThreeWorking()

        val vm = loaded()

        assertEquals(4, vm.active().exercises.single().sets.size)
        val first = vm.row(0, 1)
        assertEquals("40" to "10", first.weightPlaceholder to first.repsPlaceholder)
        assertEquals("" to "", first.weight to first.reps)
        val second = vm.row(0, 2)
        assertEquals("80" to "8", second.weightPlaceholder to second.repsPlaceholder)
        assertEquals("" to "", second.weight to second.reps)
    }

    @Test
    fun theStoredSetType_reachesTheRows() = runSessionTest {
        planWarmUpAndThreeWorking()

        val vm = loaded()

        assertEquals(
            listOf(SetType.WarmUp, SetType.Working, SetType.Working, SetType.Working),
            vm.active().exercises.single().sets.map { it.type },
        )
    }

    @Test
    fun plannedTargets_leaveThePreviousColumnUnchanged() = runSessionTest {
        planWarmUpAndThreeWorking()
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 30.0, 12), PreviousSet(2, 75.0, 9)))

        val vm = loaded()

        assertEquals("30 kg × 12", vm.row(0, 1).previous)
        assertEquals("75 kg × 9", vm.row(0, 2).previous)
        assertNull(vm.row(0, 3).previous)
    }

    @Test
    fun aPlannedSetWithoutWeight_fallsBackToTheGhostWeight() = runSessionTest {
        routines.routines.clear()
        routines.routines += plannedRoutine(PlannedSet(null, 6), PlannedSet(null, 6))
        repository.session = sessionOf(SessionExercise("e1", 0, null, listOf(pendingSet(1), pendingSet(2))))
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 70.0, 10)))

        val vm = loaded()

        assertEquals("70" to "6", vm.row(0, 1).weightPlaceholder to vm.row(0, 1).repsPlaceholder)
        assertNull(vm.row(0, 2).weightPlaceholder)
        assertEquals("6", vm.row(0, 2).repsPlaceholder)
    }

    @Test
    fun withoutTheRoutine_thePlaceholdersAreTheGhostOnes() = runSessionTest {
        planWarmUpAndThreeWorking()
        routines.routines.clear()
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 30.0, 12)))

        val vm = loaded()

        assertEquals("30" to "12", vm.row(0, 1).weightPlaceholder to vm.row(0, 1).repsPlaceholder)
        assertNull(vm.row(0, 2).weightPlaceholder)
    }

    @Test
    fun whenTheRoutineCannotBeRead_theSessionStillWorksWithGhostPlaceholders() = runSessionTest {
        planWarmUpAndThreeWorking()
        routines.failure = AppError.Network
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 30.0, 12)))

        val vm = loaded()

        assertEquals("30", vm.row(0, 1).weightPlaceholder)
        assertEquals(SetType.WarmUp, vm.row(0, 1).type)
    }

    @Test
    fun aTypedValue_isKeptApartFromThePlaceholder() = runSessionTest {
        planWarmUpAndThreeWorking()
        val vm = loaded()

        type(vm, 0, 2, "85", "6")

        val row = vm.row(0, 2)
        assertEquals("85" to "6", row.weight to row.reps)
        assertEquals("80" to "8", row.weightPlaceholder to row.repsPlaceholder)
        check(vm, 0, 2)
        assertEquals(85.0, (repository.upserts.last().entry as SetEntry.WeightReps).weightKg)
    }

    @Test
    fun anExtraSet_hasNoPlannedTarget() = runSessionTest {
        planWarmUpAndThreeWorking()
        val vm = loaded()
        type(vm, 0, 4, "82,5", "7")

        vm.onEvent(LiveSessionEvent.AddSet(0))
        runCurrent()

        val added = vm.row(0, 5)
        assertEquals("82,5" to "7", added.weightPlaceholder to added.repsPlaceholder)
    }

    // --- RPE per set (US-30, ADR-50) ---

    private fun LiveSessionViewModel.chooseRpe(position: Int, setIndex: Int, rpe: Double?) {
        onEvent(LiveSessionEvent.SetRpeChosen(position, setIndex, rpe))
    }

    @Test
    fun openingTheRpePicker_exposesTheTarget_andDismissingChangesNothing() = runSessionTest {
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.OpenRpePicker(0, 2))
        assertEquals(RpePickerTarget(0, 2), vm.active().rpePicker)
        vm.onEvent(LiveSessionEvent.DismissRpePicker)

        assertNull(vm.active().rpePicker)
        assertTrue(repository.upserts.isEmpty())
    }

    @Test
    fun pickingAnRpe_showsItInTheRow_andSavesIt() = runSessionTest {
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.OpenRpePicker(0, 1))

        vm.chooseRpe(0, 1, 7.5)
        runCurrent()

        assertEquals("7,5", vm.row(0, 1).rpe)
        assertNull(vm.active().rpePicker)
        assertEquals(7.5, repository.upserts.single().rpe)
    }

    @Test
    fun pickingAnRpeOnACompletedSet_keepsItCompletedAndKeepsTheValues() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "60", "12")
        check(vm, 0, 1)

        vm.chooseRpe(0, 1, 8.0)
        runCurrent()

        val write = repository.upserts.last()
        assertEquals(8.0, write.rpe)
        assertEquals(SetEntry.WeightReps(60.0, 12), write.entry)
        assertTrue(vm.row(0, 1).completed)
        assertEquals("8", vm.row(0, 1).rpe)
    }

    @Test
    fun removingTheRpe_clearsItAndSendsNull() = runSessionTest {
        val vm = loaded()
        vm.chooseRpe(0, 1, 9.0)
        runCurrent()

        vm.chooseRpe(0, 1, null)
        runCurrent()

        assertNull(vm.row(0, 1).rpe)
        assertEquals(listOf(9.0, null), repository.upserts.map { it.rpe })
    }

    @Test
    fun anRpeOffTheGrid_isIgnored() = runSessionTest {
        val vm = loaded()

        vm.chooseRpe(0, 1, 5.5)
        runCurrent()

        assertNull(vm.row(0, 1).rpe)
        assertTrue(repository.upserts.isEmpty())
    }

    @Test
    fun checkingAndUncheckingASet_keepsItsRpe() = runSessionTest {
        val vm = loaded()
        vm.chooseRpe(0, 1, 7.5)
        runCurrent()
        type(vm, 0, 1, "60", "12")

        check(vm, 0, 1)
        check(vm, 0, 1, checked = false)

        assertEquals(listOf(7.5, 7.5, 7.5), repository.upserts.map { it.rpe })
        assertEquals("7,5", vm.row(0, 1).rpe)
    }

    @Test
    fun anAddedSet_hasNoRpe() = runSessionTest {
        val vm = loaded()
        vm.chooseRpe(0, 1, 7.5)
        runCurrent()

        vm.onEvent(LiveSessionEvent.AddSet(0))
        runCurrent()

        assertNull(vm.row(0, 4).rpe)
    }

    @Test
    fun theRpeChosenOffline_isQueuedAndSentOnReconnect() = runSessionTest {
        val vm = loaded()
        connectivity.state.value = false
        runCurrent()

        vm.chooseRpe(0, 1, 8.5)
        runCurrent()
        assertTrue(repository.upserts.isEmpty())
        assertEquals("8,5", vm.row(0, 1).rpe)

        connectivity.state.value = true
        advanceUntilIdle()

        assertEquals(listOf(8.5), repository.upserts.map { it.rpe })
    }

    @Test
    fun theLoggedRpe_survivesAReload() = runSessionTest {
        val first = loaded()
        first.chooseRpe(0, 1, 7.5)
        runCurrent()

        val reloaded = loaded()

        assertEquals("7,5", reloaded.row(0, 1).rpe)
    }

    @Test
    fun theOpenRpePicker_isRestoredFromTheSameSavedState_andClearedWhenTheSessionEnds() = runSessionTest {
        val handle = SavedStateHandle()
        val first = newViewModel(handle)
        advanceUntilIdle()
        first.onEvent(LiveSessionEvent.OpenRpePicker(1, 2))
        runCurrent()

        val second = newViewModel(handle)
        advanceUntilIdle()
        assertEquals(RpePickerTarget(1, 2), second.active().rpePicker)

        second.onEvent(LiveSessionEvent.RequestDiscard)
        second.onEvent(LiveSessionEvent.ConfirmDiscard)
        advanceUntilIdle()
        val third = newViewModel(handle)
        advanceUntilIdle()
        assertNull((third.uiState.value as? LiveSessionUiState.Active)?.rpePicker)
    }

    @Test
    fun aRoutineExerciseWithATargetRpe_showsItAsText() = runSessionTest {
        routines.routines.clear()
        routines.routines += Routine(
            "r1",
            "Torso",
            null,
            false,
            listOf(
                RoutineExercise("e1", 0, null, List(3) { PlannedSet(null, 8) }, 90, targetRpe = 8.0),
                RoutineExercise("e2", 1, null, List(3) { PlannedSet(null, 8) }, 90, targetRpe = 7.5),
            ),
        )

        val vm = loaded()

        assertEquals(listOf("8", "7,5"), vm.active().exercises.map { it.targetRpeText })
    }

    @Test
    fun aRoutineExerciseWithoutATarget_hasNoTargetText() = runSessionTest {
        val vm = loaded()

        assertEquals(listOf(null, null), vm.active().exercises.map { it.targetRpeText })
    }

    @Test
    fun aSessionWithoutARoutine_hasNoTargetText() = runSessionTest {
        repository.session = repository.session!!.copy(routineId = null)

        val vm = loaded()

        assertNull(vm.active().exercises.first().targetRpeText)
    }

    @Test
    fun theGhostShowsLastTimesRpeOnItsSetOnly() = runSessionTest {
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 80.0, 10), PreviousSet(2, 85.0, 8, rpe = 8.0)))

        val vm = loaded()

        assertEquals("80 kg × 10", vm.row(0, 1).previous)
        assertEquals("85 kg × 8\nRPE 8", vm.row(0, 2).previous)
        assertEquals("85" to "8", vm.row(0, 2).weightPlaceholder to vm.row(0, 2).repsPlaceholder)
        assertNull(vm.row(0, 2).rpe)
    }

    private companion object {
        fun routineWithRests(vararg rests: Int) = Routine(
            id = "r1",
            name = "Torso",
            notes = null,
            archived = false,
            exercises = rests.mapIndexed { index, rest ->
                RoutineExercise("e${index + 1}", index, null, List(3) { PlannedSet(null, 8) }, rest)
            },
        )

        fun twoExercises() = sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1), pendingSet(2), pendingSet(3))),
            SessionExercise("e2", 1, null, listOf(pendingSet(1), pendingSet(2), pendingSet(3))),
        )
    }
}
