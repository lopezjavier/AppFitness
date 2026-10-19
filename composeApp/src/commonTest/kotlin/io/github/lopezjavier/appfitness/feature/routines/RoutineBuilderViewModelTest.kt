package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("TooManyFunctions") // One method per Gherkin scenario or rule.
class RoutineBuilderViewModelTest {
    private val exercises =
        FakeExerciseRepository(
            listOf(
                exerciseRow("a", "bench"),
                exerciseRow("b", "row"),
                exerciseRow("c", "squat"),
                exerciseRow("custom", null, name = "Mi ejercicio", custom = true),
            ),
        )
    private val library =
        FakeExerciseLibrary(
            mapOf(
                "bench" to exerciseInfo("bench", "Press de banca"),
                "row" to exerciseInfo("row", "Remo con barra"),
                "squat" to exerciseInfo("squat", "Sentadilla"),
            ),
        )
    private val repository = FakeRoutineRepository()
    private val connectivity = FakeConnectivityObserver()
    private val handle = SavedStateHandle()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.builder(
        routineId: String? = null,
        savedState: SavedStateHandle = handle,
    ): RoutineBuilderViewModel {
        val vm =
            RoutineBuilderViewModel(
                routineId,
                savedState,
                repository,
                LoadExerciseCatalog(exercises, library),
                connectivity,
            )
        advanceUntilIdle()
        return vm
    }

    private fun RoutineBuilderViewModel.form() = assertIs<RoutineBuilderUiState.Editing>(uiState.value)

    private fun RoutineBuilderViewModel.order() = form().rows.map { it.exerciseId }

    private fun RoutineBuilderViewModel.add(vararg ids: String) = ids.forEach {
        onEvent(RoutineBuilderEvent.AddExercise(it))
    }

    @Test
    fun creatingARoutine_savesNameAndExercisesWithDefaultTargets_thenCloses() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.SetName("Torso fuerza"))
        vm.add("a", "b")
        assertEquals(listOf("Press de banca", "Remo con barra"), vm.form().rows.map { it.name })
        assertTrue(vm.form().canSave)

        vm.effects.test {
            vm.onEvent(RoutineBuilderEvent.Save)
            advanceUntilIdle()

            assertEquals(RoutineBuilderEffect.Close, awaitItem())
        }

        val saved = repository.saved.single()
        assertNull(saved.id)
        assertEquals("Torso fuerza", saved.name)
        assertEquals(
            listOf(
                RoutineExercise("a", 0, null, targetSets = 3, targetReps = 8, restSeconds = 120),
                RoutineExercise("b", 1, null, targetSets = 3, targetReps = 8, restSeconds = 120),
            ),
            saved.exercises,
        )
    }

    @Test
    fun theSavedName_isTrimmed() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.SetName("  Torso  "))
        vm.add("a")

        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()

        assertEquals("Torso", repository.saved.single().name)
    }

    @Test
    fun customExercises_showTheirOwnName() = runTest {
        val vm = builder()

        vm.add("custom")

        assertEquals("Mi ejercicio", vm.form().rows.single().name)
    }

    @Test
    fun reordering_byMoveTo_andByMoveUpAndDown_giveTheSameOrder() = runTest {
        val byMoveTo = builder()
        byMoveTo.add("a", "b", "c")
        byMoveTo.onEvent(RoutineBuilderEvent.MoveTo(from = 2, to = 0))

        val byButtons = builder(savedState = SavedStateHandle())
        byButtons.add("a", "b", "c")
        byButtons.onEvent(RoutineBuilderEvent.MoveUp(2))
        byButtons.onEvent(RoutineBuilderEvent.MoveUp(1))

        assertEquals(listOf("c", "a", "b"), byMoveTo.order())
        assertEquals(byMoveTo.order(), byButtons.order())
    }

    @Test
    fun moveDown_movesTheRowOneStep_andTheEndsDoNotMove() = runTest {
        val vm = builder()
        vm.add("a", "b", "c")

        vm.onEvent(RoutineBuilderEvent.MoveDown(0))
        vm.onEvent(RoutineBuilderEvent.MoveDown(2))
        vm.onEvent(RoutineBuilderEvent.MoveUp(0))

        assertEquals(listOf("b", "a", "c"), vm.order())
        assertFalse(vm.form().rows.first().canMoveUp)
        assertFalse(vm.form().rows.last().canMoveDown)
    }

    @Test
    fun reorderedRows_keepTheirStableIds() = runTest {
        val vm = builder()
        vm.add("a", "b", "c")
        val before = vm.form().rows.associate { it.exerciseId to it.rowId }

        vm.onEvent(RoutineBuilderEvent.MoveTo(0, 2))

        assertEquals(before, vm.form().rows.associate { it.exerciseId to it.rowId })
        assertEquals(3, vm.form().rows.map { it.rowId }.toSet().size)
    }

    @Test
    fun groupingTwoExercises_labelsThemA1AndA2_withTheSameStoredGroup() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.SetName("Brazos"))
        vm.add("a", "b", "c")

        vm.onEvent(RoutineBuilderEvent.GroupWithNext(0))

        assertEquals(
            listOf("A1", "A2", null),
            vm.form().rows.map {
                it.supersetLabel?.let { l -> "${l.letter}${l.number}" }
            },
        )
        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()
        val groups = repository.saved.single().exercises.map { it.supersetGroup }
        assertNotNullAndEqual(groups[0], groups[1])
        assertNull(groups[2])
    }

    private fun assertNotNullAndEqual(first: Int?, second: Int?) {
        assertTrue(first != null && first == second, "expected the same non-null group, got $first and $second")
    }

    @Test
    fun groupingTheLastRow_changesNothing() = runTest {
        val vm = builder()
        vm.add("a", "b")

        vm.onEvent(RoutineBuilderEvent.GroupWithNext(1))

        assertTrue(vm.form().rows.all { it.supersetLabel == null })
        assertFalse(vm.form().rows.last().canGroupWithNext)
    }

    @Test
    fun ungroup_removesTheWholeSuperset() = runTest {
        val vm = builder()
        vm.add("a", "b", "c")
        vm.onEvent(RoutineBuilderEvent.GroupWithNext(0))
        vm.onEvent(RoutineBuilderEvent.GroupWithNext(1))
        assertEquals(3, vm.form().rows.count { it.supersetLabel != null })

        vm.onEvent(RoutineBuilderEvent.Ungroup(1))

        assertTrue(vm.form().rows.all { it.supersetLabel == null })
    }

    @Test
    fun movingAMemberOutOfAGroup_dissolvesTheGroupLeftWithOneRow() = runTest {
        val vm = builder()
        vm.add("a", "b", "c")
        vm.onEvent(RoutineBuilderEvent.GroupWithNext(0))

        vm.onEvent(RoutineBuilderEvent.MoveTo(from = 1, to = 2))

        assertTrue(vm.form().rows.all { it.supersetLabel == null })
    }

    @Test
    fun removingARow_renumbersTheSupersetLabels() = runTest {
        val vm = builder()
        vm.add("a", "b", "c", "custom")
        vm.onEvent(RoutineBuilderEvent.GroupWithNext(1))

        vm.onEvent(RoutineBuilderEvent.Remove(0))

        assertEquals(listOf("b", "c", "custom"), vm.order())
        assertEquals(
            listOf("A1", "A2", null),
            vm.form().rows.map {
                it.supersetLabel?.let { l -> "${l.letter}${l.number}" }
            },
        )
    }

    @Test
    fun removingAMember_dissolvesAGroupLeftWithOneRow() = runTest {
        val vm = builder()
        vm.add("a", "b", "c")
        vm.onEvent(RoutineBuilderEvent.GroupWithNext(0))

        vm.onEvent(RoutineBuilderEvent.Remove(1))

        assertEquals(listOf("a", "c"), vm.order())
        assertTrue(vm.form().rows.all { it.supersetLabel == null })
    }

    @Test
    fun validation_needsANonBlankNameAndAtLeastOneExercise() = runTest {
        val vm = builder()
        assertFalse(vm.form().canSave)

        vm.add("a")
        assertFalse(vm.form().canSave)

        vm.onEvent(RoutineBuilderEvent.SetName("   "))
        assertFalse(vm.form().canSave)

        vm.onEvent(RoutineBuilderEvent.SetName("Torso"))
        assertTrue(vm.form().canSave)

        vm.onEvent(RoutineBuilderEvent.Remove(0))
        assertFalse(vm.form().canSave)
    }

    @Test
    fun saveWithoutAValidForm_savesNothing() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()

        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun offline_disablesSaving() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.SetName("Torso"))
        vm.add("a")
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(vm.form().isOnline)
        assertFalse(vm.form().canSave)
        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun steppers_stayWithinTheirBounds() = runTest {
        val vm = builder()
        vm.add("a")

        repeat(20) { vm.onEvent(RoutineBuilderEvent.StepSets(0, +1)) }
        repeat(80) { vm.onEvent(RoutineBuilderEvent.StepReps(0, +1)) }
        repeat(60) { vm.onEvent(RoutineBuilderEvent.StepRest(0, +1)) }
        val max = vm.form().rows.single()
        repeat(20) { vm.onEvent(RoutineBuilderEvent.StepSets(0, -1)) }
        repeat(80) { vm.onEvent(RoutineBuilderEvent.StepReps(0, -1)) }
        repeat(60) { vm.onEvent(RoutineBuilderEvent.StepRest(0, -1)) }
        val min = vm.form().rows.single()

        assertEquals(Triple(10, 50, 600), Triple(max.sets, max.reps, max.restSeconds))
        assertEquals(Triple(1, 1, 0), Triple(min.sets, min.reps, min.restSeconds))
    }

    @Test
    fun restMovesInStepsOfFifteenSeconds() = runTest {
        val vm = builder()
        vm.add("a")

        vm.onEvent(RoutineBuilderEvent.StepRest(0, +1))
        vm.onEvent(RoutineBuilderEvent.StepRest(0, +1))
        vm.onEvent(RoutineBuilderEvent.StepRest(0, -1))

        assertEquals(135, vm.form().rows.single().restSeconds)
    }

    @Test
    fun saveFailure_keepsTheEditsAndReportsIt() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.SetName("Torso"))
        vm.add("a", "b")
        repository.saveFailure = AppError.Unknown

        vm.effects.test {
            vm.onEvent(RoutineBuilderEvent.Save)
            advanceUntilIdle()

            assertEquals(RoutineBuilderEffect.SaveFailed(offline = false), awaitItem())
        }

        assertEquals("Torso", vm.form().name)
        assertEquals(listOf("a", "b"), vm.order())
        assertFalse(vm.form().isSaving)
        assertTrue(vm.form().canSave)
    }

    @Test
    fun aNetworkSaveFailure_isReportedAsOffline() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.SetName("Torso"))
        vm.add("a")
        repository.saveFailure = AppError.Network

        vm.effects.test {
            vm.onEvent(RoutineBuilderEvent.Save)
            advanceUntilIdle()

            assertEquals(RoutineBuilderEffect.SaveFailed(offline = true), awaitItem())
        }
    }

    @Test
    fun editMode_loadsTheExistingRoutine_andSavingKeepsItsId() = runTest {
        repository.routines +=
            Routine(
                id = "r1",
                name = "Pierna",
                notes = "nota",
                archived = false,
                exercises =
                listOf(
                    RoutineExercise("c", 1, 4, targetSets = 4, targetReps = 6, restSeconds = 90),
                    RoutineExercise("a", 0, 4, targetSets = 3, targetReps = 10, restSeconds = 60),
                ),
            )

        val vm = builder(routineId = "r1")

        assertTrue(vm.form().isEditMode)
        assertEquals("Pierna", vm.form().name)
        assertEquals(listOf("a", "c"), vm.order())
        assertEquals(listOf("A1", "A2"), vm.form().rows.map { it.supersetLabel?.let { l -> "${l.letter}${l.number}" } })
        assertFalse(vm.form().isDirty)

        vm.onEvent(RoutineBuilderEvent.StepSets(0, +1))
        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()
        val saved = repository.saved.single()
        assertEquals("r1", saved.id)
        assertEquals("nota", saved.notes)
        assertEquals(4, saved.exercises.first().targetSets)
    }

    @Test
    fun editMode_showsLoadingFirst_andAnErrorWithRetry() = runTest {
        repository.routines += routineOf("r1", "Pierna", "a")
        repository.failure = AppError.Network
        val vm =
            RoutineBuilderViewModel("r1", handle, repository, LoadExerciseCatalog(exercises, library), connectivity)
        assertEquals(RoutineBuilderUiState.Loading, vm.uiState.value)

        advanceUntilIdle()
        assertEquals(RoutineBuilderUiState.LoadError, vm.uiState.value)

        repository.failure = null
        vm.onEvent(RoutineBuilderEvent.Retry)
        advanceUntilIdle()
        assertEquals("Pierna", vm.form().name)
    }

    @Test
    fun aDraftInTheSavedStateHandle_isRestored_withoutReloadingTheRoutine() = runTest {
        repository.routines += routineOf("r1", "Pierna", "a")
        val first = builder(routineId = "r1")
        first.onEvent(RoutineBuilderEvent.SetName("Pierna editada"))
        first.add("b")
        first.onEvent(RoutineBuilderEvent.GroupWithNext(0))
        repository.routines.clear()

        val second = builder(routineId = "r1", savedState = handle)

        assertEquals("Pierna editada", second.form().name)
        assertEquals(listOf("a", "b"), second.order())
        assertEquals(
            listOf("A1", "A2"),
            second.form().rows.map {
                it.supersetLabel?.let { l -> "${l.letter}${l.number}" }
            },
        )
        assertTrue(second.form().isDirty)
    }

    @Test
    fun aRestoredDraftOfANewRoutine_isDirty_andNewRowsKeepGettingFreshIds() = runTest {
        val first = builder()
        first.add("a")

        val second = builder(savedState = handle)
        second.add("b")

        assertEquals(listOf("a", "b"), second.order())
        assertEquals(2, second.form().rows.map { it.rowId }.toSet().size)
    }

    @Test
    fun back_withoutEdits_closesAtOnce() = runTest {
        val vm = builder()

        vm.effects.test {
            vm.onEvent(RoutineBuilderEvent.Back)

            assertEquals(RoutineBuilderEffect.Close, awaitItem())
        }
    }

    @Test
    fun back_withEdits_asksBeforeDiscarding() = runTest {
        val vm = builder()
        vm.add("a")

        vm.onEvent(RoutineBuilderEvent.Back)
        assertTrue(vm.form().showDiscardConfirm)

        vm.onEvent(RoutineBuilderEvent.DismissDiscard)
        assertFalse(vm.form().showDiscardConfirm)

        vm.onEvent(RoutineBuilderEvent.Back)
        vm.effects.test {
            vm.onEvent(RoutineBuilderEvent.ConfirmDiscard)

            assertEquals(RoutineBuilderEffect.Close, awaitItem())
        }
    }

    @Test
    fun theNameIsLimitedToTheDatabaseLength() = runTest {
        val vm = builder()

        vm.onEvent(RoutineBuilderEvent.SetName("x".repeat(150)))

        assertEquals(100, vm.form().name.length)
    }
}
