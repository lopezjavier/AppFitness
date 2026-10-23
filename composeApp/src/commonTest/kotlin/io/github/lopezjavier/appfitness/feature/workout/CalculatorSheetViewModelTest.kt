package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeExerciseOptionsRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.model.Equipment
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

/** US-33: the warm-up and plate calculators of the live session and the insertion of warm-up sets. */
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
        FakeExerciseOptionsRepository(),
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
        assertEquals(fourSets, vm.calculator().warmUp)
    }

    @Test
    fun opening_withoutATypedWeightUsesThePlaceholder() = runSessionTest {
        repository.previous = mapOf("e1" to listOf(PreviousSet(1, 82.5, 5)))
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.Plates))

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
    fun theBarStartsAtTheSavedDefault_andChangingItIsPerUse() = runSessionTest {
        trainingPreferences.result = Result.success(TrainingPreferences(barWeightKg = 15.0))
        val vm = loaded()
        type(vm, 0, 1, "100", "5")
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        assertEquals(15.0, vm.calculator().draft.barKg)

        send(vm, LiveSessionEvent.CalculatorBarChanged(7.5))
        assertEquals(7.5, vm.calculator().draft.barKg)
        assertEquals(WarmUpSet(7.5, 10), vm.calculator().warmUp.first())

        send(vm, LiveSessionEvent.CalculatorBarChanged(12.0))
        assertEquals(7.5, vm.calculator().draft.barKg, "a bar outside the catalogue is ignored")

        send(vm, LiveSessionEvent.DismissCalculator)
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        assertEquals(15.0, vm.calculator().draft.barKg, "the next use starts from the saved default again")
    }

    @Test
    fun editingTheWeight_updatesTheResultLive() = runSessionTest {
        val vm = loaded()
        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        send(vm, LiveSessionEvent.CalculatorWeightChanged("100"))

        assertEquals(fourSets, vm.calculator().warmUp)
        send(vm, LiveSessionEvent.CalculatorWeightChanged("20"))
        assertEquals(CalculatorHint.AtOrBelowBar, vm.calculator().hint)
        assertTrue(vm.calculator().warmUp.isEmpty())
    }

    // --- Rounding by equipment ---

    @Test
    fun barbellExercisesUseThePlateRounding_dumbbellExercisesTheDumbbellRounding() = runSessionTest {
        trainingPreferences.result =
            Result.success(TrainingPreferences(plateRoundingKg = 2.5, dumbbellRoundingKg = 5.0))
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        send(vm, LiveSessionEvent.CalculatorWeightChanged("90"))
        assertEquals(2.5, vm.calculator().roundingKg)
        assertEquals(listOf(20.0, 35.0, 55.0, 72.5), vm.calculator().warmUp.map { it.weightKg })

        send(vm, LiveSessionEvent.OpenCalculator(1, CalculatorKind.WarmUp))
        send(vm, LiveSessionEvent.CalculatorWeightChanged("90"))
        assertEquals(5.0, vm.calculator().roundingKg)
        assertEquals(listOf(20.0, 35.0, 55.0, 70.0), vm.calculator().warmUp.map { it.weightKg })
    }

    // --- Plates ---

    @Test
    fun theSavedPlatesDecideThePlateResult() = runSessionTest {
        trainingPreferences.result = Result.success(
            TrainingPreferences(plateKg = listOf(20.0, 10.0, 5.0, 2.5, 1.25, 0.5)),
        )
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.Plates))
        send(vm, LiveSessionEvent.CalculatorWeightChanged("100"))

        val result = assertNotNull(vm.calculator().plates)
        assertEquals(listOf(20.0, 20.0), result.platesPerSide)
        assertTrue(result.exact)
    }

    @Test
    fun anImpossibleTarget_reportsTheClosestLowerWeight() = runSessionTest {
        trainingPreferences.result = Result.success(TrainingPreferences(plateKg = listOf(25.0, 15.0, 5.0, 2.5)))
        val vm = loaded()

        send(vm, LiveSessionEvent.OpenCalculator(0, CalculatorKind.Plates))
        send(vm, LiveSessionEvent.CalculatorWeightChanged("101"))

        val result = assertNotNull(vm.calculator().plates)
        assertEquals(100.0, result.achievedKg)
        assertFalse(result.exact)
        assertTrue(vm.calculator().warmUp.isEmpty())
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
        send(vm, LiveSessionEvent.SetChecked(0, 1, true)) // Queued under index 1.
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
        send(first, LiveSessionEvent.OpenCalculator(0, CalculatorKind.Plates))
        send(first, LiveSessionEvent.CalculatorWeightChanged("102,5"))
        send(first, LiveSessionEvent.CalculatorBarChanged(10.0))

        val second = loaded(handle)

        assertEquals(CalculatorDraft(0, CalculatorKind.Plates, "102,5", 10.0), second.calculator().draft)
        assertEquals(listOf(25.0, 20.0, 1.25), assertNotNull(second.calculator().plates).platesPerSide.take(3))
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
