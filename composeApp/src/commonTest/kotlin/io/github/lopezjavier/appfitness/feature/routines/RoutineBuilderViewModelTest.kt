package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeExerciseOptionsRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SetType
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

@Suppress("TooManyFunctions", "LargeClass") // One method per Gherkin scenario or rule.
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
                FakeTrainingPreferencesRepository(),
                FakeExerciseOptionsRepository(),
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
                RoutineExercise("a", 0, null, List(3) { PlannedSet(null, 8) }, restSeconds = 120),
                RoutineExercise("b", 1, null, List(3) { PlannedSet(null, 8) }, restSeconds = 120),
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
    fun restStepper_staysWithinItsBounds() = runTest {
        val vm = builder()
        vm.add("a")

        repeat(60) { vm.onEvent(RoutineBuilderEvent.StepRest(0, +1)) }
        val max = vm.form().rows.single().restSeconds
        repeat(80) { vm.onEvent(RoutineBuilderEvent.StepRest(0, -1)) }
        val min = vm.form().rows.single().restSeconds

        assertEquals(600 to 0, max to min)
    }

    @Test
    fun restMovesInStepsOfTenSeconds() = runTest {
        val vm = builder()
        vm.add("a")

        vm.onEvent(RoutineBuilderEvent.StepRest(0, +1))
        vm.onEvent(RoutineBuilderEvent.StepRest(0, +1))
        vm.onEvent(RoutineBuilderEvent.StepRest(0, -1))

        assertEquals(130, vm.form().rows.single().restSeconds)
    }

    @Test
    fun restOffTheTenSecondGrid_movesByTenFromItsCurrentValue() = runTest {
        repository.routines.add(
            Routine(
                "r1",
                "Torso",
                null,
                false,
                listOf(
                    RoutineExercise(
                        "a",
                        0,
                        null,
                        List(3) {
                            PlannedSet(null, 8)
                        },
                        restSeconds = 45,
                    ),
                ),
            ),
        )
        val vm = builder("r1")

        vm.onEvent(RoutineBuilderEvent.StepRest(0, +1))

        assertEquals(55, vm.form().rows.single().restSeconds)
    }

    @Test
    fun anExerciseCreatedAfterTheBuilderOpened_showsItsOwnName() = runTest {
        val vm = builder()
        exercises.rows += exerciseRow("new-custom", null, name = "Curl nórdico", custom = true)

        vm.add("new-custom")
        advanceUntilIdle()

        assertEquals("Curl nórdico", vm.form().rows.single().name)
    }

    @Test
    fun aRestoredDraftWithACustomExercise_resolvesItsName() = runTest {
        val first = builder()
        first.add("custom")

        val second = builder(savedState = handle)

        assertEquals("Mi ejercicio", second.form().rows.single().name)
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
                    RoutineExercise("c", 1, 4, List(3) { PlannedSet(null, 6) }, restSeconds = 90),
                    RoutineExercise("a", 0, 4, List(3) { PlannedSet(null, 10) }, restSeconds = 60),
                ),
            )

        val vm = builder(routineId = "r1")

        assertTrue(vm.form().isEditMode)
        assertEquals("Pierna", vm.form().name)
        assertEquals(listOf("a", "c"), vm.order())
        assertEquals(listOf("A1", "A2"), vm.form().rows.map { it.supersetLabel?.let { l -> "${l.letter}${l.number}" } })
        assertFalse(vm.form().isDirty)

        vm.onEvent(RoutineBuilderEvent.AddSet(0))
        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()
        val saved = repository.saved.single()
        assertEquals("r1", saved.id)
        assertEquals("nota", saved.notes)
        assertEquals(listOf(4, 4), saved.exercises.map { it.targetSets })
    }

    @Test
    fun editMode_showsLoadingFirst_andAnErrorWithRetry() = runTest {
        repository.routines += routineOf("r1", "Pierna", "a")
        repository.failure = AppError.Network
        val vm =
            RoutineBuilderViewModel(
                "r1",
                handle,
                repository,
                LoadExerciseCatalog(exercises, library),
                connectivity,
                FakeTrainingPreferencesRepository(),
                FakeExerciseOptionsRepository(),
            )
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
    fun aRestoredDraftOfANewRoutine_staysDirty_soBackStillAsksBeforeDiscarding() = runTest {
        val first = builder()
        first.add("a")

        val second = builder(savedState = handle)

        assertTrue(second.form().isDirty)
        second.onEvent(RoutineBuilderEvent.Back)
        assertTrue(second.form().showDiscardConfirm)
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

    private fun RoutineBuilderViewModel.sets(row: Int) =
        form().rows[row].plannedSets.map { Triple(it.weight, it.reps, it.type) }

    private fun bench(vararg sets: PlannedSet) = Routine(
        id = "r1",
        name = "Torso",
        notes = null,
        archived = false,
        exercises = listOf(RoutineExercise("a", 0, null, sets.toList(), restSeconds = 120)),
    )

    @Test
    fun plannedSets_ofAWarmUpAndThreeWorkingSets_areSavedAndReopenedAsTheSameFour() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.SetName("Torso"))
        vm.add("a")
        vm.onEvent(RoutineBuilderEvent.ChangeSetType(0, 0, SetType.WarmUp))
        vm.onEvent(RoutineBuilderEvent.SetWeight(0, 0, "40"))
        vm.onEvent(RoutineBuilderEvent.SetReps(0, 0, "10"))
        listOf(1, 2).forEach { vm.onEvent(RoutineBuilderEvent.SetWeight(0, it, "80")) }
        vm.onEvent(RoutineBuilderEvent.AddSet(0))

        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()

        val expected =
            listOf(
                PlannedSet(40.0, 10, SetType.WarmUp),
                PlannedSet(80.0, 8, SetType.Working),
                PlannedSet(80.0, 8, SetType.Working),
                PlannedSet(80.0, 8, SetType.Working),
            )
        assertEquals(expected, repository.saved.single().exercises.single().plannedSets)
        val reopened = builder("new-1", SavedStateHandle())
        assertEquals(
            listOf(
                Triple("40", "10", SetType.WarmUp),
                Triple("80", "8", SetType.Working),
                Triple("80", "8", SetType.Working),
                Triple("80", "8", SetType.Working),
            ),
            reopened.sets(0),
        )
    }

    @Test
    fun aNewExercise_startsWithThreeNormalSetsOfEightRepsAndNoWeight() = runTest {
        val vm = builder()

        vm.add("a")

        assertEquals(List(3) { Triple("", "8", SetType.Working) }, vm.sets(0))
    }

    @Test
    fun addSet_copiesTheLastSet_andStopsAtTen() = runTest {
        val vm = builder()
        vm.add("a")
        vm.onEvent(RoutineBuilderEvent.SetWeight(0, 2, "62,5"))
        vm.onEvent(RoutineBuilderEvent.ChangeSetType(0, 2, SetType.Drop))

        vm.onEvent(RoutineBuilderEvent.AddSet(0))
        assertEquals(Triple("62,5", "8", SetType.Drop), vm.sets(0)[3])

        repeat(12) { vm.onEvent(RoutineBuilderEvent.AddSet(0)) }
        assertEquals(10, vm.form().rows.single().plannedSets.size)
        assertFalse(vm.form().rows.single().canAddSet)
    }

    @Test
    fun removeSet_dropsThatSet_andNeverTheLastOne() = runTest {
        val vm = builder()
        vm.add("a")
        vm.onEvent(RoutineBuilderEvent.SetReps(0, 1, "12"))

        vm.onEvent(RoutineBuilderEvent.RemoveSet(0, 0))
        assertEquals(listOf("12", "8"), vm.form().rows.single().plannedSets.map { it.reps })

        repeat(5) { vm.onEvent(RoutineBuilderEvent.RemoveSet(0, 0)) }
        assertEquals(1, vm.form().rows.single().plannedSets.size)
        assertFalse(vm.form().rows.single().canRemoveSet)
    }

    @Test
    fun editingOneSet_leavesTheOthersUntouched() = runTest {
        val vm = builder()
        vm.add("a")

        vm.onEvent(RoutineBuilderEvent.SetWeight(0, 1, "80"))
        vm.onEvent(RoutineBuilderEvent.SetReps(0, 1, "5"))
        vm.onEvent(RoutineBuilderEvent.ChangeSetType(0, 1, SetType.Failure))

        assertEquals(
            listOf(
                Triple("", "8", SetType.Working),
                Triple("80", "5", SetType.Failure),
                Triple("", "8", SetType.Working),
            ),
            vm.sets(0),
        )
    }

    @Test
    fun quitarTipo_isChoosingNormal() = runTest {
        val vm = builder()
        vm.add("a")
        vm.onEvent(RoutineBuilderEvent.ChangeSetType(0, 0, SetType.WarmUp))

        vm.onEvent(RoutineBuilderEvent.ChangeSetType(0, 0, SetType.Working))

        assertEquals(SetType.Working, vm.form().rows.single().plannedSets.first().type)
    }

    @Test
    fun aSetWithoutValidReps_blocksSaving_butAnEmptyWeightDoesNot() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.SetName("Torso"))
        vm.add("a")
        assertTrue(vm.form().canSave)

        vm.onEvent(RoutineBuilderEvent.SetReps(0, 0, ""))
        assertFalse(vm.form().canSave)
        vm.onEvent(RoutineBuilderEvent.SetReps(0, 0, "51"))
        assertFalse(vm.form().canSave)
        vm.onEvent(RoutineBuilderEvent.SetReps(0, 0, "50"))
        assertTrue(vm.form().canSave)
    }

    @Test
    fun inASuperset_addingAndRemovingASet_appliesToEveryMember() = runTest {
        val vm = builder()
        vm.add("a", "b", "c")
        vm.onEvent(RoutineBuilderEvent.GroupWithNext(0))

        vm.onEvent(RoutineBuilderEvent.AddSet(1))
        assertEquals(listOf(4, 4, 3), vm.form().rows.map { it.plannedSets.size })

        vm.onEvent(RoutineBuilderEvent.RemoveSet(0, 0))
        vm.onEvent(RoutineBuilderEvent.RemoveSet(0, 0))
        assertEquals(listOf(2, 2, 3), vm.form().rows.map { it.plannedSets.size })
    }

    @Test
    fun inASuperset_theLimitsApplyToTheWholeGroup() = runTest {
        val vm = builder()
        vm.add("a", "b")
        vm.onEvent(RoutineBuilderEvent.GroupWithNext(0))

        repeat(12) { vm.onEvent(RoutineBuilderEvent.AddSet(0)) }

        assertEquals(listOf(10, 10), vm.form().rows.map { it.plannedSets.size })
    }

    @Test
    fun groupingTwoExercisesWithDifferentCounts_makesTheSecondAdoptTheFirstCount() = runTest {
        val vm = builder()
        vm.add("a", "b", "c")
        repeat(2) { vm.onEvent(RoutineBuilderEvent.AddSet(0)) }
        vm.onEvent(RoutineBuilderEvent.RemoveSet(1, 0))

        vm.onEvent(RoutineBuilderEvent.GroupWithNext(0))
        assertEquals(listOf(5, 5, 3), vm.form().rows.map { it.plannedSets.size })

        val other = builder(savedState = SavedStateHandle())
        other.add("a", "b")
        other.onEvent(RoutineBuilderEvent.RemoveSet(0, 0))
        other.onEvent(RoutineBuilderEvent.GroupWithNext(0))
        assertEquals(listOf(2, 2), other.form().rows.map { it.plannedSets.size })
    }

    @Test
    fun aSupersetSavesWithEqualSetCounts() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.SetName("Brazos"))
        vm.add("a", "b")
        vm.onEvent(RoutineBuilderEvent.GroupWithNext(0))
        vm.onEvent(RoutineBuilderEvent.AddSet(1))

        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf(4, 4), repository.saved.single().exercises.map { it.targetSets })
    }

    @Test
    fun editingTheSetsOfTheSecondSupersetMember_ofALongerRoutine_keepsTheCountsEqual() = runTest {
        repository.routines +=
            Routine(
                "r1",
                "Largo",
                null,
                false,
                listOf(
                    RoutineExercise("a", 0, null, List(3) { PlannedSet(null, 8) }, restSeconds = 60),
                    RoutineExercise("b", 1, 1, List(3) { PlannedSet(50.0, 10) }, restSeconds = 60),
                    RoutineExercise("c", 2, 1, List(3) { PlannedSet(30.0, 12, SetType.Drop) }, restSeconds = 60),
                    RoutineExercise("custom", 3, null, List(2) { PlannedSet(null, 5) }, restSeconds = 60),
                ),
            )
        val vm = builder("r1")

        vm.onEvent(RoutineBuilderEvent.AddSet(2))
        vm.onEvent(RoutineBuilderEvent.SetWeight(2, 3, "35"))
        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()

        val saved = repository.saved.single().exercises
        assertEquals(listOf(3, 4, 4, 2), saved.map { it.targetSets })
        assertEquals(PlannedSet(35.0, 12, SetType.Drop), saved[2].plannedSets.last())
        assertEquals(PlannedSet(50.0, 10, SetType.Working), saved[1].plannedSets.last())
    }

    @Test
    fun editingPlannedSets_makesTheDraftDirty_andBackAsks() = runTest {
        repository.routines += bench(PlannedSet(60.0, 8), PlannedSet(60.0, 8))
        val vm = builder("r1")
        assertFalse(vm.form().isDirty)

        vm.onEvent(RoutineBuilderEvent.SetWeight(0, 0, "65"))

        assertTrue(vm.form().isDirty)
        vm.onEvent(RoutineBuilderEvent.Back)
        assertTrue(vm.form().showDiscardConfirm)
    }

    @Test
    fun theTypePicker_opensForOneSet_andClosesWithoutChangingIt() = runTest {
        val vm = builder()
        vm.add("a")

        vm.onEvent(RoutineBuilderEvent.OpenTypePicker(0, 1))
        assertEquals(TypePickerUi(0, 1, SetType.Working), vm.form().typePicker)

        vm.onEvent(RoutineBuilderEvent.CloseTypePicker)
        assertNull(vm.form().typePicker)
        assertEquals(SetType.Working, vm.form().rows.single().plannedSets[1].type)
    }

    @Test
    fun choosingATypeInThePicker_appliesItToTheDraft_andClosesThePicker() = runTest {
        val vm = builder()
        vm.add("a")
        vm.onEvent(RoutineBuilderEvent.OpenTypePicker(0, 0))

        vm.onEvent(RoutineBuilderEvent.ChangeSetType(0, 0, SetType.WarmUp))

        assertNull(vm.form().typePicker)
        assertEquals(SetType.WarmUp, vm.form().rows.single().plannedSets.first().type)
    }

    @Test
    fun aRestoredDraft_keepsTheSetEditsAndTheOpenPicker() = runTest {
        val first = builder()
        first.add("a", "b")
        first.onEvent(RoutineBuilderEvent.SetWeight(1, 2, "77,5"))
        first.onEvent(RoutineBuilderEvent.ChangeSetType(1, 0, SetType.WarmUp))
        first.onEvent(RoutineBuilderEvent.AddSet(0))
        first.onEvent(RoutineBuilderEvent.OpenTypePicker(1, 2))

        val second = builder(savedState = handle)

        assertEquals(4, second.form().rows[0].plannedSets.size)
        assertEquals(
            listOf(
                Triple("", "8", SetType.WarmUp),
                Triple("", "8", SetType.Working),
                Triple("77,5", "8", SetType.Working),
            ),
            second.sets(1),
        )
        assertEquals(TypePickerUi(1, 2, SetType.Working), second.form().typePicker)
        second.add("c")
        assertEquals(10, second.form().rows.sumOf { it.plannedSets.size })
        assertEquals(10, second.form().rows.flatMap { it.plannedSets }.map { it.setId }.toSet().size)
    }

    private fun RoutineBuilderViewModel.stepRpe(delta: Int, times: Int = 1) =
        repeat(times) { onEvent(RoutineBuilderEvent.StepTargetRpe(0, delta)) }

    @Test
    fun targetRpe_startsWithNone_andTheFirstIncreaseGivesEight() = runTest {
        val vm = builder()
        vm.add("a")
        assertNull(vm.form().rows.single().targetRpe)

        vm.stepRpe(+1)

        assertEquals(8.0, vm.form().rows.single().targetRpe)
    }

    @Test
    fun targetRpe_movesInHalfPoints_andClampsAtTen() = runTest {
        val vm = builder()
        vm.add("a")

        vm.stepRpe(+1)
        vm.stepRpe(+1)
        assertEquals(8.5, vm.form().rows.single().targetRpe)
        vm.stepRpe(+1, times = 10)

        assertEquals(10.0, vm.form().rows.single().targetRpe)
    }

    @Test
    fun targetRpe_steppingDownFromSix_clearsIt() = runTest {
        val vm = builder()
        vm.add("a")
        vm.stepRpe(+1)
        vm.stepRpe(-1, times = 4)
        assertEquals(6.0, vm.form().rows.single().targetRpe)

        vm.stepRpe(-1)

        assertNull(vm.form().rows.single().targetRpe)
    }

    @Test
    fun targetRpe_steppingDownWithNone_staysNone() = runTest {
        val vm = builder()
        vm.add("a")

        vm.stepRpe(-1)

        assertNull(vm.form().rows.single().targetRpe)
    }

    @Test
    fun targetRpe_makesTheDraftDirty_andSaveSendsIt() = runTest {
        val vm = builder()
        vm.onEvent(RoutineBuilderEvent.SetName("Torso"))
        vm.add("a")
        vm.stepRpe(+1)
        assertTrue(vm.form().isDirty)

        vm.onEvent(RoutineBuilderEvent.Save)
        advanceUntilIdle()

        assertEquals(8.0, repository.saved.single().exercises.single().targetRpe)
    }

    @Test
    fun editingARoutine_loadsItsExistingTarget_andItSurvivesARecreation() = runTest {
        repository.routines.add(
            Routine(
                "r1",
                "Torso",
                null,
                false,
                listOf(RoutineExercise("a", 0, null, List(3) { PlannedSet(null, 8) }, 90, targetRpe = 7.5)),
            ),
        )
        val first = builder("r1")
        assertEquals(7.5, first.form().rows.single().targetRpe)
        first.stepRpe(+1)

        val second = builder("r1", handle)

        assertEquals(8.0, second.form().rows.single().targetRpe)
    }

    @Test
    fun aDraftSavedWithoutTheTargetField_stillDecodes() {
        val old = """{"name":"X","rows":[{"rowId":0,"exerciseId":"a","sets":[],"restSeconds":90,"group":null}]}"""

        val draft = decode(old)

        assertNull(draft!!.rows.single().targetRpe)
    }
}
