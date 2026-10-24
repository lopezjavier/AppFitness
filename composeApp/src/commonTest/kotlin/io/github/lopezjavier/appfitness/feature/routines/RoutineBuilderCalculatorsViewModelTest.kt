package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeExerciseOptionsRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.model.WarmUpSet
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.CalculatorHint
import io.github.lopezjavier.appfitness.feature.workout.CalculatorKind
import io.github.lopezjavier.appfitness.feature.workout.DoubleDumbbellDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-097f: calculators and double dumbbell in the routine builder (ADR-55). */
@Suppress("TooManyFunctions") // One method per scenario.
class RoutineBuilderCalculatorsViewModelTest {
    private val exercises = FakeExerciseRepository(
        listOf(
            exerciseRow("bar", "bench", equipment = Equipment.Barbell),
            exerciseRow("db", "curl", equipment = Equipment.Dumbbell),
        ),
    )
    private val preferences = FakeTrainingPreferencesRepository()
    private val options = FakeExerciseOptionsRepository()
    private val connectivity = FakeConnectivityObserver()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.builder(handle: SavedStateHandle = SavedStateHandle()): RoutineBuilderViewModel {
        val vm = RoutineBuilderViewModel(
            null,
            handle,
            FakeRoutineRepository(),
            LoadExerciseCatalog(
                exercises,
                FakeExerciseLibrary(
                    mapOf(
                        "bench" to exerciseInfo("bench", "Press"),
                        "curl" to exerciseInfo("curl", "Curl"),
                    ),
                ),
            ),
            connectivity,
            preferences,
            options,
        )
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.withRows(vararg ids: String, handle: SavedStateHandle = SavedStateHandle()) =
        builder(handle).also { vm ->
            ids.forEach { vm.onEvent(RoutineBuilderEvent.AddExercise(it)) }
            advanceUntilIdle()
        }

    private fun RoutineBuilderViewModel.form() = assertIs<RoutineBuilderUiState.Editing>(uiState.value)

    private fun TestScope.send(vm: RoutineBuilderViewModel, event: RoutineBuilderEvent) {
        vm.onEvent(event)
        advanceUntilIdle()
    }

    @Test
    fun rowsExposeTheirEquipment_andDoubleDumbbellState() = runTest {
        options.ids = setOf("db")
        val vm = withRows("bar", "db")

        assertEquals(listOf(Equipment.Barbell, Equipment.Dumbbell), vm.form().rows.map { it.equipment })
        assertEquals(listOf(false, true), vm.form().rows.map { it.isDoubleDumbbell })
    }

    @Test
    fun warmUpCalculator_isPrefilledWithTheFirstWorkingSetWeight() = runTest {
        val vm = withRows("bar")
        send(vm, RoutineBuilderEvent.SetWeight(0, 0, "100"))

        send(vm, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        val calculator = assertNotNull(vm.form().calculator)
        assertEquals("100", calculator.draft.weightText)
        assertEquals(20.0, calculator.barKg)
        assertEquals(listOf(WarmUpSet(60.0, 3)), calculator.warmUp)
    }

    @Test
    fun theFirstWorkingSetIsTheReference_andNoWeightLeavesItEmpty() = runTest {
        val vm = withRows("bar")
        send(vm, RoutineBuilderEvent.ChangeSetType(0, 0, SetType.WarmUp))
        send(vm, RoutineBuilderEvent.SetWeight(0, 0, "30"))
        send(vm, RoutineBuilderEvent.SetWeight(0, 1, "100"))

        send(vm, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        assertEquals("100", vm.form().calculator?.draft?.weightText)

        send(vm, RoutineBuilderEvent.DismissCalculator)
        send(vm, RoutineBuilderEvent.SetWeight(0, 1, ""))
        send(vm, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        assertEquals("", vm.form().calculator?.draft?.weightText)
        assertEquals(CalculatorHint.MissingWeight, vm.form().calculator?.hint)
    }

    @Test
    fun discLoader_startsEmpty_andTwentyFiveAndFifteenMakeHundredKilos() = runTest {
        val vm = withRows("bar")

        send(vm, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.Discs))
        assertEquals(20.0, vm.form().calculator?.totalKg)
        assertEquals(emptyMap(), vm.form().calculator?.draft?.discs)

        send(vm, RoutineBuilderEvent.DiscCountChanged(25.0, 1))
        send(vm, RoutineBuilderEvent.DiscCountChanged(15.0, 1))
        assertEquals(100.0, vm.form().calculator?.totalKg)
    }

    @Test
    fun barbellUsesThePreferencesBar_dumbbellUsesTheHandle() = runTest {
        preferences.result =
            Result.success(TrainingPreferences(barWeightKg = 15.0, dumbbellHandleKg = 1.5))
        val vm = withRows("bar", "db")

        send(vm, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.Discs))
        assertEquals(15.0, vm.form().calculator?.barKg)

        send(vm, RoutineBuilderEvent.OpenCalculator(1, CalculatorKind.Discs))
        assertEquals(1.5, vm.form().calculator?.barKg)
    }

    @Test
    fun dumbbellWithATenKiloDisc_totalsTwentyOneKilos() = runTest {
        val vm = withRows("db")
        send(vm, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.Discs))

        send(vm, RoutineBuilderEvent.DiscCountChanged(10.0, 1))

        assertEquals(21.0, vm.form().calculator?.totalKg)
    }

    @Test
    fun discCounts_areCapped_andDisabledPlatesIgnored() = runTest {
        preferences.result = Result.success(TrainingPreferences(plateKg = listOf(20.0, 5.0)))
        val vm = withRows("bar")
        send(vm, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.Discs))

        send(vm, RoutineBuilderEvent.DiscCountChanged(20.0, 50))
        send(vm, RoutineBuilderEvent.DiscCountChanged(25.0, 1))

        assertEquals(mapOf(20.0 to 10), vm.form().calculator?.draft?.discs)
        assertEquals(listOf(20.0, 5.0), vm.form().calculator?.availableDiscs)
    }

    @Test
    fun discCounts_surviveARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val first = withRows("bar", handle = handle)
        send(first, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.Discs))
        send(first, RoutineBuilderEvent.DiscCountChanged(25.0, 2))

        val second = builder(handle)

        assertEquals(mapOf(25.0 to 2), assertNotNull(second.form().calculator).draft.discs)
    }

    @Test
    fun aFailedPreferencesRead_fallsBackToTheDefaults_andNeverBlocks() = runTest {
        preferences.result = Result.failure(IllegalStateException("offline"))
        val vm = withRows("db")

        send(vm, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.Discs))

        assertEquals(1.0, vm.form().calculator?.barKg)
    }

    @Test
    fun addingWarmUps_editsTheDraft_closesTheSheet_andMarksItDirty() = runTest {
        val vm = withRows("bar")
        send(vm, RoutineBuilderEvent.SetWeight(0, 0, "100"))
        send(vm, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        val before = vm.form().rows[0].plannedSets.size

        send(vm, RoutineBuilderEvent.AddWarmUpSets(0, listOf(WarmUpSet(60.0, 3))))

        assertNull(vm.form().calculator)
        val sets = vm.form().rows[0].plannedSets
        assertEquals(before + 1, sets.size)
        assertEquals(SetType.WarmUp, sets.first().type)
        assertEquals("60", sets.first().weight)
        assertTrue(vm.form().isDirty)
    }

    @Test
    fun warmUps_thatExceedTheCap_areBlockedAndFlagged() = runTest {
        val vm = withRows("bar")
        repeat(RoutineBounds.SETS_MAX) { send(vm, RoutineBuilderEvent.AddSet(0)) }
        send(vm, RoutineBuilderEvent.SetWeight(0, 0, "100"))
        send(vm, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.WarmUp))

        assertTrue(vm.form().warmUpBlockedByCap)
        val before = vm.form().rows[0].plannedSets.size
        send(vm, RoutineBuilderEvent.AddWarmUpSets(0, listOf(WarmUpSet(60.0, 3))))

        assertEquals(before, vm.form().rows[0].plannedSets.size)
    }

    @Test
    fun dismissingTheCalculator_closesItWithoutTouchingTheRoutine() = runTest {
        val vm = withRows("bar")
        val before = vm.form().rows
        send(vm, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.Discs))

        send(vm, RoutineBuilderEvent.DismissCalculator)

        assertNull(vm.form().calculator)
        assertEquals(before, vm.form().rows)
    }

    @Test
    fun calculatorInputs_surviveARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val first = withRows("bar", handle = handle)
        send(first, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.WarmUp))
        send(first, RoutineBuilderEvent.CalculatorWeightChanged("90"))
        send(first, RoutineBuilderEvent.CalculatorWarmUpSetsChanged(3))

        val second = builder(handle)

        val draft = assertNotNull(second.form().calculator).draft
        assertEquals("90", draft.weightText)
        assertEquals(3, draft.warmUpSets)
        assertEquals(CalculatorKind.WarmUp, draft.kind)
    }

    @Test
    fun calculatorInputs_neverMakeTheRoutineDirty() = runTest {
        val handle = SavedStateHandle()
        val vm = builder(handle)

        vm.onEvent(RoutineBuilderEvent.AddExercise("bar"))
        advanceUntilIdle()
        val before = vm.form().isDirty
        send(vm, RoutineBuilderEvent.OpenCalculator(0, CalculatorKind.Discs))
        send(vm, RoutineBuilderEvent.DiscCountChanged(25.0, 1))

        assertEquals(before, vm.form().isDirty)
    }

    @Test
    fun doubleDumbbell_opensWithTheCurrentValue_andSavesThenCloses() = runTest {
        val vm = withRows("db")
        send(vm, RoutineBuilderEvent.OpenDoubleDumbbell(0))
        assertEquals(DoubleDumbbellDraft("db", false), vm.form().doubleDumbbellDraft)

        send(vm, RoutineBuilderEvent.DoubleDumbbellDraftChanged("db", true))
        assertTrue(options.writes.isEmpty(), "nothing is written before Guardar")
        send(vm, RoutineBuilderEvent.SetDoubleDumbbell("db", true))

        assertEquals(listOf("db" to true), options.writes)
        assertNull(vm.form().doubleDumbbellDraft)
        assertTrue(vm.form().rows[0].isDoubleDumbbell)
    }

    @Test
    fun doubleDumbbell_failureKeepsTheSheetOpenWithAnError_andRetrySucceeds() = runTest {
        val vm = withRows("db")
        send(vm, RoutineBuilderEvent.OpenDoubleDumbbell(0))
        send(vm, RoutineBuilderEvent.DoubleDumbbellDraftChanged("db", true))
        options.failWrite = true

        send(vm, RoutineBuilderEvent.SetDoubleDumbbell("db", true))

        assertNotNull(vm.form().doubleDumbbellDraft)
        assertTrue(vm.form().doubleDumbbellError)
        assertFalse(vm.form().rows[0].isDoubleDumbbell)

        options.failWrite = false
        send(vm, RoutineBuilderEvent.SetDoubleDumbbell("db", true))
        assertNull(vm.form().doubleDumbbellDraft)
        assertFalse(vm.form().doubleDumbbellError)
        assertTrue(vm.form().rows[0].isDoubleDumbbell)
    }

    @Test
    fun doubleDumbbell_offlineWritesNothing_andKeepsTheSheet() = runTest {
        val vm = withRows("db")
        send(vm, RoutineBuilderEvent.OpenDoubleDumbbell(0))
        connectivity.state.value = false
        advanceUntilIdle()

        send(vm, RoutineBuilderEvent.SetDoubleDumbbell("db", true))

        assertTrue(options.writes.isEmpty())
        assertNotNull(vm.form().doubleDumbbellDraft)
    }

    @Test
    fun doubleDumbbell_isOnlyOpenedForDumbbells_andTheDraftSurvivesRecreation() = runTest {
        val handle = SavedStateHandle()
        val first = withRows("bar", "db", handle = handle)
        send(first, RoutineBuilderEvent.OpenDoubleDumbbell(0))
        assertNull(first.form().doubleDumbbellDraft)

        send(first, RoutineBuilderEvent.OpenDoubleDumbbell(1))
        send(first, RoutineBuilderEvent.DoubleDumbbellDraftChanged("db", true))
        val second = builder(handle)

        assertEquals(DoubleDumbbellDraft("db", true), second.form().doubleDumbbellDraft)
    }

    @Test
    fun dismissingTheOptionSheets_discardsTheDraft() = runTest {
        val vm = withRows("db")
        send(vm, RoutineBuilderEvent.OpenDoubleDumbbell(0))
        send(vm, RoutineBuilderEvent.DoubleDumbbellDraftChanged("db", true))

        send(vm, RoutineBuilderEvent.DismissOptionSheets)

        assertNull(vm.form().doubleDumbbellDraft)
        assertTrue(options.writes.isEmpty())
    }
}
