package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.model.WarmUpSet
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-33: the warm-up calculator and the disc loader of the live session and the insertion of warm-up sets. */
class CalculatorSheetViewModelTest {
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1), pendingSet(2), pendingSet(3))),
            SessionExercise("e2", 1, null, listOf(pendingSet(1), pendingSet(2))),
        ),
    )
    private val exercises = FakeExerciseRepository(
        listOf(
            exerciseRow("e1", "bench", equipment = Equipment.Barbell),
            exerciseRow("e2", "curl", equipment = Equipment.Dumbbell),
        ),
    )
    private val connectivity = FakeConnectivityObserver()
    private val clock = MutableClock()
    private val trainingPreferences = FakeTrainingPreferencesRepository()
    private val bodyMeasurements = FakeWeightRepository()
    private var created: LiveSessionViewModel? = null

    private fun newViewModel(savedState: SavedStateHandle = SavedStateHandle()) = LiveSessionViewModel(
        repository,
        exercises,
        FakeExerciseLibrary(emptyMap()),
        connectivity,
        clock,
        FakeHaptics(),
        FakeRoutineRepository(),
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

    private fun LiveSessionViewModel.active() = assertIs<LiveSessionUiState.Active>(uiState.value)

    private fun LiveSessionViewModel.calculator() = assertNotNull(active().calculator)

    private fun LiveSessionViewModel.sets(position: Int = 0) = active().exercises[position].sets

    private fun TestScope.send(vm: LiveSessionViewModel, event: LiveSessionEvent) {
        vm.onEvent(event)
        runCurrent()
    }

    private fun TestScope.type(vm: LiveSessionViewModel, position: Int, index: Int, weight: String, reps: String) {
        send(vm, LiveSessionEvent.WeightChanged(position, index, weight))
        send(vm, LiveSessionEvent.RepsChanged(position, index, reps))
    }

    private val fourSets = listOf(WarmUpSet(20.0, 10), WarmUpSet(40.0, 5), WarmUpSet(60.0, 3), WarmUpSet(80.0, 2))

    /** The set the server holds for each index after replaying the writes in the order it received them. */
    private fun serverSets(position: Int = 0) =
        repository.upserts.filter { it.exercisePosition == position }.associateBy { it.setIndex }.toSortedMap()

    // --- Opening ---

    @Test
    fun opening_prefillsTheWeightFromTheFirstWorkingSet() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "100", "5")

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        assertEquals("100", vm.calculator().draft.weightText)
        assertEquals(CalculatorKind.WarmUp, vm.calculator().draft.kind)
        assertEquals(listOf(WarmUpSet(60.0, 3)), vm.calculator().warmUp)
        send(vm, LiveSessionEvent.CalculatorWarmUpSetsChanged(4))
        assertEquals(fourSets, vm.calculator().warmUp)
    }

    @Test
    fun opening_withoutATypedWeightUsesThePlaceholder() = runSessionTest {
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 82.5, 5)))
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        assertEquals("82,5", vm.calculator().draft.weightText)
    }

    @Test
    fun opening_withNothingToPrefill_isEmptyWithAHint() = runSessionTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        assertEquals("", vm.calculator().draft.weightText)
        assertEquals(CalculatorHint.MissingWeight, vm.calculator().hint)
        assertFalse(vm.calculator().canAddWarmUp)
    }

    @Test
    fun opening_skipsLeadingWarmUpSetsWhenPrefilling() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "40", "5")
        send(vm, LiveSessionEvent.SetTypeChosen(0, 1, SetType.WarmUp))
        type(vm, 0, 2, "100", "5")

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        assertEquals("100", vm.calculator().draft.weightText)
    }

    @Test
    fun theWarmUpBarIsThePreferencesBar() = runSessionTest {
        trainingPreferences.result = Result.success(TrainingPreferences(barWeightKg = 15.0))
        val vm = loaded()
        type(vm, 0, 1, "100", "5")

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        assertEquals(15.0, vm.calculator().barKg)
    }

    @Test
    fun aDraftSavedWithAnOldBar_usesThePreferencesBar() = runSessionTest {
        trainingPreferences.result = Result.success(TrainingPreferences(barWeightKg = 15.0))
        val handle = SavedStateHandle()
        handle.persistCalculatorDraft(CalculatorDraft(0, CalculatorKind.WarmUp, "100"))
        handle["live_calc_bar"] = 7.5

        val vm = loaded(handle)

        assertEquals(15.0, vm.calculator().barKg)
    }

    @Test
    fun theWarmUpOpensWithTheBestPastWeight() = runSessionTest {
        repository.sessionRecords = bestWeight(110.0)
        val vm = loaded()
        type(vm, 0, 1, "100", "5")

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        assertEquals("110", vm.calculator().draft.weightText)
        assertFalse(vm.calculator().draft.weightEdited)
    }

    @Test
    fun theWarmUpFallsBackToThePlannedWeight_andWithNothingShowsTheHint() = runSessionTest {
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 82.5, 5)))
        val vm = loaded()
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        assertEquals("82,5", vm.calculator().draft.weightText)

        repository.previous = emptyMap()
        val empty = loaded()
        send(empty, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        assertEquals(CalculatorHint.MissingWeight, empty.calculator().hint)
    }

    @Test
    fun aBestWeightArrivingAfterTheUserTyped_doesNotOverwriteIt() = runSessionTest {
        repository.sessionRecords = bestWeight(110.0)
        val vm = loaded()
        vm.onEvent(LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        vm.onEvent(LiveSessionEvent.CalculatorWeightChanged("90"))

        runCurrent()

        assertEquals("90", vm.calculator().draft.weightText)
        assertTrue(vm.calculator().draft.weightEdited)
    }

    @Test
    fun theWarmUpOfADumbbellUsesTheHandle() = runSessionTest {
        trainingPreferences.result = Result.success(TrainingPreferences(dumbbellHandleKg = 1.5))
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenCalculator(1, CalculatorKind.WarmUp))

        assertEquals(1.5, vm.calculator().barKg)
    }

    private fun bestWeight(kg: Double) =
        mapOf("e1" to listOf(ExerciseSessionRecords("old", SESSION_START, heaviestLift = kg)))

    @Test
    fun editingTheWeight_updatesTheResultLive() = runSessionTest {
        val vm = loaded()
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        send(vm, LiveSessionEvent.CalculatorWarmUpSetsChanged(4))

        send(vm, LiveSessionEvent.CalculatorWeightChanged("100"))

        assertEquals(fourSets, vm.calculator().warmUp)
        send(vm, LiveSessionEvent.CalculatorWeightChanged("20"))
        assertEquals(CalculatorHint.AtOrBelowBar, vm.calculator().hint)
        assertTrue(vm.calculator().warmUp.isEmpty())
    }

    // --- Derived rounding (ADR-56) ---

    @Test
    fun theRoundingIsTwiceTheSmallestEnabledPlate_forBarbellsAndDumbbells() = runSessionTest {
        trainingPreferences.result =
            Result.success(TrainingPreferences(plateKg = listOf(25.0, 5.0, 2.5)))
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        send(vm, LiveSessionEvent.CalculatorWarmUpSetsChanged(4))
        send(vm, LiveSessionEvent.CalculatorWeightChanged("90"))
        assertEquals(5.0, vm.calculator().roundingKg)
        assertTrue(vm.calculator().warmUp.all { it.weightKg % 5.0 == 0.0 })

        send(vm, LiveSessionEvent.OpenCalculator(1, CalculatorKind.WarmUp))
        assertEquals(5.0, vm.calculator().roundingKg)
    }

    @Test
    fun withTheDefaultPlates_theRoundingIsOneKg() = runSessionTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.Discs))

        assertEquals(1.0, vm.calculator().roundingKg)
    }

    // --- Dumbbell handle (ADR-55) ---

    @Test
    fun dumbbellDiscLoaderOpensWithTheHandle_barbellWithTheBar() = runSessionTest {
        trainingPreferences.result = Result.success(TrainingPreferences(barWeightKg = 15.0, dumbbellHandleKg = 1.5))
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.Discs))
        assertEquals(15.0, vm.calculator().barKg)
        send(vm, LiveSessionEvent.OpenCalculator(1, CalculatorKind.Discs))
        assertEquals(1.5, vm.calculator().barKg)
    }

    @Test
    fun dumbbellWarmUp_neverGoesBelowTheHandle() = runSessionTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenCalculator(1, CalculatorKind.WarmUp))
        send(vm, LiveSessionEvent.CalculatorWarmUpSetsChanged(4))
        send(vm, LiveSessionEvent.CalculatorWeightChanged("20"))

        assertEquals(listOf(1.0, 8.0, 12.0, 16.0), vm.calculator().warmUp.map { it.weightKg })
    }

    // --- Disc loader (ADR-56) ---

    @Test
    fun theDiscLoaderStartsEmpty_withTheBarAsTotal() = runSessionTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.Discs))

        assertTrue(vm.calculator().draft.discs.isEmpty())
        assertEquals(20.0, vm.calculator().totalKg)
        assertEquals("", vm.calculator().draft.weightText)
    }

    @Test
    fun discCounts_changeTheTotal_twoSidesAtATime() = runSessionTest {
        val vm = loaded()
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.Discs))

        send(vm, LiveSessionEvent.DiscCountChanged(25.0, 1))
        send(vm, LiveSessionEvent.DiscCountChanged(15.0, 1))

        assertEquals(100.0, vm.calculator().totalKg)
        send(vm, LiveSessionEvent.DiscCountChanged(25.0, 0))
        assertEquals(50.0, vm.calculator().totalKg)
        assertEquals(mapOf(15.0 to 1), vm.calculator().draft.discs)
    }

    @Test
    fun aDumbbellLoaderAddsDiscsToTheHandle() = runSessionTest {
        val vm = loaded()
        send(vm, LiveSessionEvent.OpenCalculator(1, CalculatorKind.Discs))

        send(vm, LiveSessionEvent.DiscCountChanged(10.0, 1))

        assertEquals(21.0, vm.calculator().totalKg)
    }

    @Test
    fun discCounts_areCappedAtTenAndNeverNegative() = runSessionTest {
        val vm = loaded()
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.Discs))

        send(vm, LiveSessionEvent.DiscCountChanged(25.0, 99))
        assertEquals(10, vm.calculator().draft.discs[25.0])
        send(vm, LiveSessionEvent.DiscCountChanged(25.0, -3))
        assertTrue(vm.calculator().draft.discs.isEmpty())
    }

    @Test
    fun aDiscOfADisabledPlate_isIgnored_andDroppedWhenThePlateIsDisabled() = runSessionTest {
        trainingPreferences.result = Result.success(TrainingPreferences(plateKg = listOf(20.0, 5.0)))
        val vm = loaded()
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.Discs))

        send(vm, LiveSessionEvent.DiscCountChanged(25.0, 1))
        assertTrue(vm.calculator().draft.discs.isEmpty())
        assertEquals(listOf(20.0, 5.0), vm.calculator().availableDiscs)

        val handle = SavedStateHandle()
        handle.persistCalculatorDraft(CalculatorDraft(0, CalculatorKind.Discs, discs = mapOf(25.0 to 1, 5.0 to 2)))
        val restored = loaded(handle)
        assertEquals(mapOf(5.0 to 2), restored.calculator().draft.discs)
        assertEquals(40.0, restored.calculator().totalKg)
    }

    @Test
    fun discCountsAreIgnoredWhileTheWarmUpIsOpen() = runSessionTest {
        val vm = loaded()
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        send(vm, LiveSessionEvent.DiscCountChanged(25.0, 1))

        assertTrue(vm.calculator().draft.discs.isEmpty())
    }

    @Test
    fun aDraftSavedBeforeTheDiscLoader_restoresAsAnEmptyLoader() = runSessionTest {
        val saved = SavedStateHandle()
        saved["live_calc_position"] = 1
        saved["live_calc_kind"] = "Plates"
        saved["live_calc_weight"] = "21"
        saved["live_calc_bar"] = 20.0

        val vm = loaded(saved)

        assertEquals(CalculatorKind.Discs, vm.calculator().draft.kind)
        assertTrue(vm.calculator().draft.discs.isEmpty())
        assertEquals(1.0, vm.calculator().barKg)
    }

    @Test
    fun aDraftWithAnUnknownKind_isNotRestored() = runSessionTest {
        val saved = SavedStateHandle()
        saved["live_calc_position"] = 0
        saved["live_calc_kind"] = "Unknown"

        assertNull(loaded(saved).active().calculator)
    }

    // --- Inserting warm-up sets ---

    @Test
    fun adding_insertsWarmUpSetsBeforeTheWorkingSetsAndReindexes() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "100", "5")
        type(vm, 0, 2, "100", "4")
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        send(vm, LiveSessionEvent.AddWarmUpSets(0, fourSets))

        val rows = vm.sets()
        assertEquals((1..7).toList(), rows.map { it.setIndex })
        assertEquals(List(4) { SetType.WarmUp } + List(3) { SetType.Working }, rows.map { it.type })
        assertEquals(listOf("20", "40", "60", "80", "100", "100", ""), rows.map { it.weight })
        assertEquals(listOf("10", "5", "3", "2", "5", "4", ""), rows.map { it.reps })
        assertTrue(rows.none { it.completed })
        assertNull(vm.active().calculator, "the calculator closes after inserting")
    }

    @Test
    fun adding_writesEveryMovedOrNewSetExactlyOnce_underItsFinalIndex() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "100", "5")
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        send(vm, LiveSessionEvent.AddWarmUpSets(0, fourSets))
        runCurrent()

        assertEquals(
            listOf(7, 6, 5, 4, 3, 2, 1).map { "upsert:0:$it" },
            repository.serverLog.filter { it.startsWith("upsert:0") },
        )
        val server = serverSets()
        assertEquals(List(4) { SetType.WarmUp } + List(3) { SetType.Working }, server.values.map { it.setType })
        assertEquals(100.0, (server.getValue(5).entry as SetEntry.WeightReps).weightKg)
        // A moved working set that was blank and never on the server is written as the empty entry
        // (weight 0, reps 0 are accepted by workout_sets: both columns are NOT NULL DEFAULT 0, CHECK >= 0).
        val blank = server.getValue(7)
        assertEquals(SetEntry.WeightReps(0.0, 0), blank.entry)
        assertNull(blank.completedAt)
    }

    @Test
    fun adding_doesNotWriteTheSetsBeforeTheInsertionPoint() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "40", "5")
        send(vm, LiveSessionEvent.SetTypeChosen(0, 1, SetType.WarmUp))
        runCurrent()
        repository.serverLog.clear()
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        send(vm, LiveSessionEvent.CalculatorWeightChanged("100"))

        send(vm, LiveSessionEvent.AddWarmUpSets(0, listOf(WarmUpSet(60.0, 3), WarmUpSet(80.0, 2))))
        runCurrent()

        // The existing warm-up stays first; the new ones follow it; the working sets move from index 2 on.
        assertEquals(
            listOf(SetType.WarmUp, SetType.WarmUp, SetType.WarmUp, SetType.Working, SetType.Working),
            vm.sets().map { it.type },
        )
        assertEquals(listOf("40", "60", "80", "", ""), vm.sets().map { it.weight })
        assertEquals(
            listOf(5, 4, 3, 2).map { "upsert:0:$it" },
            repository.serverLog,
            "set 1 is untouched",
        )
    }

    @Test
    fun adding_keepsCompletedSetsCompleted() = runSessionTest {
        val vm = loaded()
        type(vm, 0, 1, "100", "5")
        send(vm, LiveSessionEvent.SetChecked(0, 1, true))
        send(vm, LiveSessionEvent.SetRpeChosen(0, 1, 8.0)) // Answers the RPE prompt (ADR-55).
        runCurrent()
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        send(vm, LiveSessionEvent.CalculatorWeightChanged("100"))

        send(vm, LiveSessionEvent.AddWarmUpSets(0, fourSets))
        runCurrent()

        val moved = vm.sets()[4]
        assertTrue(moved.completed)
        assertEquals("100", moved.weight)
        assertEquals(5, moved.setIndex)
        assertNotNull(serverSets().getValue(5).completedAt)
        assertTrue(serverSets().filterKeys { it <= 4 }.values.all { it.completedAt == null })
    }

    @Test
    fun adding_leavesOtherExercisesAlone() = runSessionTest {
        val vm = loaded()
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        send(vm, LiveSessionEvent.CalculatorWeightChanged("100"))

        send(vm, LiveSessionEvent.AddWarmUpSets(0, fourSets))
        runCurrent()

        assertEquals(listOf(1, 2), vm.sets(1).map { it.setIndex })
        assertTrue(repository.upserts.none { it.exercisePosition == 1 })
    }

    @Test
    fun adding_movesAnOpenTypePickerWithItsSet() = runSessionTest {
        val vm = loaded()
        send(vm, LiveSessionEvent.OpenTypePicker(0, 2))
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        send(vm, LiveSessionEvent.CalculatorWeightChanged("100"))

        send(vm, LiveSessionEvent.AddWarmUpSets(0, fourSets))

        assertEquals(TypePickerTarget(0, 6), vm.active().typePicker)
    }

    @Test
    fun adding_withNoSets_doesNothing() = runSessionTest {
        val vm = loaded()

        send(vm, LiveSessionEvent.AddWarmUpSets(0, emptyList()))
        runCurrent()

        assertEquals(3, vm.sets().size)
        assertTrue(repository.upserts.isEmpty())
    }

    @Test
    fun dismissing_insertsNothing() = runSessionTest {
        val vm = loaded()
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        send(vm, LiveSessionEvent.CalculatorWeightChanged("100"))

        send(vm, LiveSessionEvent.DismissCalculator)
        runCurrent()

        assertNull(vm.active().calculator)
        assertEquals(3, vm.sets().size)
        assertTrue(repository.upserts.isEmpty())
    }

    // --- Offline ---

    @Test
    fun offline_theInsertionIsQueuedAndSentOnReconnect() = runSessionTest {
        connectivity.state.value = false
        val vm = loaded()
        type(vm, 0, 1, "100", "5")
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        send(vm, LiveSessionEvent.AddWarmUpSets(0, fourSets))

        assertTrue(repository.upserts.isEmpty())
        assertTrue(vm.sets().all { it.pendingSave }, "every moved or new set is marked unsaved")
        assertEquals(7, vm.sets().size)

        connectivity.state.value = true
        runCurrent()

        assertEquals(7, repository.upserts.size)
        assertEquals(List(4) { SetType.WarmUp } + List(3) { SetType.Working }, serverSets().values.map { it.setType })
        assertTrue(vm.sets().none { it.pendingSave })
    }

    @Test
    fun offline_aStaleQueuedWriteOfAMovedSetIsReplacedByTheNewOne() = runSessionTest {
        connectivity.state.value = false
        val vm = loaded()
        type(vm, 0, 1, "100", "5")
        send(vm, LiveSessionEvent.SetChecked(0, 1, true))
        send(vm, LiveSessionEvent.SetRpeChosen(0, 1, 8.0)) // Completes the set, queued under index 1.
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        send(vm, LiveSessionEvent.AddWarmUpSets(0, fourSets))
        connectivity.state.value = true
        runCurrent()

        val atOne = serverSets().getValue(1)
        assertEquals(SetType.WarmUp, atOne.setType)
        assertNull(atOne.completedAt, "index 1 now holds the new warm-up, not the old completed set")
        assertNotNull(serverSets().getValue(5).completedAt)
    }

    @Test
    fun offline_aPendingDeletionIsSentBeforeTheInsertionWrites() = runSessionTest {
        connectivity.state.value = false
        val vm = loaded()
        send(vm, LiveSessionEvent.DeleteSet(0, 3))
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        send(vm, LiveSessionEvent.CalculatorWeightChanged("100"))

        send(vm, LiveSessionEvent.AddWarmUpSets(0, fourSets))
        connectivity.state.value = true
        runCurrent()

        assertEquals("delete:0:3", repository.serverLog.first())
        assertEquals(6, vm.sets().size)
        assertEquals(6, repository.serverLog.count { it.startsWith("upsert:0") })
    }

    // --- Restoration (ADR-40) ---

    @Test
    fun theOpenCalculatorSurvivesARecreatedViewModel() = runSessionTest {
        val handle = SavedStateHandle()
        val first = loaded(handle)
        send(first, LiveSessionEvent.OpenCalculator(0, CalculatorKind.Discs))
        send(first, LiveSessionEvent.DiscCountChanged(25.0, 2))
        send(first, LiveSessionEvent.DiscCountChanged(1.25, 1))

        val second = loaded(handle)

        assertEquals(
            CalculatorDraft(0, CalculatorKind.Discs, discs = mapOf(25.0 to 2, 1.25 to 1)),
            second.calculator().draft,
        )
        assertEquals(122.5, second.calculator().totalKg)
    }

    @Test
    fun theWarmUpSetCountDefaultsToOne_changesTheResult_andSurvivesARecreatedViewModel() = runSessionTest {
        val handle = SavedStateHandle()
        val first = loaded(handle)
        type(first, 0, 1, "100", "5")
        send(first, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        assertEquals(1, first.calculator().draft.warmUpSets)
        assertEquals(listOf(60.0), first.calculator().warmUp.map { it.weightKg })

        send(first, LiveSessionEvent.CalculatorWarmUpSetsChanged(2))
        assertEquals(listOf(50.0, 80.0), first.calculator().warmUp.map { it.weightKg })
        send(first, LiveSessionEvent.CalculatorWarmUpSetsChanged(9))
        assertEquals(2, first.calculator().draft.warmUpSets, "a count outside 1..4 is ignored")

        val second = loaded(handle)
        assertEquals(2, second.calculator().draft.warmUpSets)

        send(second, LiveSessionEvent.DismissCalculator)
        send(second, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        assertEquals(1, second.calculator().draft.warmUpSets, "not persisted: the next use starts at 1")
    }

    @Test
    fun aClosedCalculatorIsNotRestored() = runSessionTest {
        val handle = SavedStateHandle()
        val first = loaded(handle)
        send(first, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        send(first, LiveSessionEvent.DismissCalculator)

        val second = loaded(handle)

        assertNull(second.active().calculator)
    }
}
