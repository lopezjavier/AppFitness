package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderInputs
import io.github.lopezjavier.appfitness.domain.coach.builder.DaysCheck
import io.github.lopezjavier.appfitness.domain.coach.builder.SplitCatalog
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.RepRange
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.usecase.BuildBlockUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.LoadFolderBlockUseCase
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.FakeFavoriteExercisesRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FolderBlockViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val blocks = FakeCoachBlockRepository()
    private val plans = FakeCoachPlanRepository()
    private val prefs = FakeTrainingPreferencesRepository()
    private val favourites = FakeFavoriteExercisesRepository()
    private val routines = FakeRoutineRepository()
    private val groups = SplitCatalog.ALL_GROUPS

    private val mainPattern = mapOf(
        MuscleGroup.Chest to MovementPattern.HorizontalPush,
        MuscleGroup.Back to MovementPattern.HorizontalPull,
        MuscleGroup.Shoulders to MovementPattern.VerticalPush,
        MuscleGroup.Quads to MovementPattern.Squat,
        MuscleGroup.Hamstrings to MovementPattern.Hinge,
        MuscleGroup.Glutes to MovementPattern.Hinge,
    )

    private fun rows(): List<Exercise> = groups.flatMap { g ->
        val key = g.name.lowercase()
        listOf(exerciseRow("$key-main", "$key-main", g, Equipment.Barbell, pattern = mainPattern.getValue(g))) +
            (1..4).map {
                exerciseRow("$key-iso$it", "$key-iso$it", g, Equipment.Machine, pattern = MovementPattern.Isolation)
            }
    } + listOf(
        exerciseRow("stretch", "stretch", MuscleGroup.Back, Equipment.Machine, pattern = MovementPattern.Isolation)
            .copy(countsAsStrength = false),
        exerciseRow(
            "bike",
            "bike",
            MuscleGroup.FullBody,
            metric = ExerciseMetricType.Duration,
            pattern = MovementPattern.Cardio,
        ),
    )

    private val exerciseRepository = FakeExerciseRepository(rows())
    private val library = FakeExerciseLibrary(
        rows().associate { it.key!! to exerciseInfo(it.key!!, "Nombre ${it.key}") },
    )

    private fun planned(id: String, sets: Int = 5, range: RepRange? = RepRange(8, 12)) = RoutineExercise(
        exerciseId = id,
        position = 0,
        supersetGroup = null,
        plannedSets = List(sets) { PlannedSet(null, 10) },
        restSeconds = 120,
        targetRpe = 8.0,
        repRange = range,
    )

    private fun routine(id: String, vararg ex: RoutineExercise) =
        Routine(id, "Rutina $id", null, false, ex.mapIndexed { i, e -> e.copy(position = i) })

    /** Two routines, every group once per routine at 5 sets: 100 %. [skip] drops one group from both. */
    private fun folder(skip: MuscleGroup? = null, intention: TrainingIntention = TrainingIntention.Muscle) {
        prefs.result = Result.success(
            TrainingPreferences(intention = intention, daysPerWeek = 2, sessionMinutes = 180),
        )
        val ids = listOf("1", "2")
        ids.forEach { n ->
            val ex = groups.filter { it != skip }.map { g ->
                planned("${g.name.lowercase()}-iso$n")
            }
            routines.routines += routine(n, *ex.toTypedArray())
            routines.folderOf[n] = "f"
        }
        routines.folders += RoutineFolder("f", "Fuerza")
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        blocks.builderInputs = Result.success(BuilderInputs(emptyMap(), listOf("bike"), "bike"))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(saved: SavedStateHandle = SavedStateHandle()): FolderBlockViewModel {
        val model = FolderBlockViewModel(
            planId = "plan-1",
            folderId = "f",
            buildBlock = BuildBlockUseCase(blocks, LoadFolderBlockUseCase(routines, exerciseRepository, prefs)),
            catalog = LoadExerciseCatalog(exerciseRepository, library),
            favourites = favourites,
            plans = plans,
            clock = MutableClock(),
            savedState = saved,
        )
        advanceUntilIdle()
        return model
    }

    private fun TestScope.send(model: FolderBlockViewModel, vararg events: FolderBlockEvent) {
        events.forEach { model.onEvent(it) }
        advanceUntilIdle()
    }

    private fun FolderBlockViewModel.content() = assertIs<FolderBlockUiState.Content>(uiState.value)

    private suspend fun ReceiveTurbine<FolderBlockEffect>.awaitEffect(expected: FolderBlockEffect) {
        while (awaitItem() != expected) {
            // Skips the haptic effects emitted before.
        }
    }

    private fun assertNothingWritten() {
        assertTrue(routines.saved.isEmpty())
        assertTrue(routines.createdFolders.isEmpty())
        assertTrue(routines.renamedFolders.isEmpty())
        assertTrue(routines.deletedIds.isEmpty())
        assertTrue(routines.movedToFolder.isEmpty())
        assertTrue(blocks.builtSaves.isEmpty())
    }

    @Test
    fun folderAt100Percent_savesUnderAUniqueName_andNeverWritesTheSourceFolder() = runTest(dispatcher) {
        folder()
        routines.folders += RoutineFolder("g", "Fuerza · bloque")
        val model = viewModel()
        assertEquals(100, model.content().status.percent)
        assertTrue(model.content().canSave)

        send(model, FolderBlockEvent.Save)

        model.effects.test { awaitEffect(FolderBlockEffect.Saved) }
        val (plan, block, name) = blocks.builtSaves.single()
        assertEquals("plan-1", plan)
        assertEquals("Fuerza · bloque 2", name)
        assertEquals(2, block.daysPerWeek)
        assertTrue(routines.saved.isEmpty() && routines.createdFolders.isEmpty())
        assertFalse(model.content().saving)
    }

    @Test
    fun loading_writesNothing() = runTest(dispatcher) {
        folder(skip = MuscleGroup.Hamstrings)
        viewModel()
        assertNothingWritten()
    }

    @Test
    fun daysDifferFromTheLiftingRoutines_isRefusedWithBothNumbers_andUnlocksAfterThePreferenceChanges() =
        runTest(dispatcher) {
            folder()
            prefs.result = Result.success(prefs.result.getOrThrow().copy(daysPerWeek = 3))
            val model = viewModel()

            assertEquals(DaysCheck.Refused(2, 3), assertIs<FolderBlockUiState.Refused>(model.uiState.value).days)
            send(model, FolderBlockEvent.Save)
            assertNothingWritten()

            prefs.result = Result.success(prefs.result.getOrThrow().copy(daysPerWeek = 2))
            send(model, FolderBlockEvent.Retry)
            assertIs<FolderBlockUiState.Content>(model.uiState.value)
        }

    @Test
    fun oneRoutineWithTwoDays_isRefusedAsNeedingASecondRoutine() = runTest(dispatcher) {
        folder()
        routines.routines.removeAt(1)
        routines.folderOf.remove("2")

        val model = viewModel()

        assertEquals(DaysCheck.NeedsSecondRoutine, assertIs<FolderBlockUiState.Refused>(model.uiState.value).days)
    }

    @Test
    fun folderWithoutRoutines_isEmpty() = runTest(dispatcher) {
        folder()
        routines.routines.clear()

        val model = viewModel()

        assertEquals("Fuerza", assertIs<FolderBlockUiState.Empty>(model.uiState.value).folderName)
    }

    @Test
    fun addChain_skipsSingleOptionSteps_listsOnlySameGroupExercises_andRaisesThePercent() = runTest(dispatcher) {
        folder(skip = MuscleGroup.Hamstrings)
        val model = viewModel()
        val before = model.content().status.percent
        assertTrue(before < 100)

        send(model, FolderBlockEvent.OpenAdd)
        assertEquals(FolderPickerTarget.ChooseRoutine(MuscleGroup.Hamstrings), model.content().pickerTarget)

        send(model, FolderBlockEvent.ChooseRoutine(0))
        val entries = model.content().pickerEntries
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.all { it.exercise.exercise.muscleGroup == MuscleGroup.Hamstrings })

        send(model, FolderBlockEvent.PickExercise("hamstrings-iso1"))
        assertNotEquals(null, model.content().draft.routines[0].exercises.firstOrNull { it.added })
        assertEquals(null, model.content().pickerTarget)
        assertTrue(model.content().status.percent > before)

        send(
            model,
            FolderBlockEvent.OpenAdd,
            FolderBlockEvent.ChooseRoutine(1),
            FolderBlockEvent.PickExercise("hamstrings-iso1"),
        )
        assertEquals(100, model.content().status.percent)
        assertTrue(model.content().canSave)
        assertNothingWritten()
    }

    @Test
    fun addChain_withTwoUnmetGroups_asksForTheGroupFirst() = runTest(dispatcher) {
        folder(skip = MuscleGroup.Hamstrings)
        routines.routines.forEach { r ->
            routines.routines[routines.routines.indexOf(r)] =
                r.copy(exercises = r.exercises.filter { it.exerciseId != "chest-iso${r.id}" })
        }
        val model = viewModel()
        assertEquals(2, model.content().addableGroups.size)

        send(model, FolderBlockEvent.OpenAdd)
        assertEquals(FolderPickerTarget.ChooseGroup, model.content().pickerTarget)

        send(model, FolderBlockEvent.ChooseGroup(MuscleGroup.Chest), FolderBlockEvent.ChooseRoutine(1))
        assertEquals(FolderPickerTarget.Add(MuscleGroup.Chest, 1), model.content().pickerTarget)
    }

    @Test
    fun addOpen_atOneHundredPercent_doesNothing() = runTest(dispatcher) {
        folder()
        val model = viewModel()

        send(model, FolderBlockEvent.OpenAdd)

        assertEquals(null, model.content().pickerTarget)
    }

    @Test
    fun swap_listsSameGroupRows_disablesUsedOnes_andReplacesTheExercise() = runTest(dispatcher) {
        folder()
        val model = viewModel()

        send(model, FolderBlockEvent.OpenSwap(0, 0))
        val entries = model.content().pickerEntries
        val group = model.content().draft.routines[0].exercises[0].muscleGroup
        assertTrue(entries.all { it.exercise.exercise.muscleGroup == group })
        assertTrue(entries.none { it.exercise.id == "${group.name.lowercase()}-iso1" && it.disabled })

        send(model, FolderBlockEvent.PickExercise("${group.name.lowercase()}-iso3"))

        assertEquals("${group.name.lowercase()}-iso3", model.content().draft.routines[0].exercises[0].id)
        assertEquals(100, model.content().status.percent)
    }

    @Test
    fun removeAdded_removesAnAddedRow_butNeverAFolderRow() = runTest(dispatcher) {
        folder(skip = MuscleGroup.Hamstrings)
        val model = viewModel()
        send(
            model,
            FolderBlockEvent.OpenAdd,
            FolderBlockEvent.ChooseRoutine(0),
            FolderBlockEvent.PickExercise("hamstrings-iso1"),
        )
        val addedIndex = model.content().draft.routines[0].exercises.indexOfFirst { it.added }

        send(model, FolderBlockEvent.RemoveAdded(0, 0))
        assertEquals(1, model.content().draft.routines[0].exercises.count { it.added })

        send(model, FolderBlockEvent.RemoveAdded(0, addedIndex))
        assertEquals(0, model.content().draft.routines[0].exercises.count { it.added })
    }

    @Test
    fun strengthRepFix_changesTheRepRange_andClearsTheFix() = runTest(dispatcher) {
        folder(intention = TrainingIntention.Strength)
        routines.routines[0] = routines.routines[0].let { r ->
            r.copy(
                exercises = r.exercises.map {
                    if (it.exerciseId ==
                        "chest-iso1"
                    ) {
                        it.copy(exerciseId = "chest-main")
                    } else {
                        it
                    }
                },
            )
        }
        val model = viewModel()
        val fix = model.content().status.repFixes.single()

        send(model, FolderBlockEvent.ApplyRepFix(fix.routineIndex, fix.exerciseIndex))

        assertTrue(model.content().status.repFixes.isEmpty())
        val e = model.content().draft.routines[fix.routineIndex].exercises[fix.exerciseIndex]
        assertEquals(6 to 8, e.repMin to e.repMax)
    }

    @Test
    fun back_withNothingEdited_closesSilently_andWithEditsAsksFirst() = runTest(dispatcher) {
        folder(skip = MuscleGroup.Hamstrings)
        val model = viewModel()

        model.effects.test {
            send(model, FolderBlockEvent.Back)
            awaitEffect(FolderBlockEffect.Close)
        }

        send(
            model,
            FolderBlockEvent.OpenAdd,
            FolderBlockEvent.ChooseRoutine(0),
            FolderBlockEvent.PickExercise("hamstrings-iso1"),
        )
        send(model, FolderBlockEvent.Back)
        assertTrue(model.content().discardDialog)
        send(model, FolderBlockEvent.CancelDiscard)
        assertFalse(model.content().discardDialog)
        send(model, FolderBlockEvent.Back, FolderBlockEvent.ConfirmDiscard)
        model.effects.test { awaitEffect(FolderBlockEffect.Close) }
        assertNothingWritten()
    }

    @Test
    fun back_withAnOpenPicker_closesThePickerFirst() = runTest(dispatcher) {
        folder()
        val model = viewModel()
        send(model, FolderBlockEvent.OpenSwap(0, 0), FolderBlockEvent.Back)

        assertEquals(null, model.content().pickerTarget)
        assertFalse(model.content().discardDialog)
    }

    @Test
    fun back_fromRefusedFailedAndEmpty_closes() = runTest(dispatcher) {
        folder()
        routines.routines.clear()
        val model = viewModel()
        model.effects.test {
            send(model, FolderBlockEvent.Back)
            awaitEffect(FolderBlockEffect.Close)
        }
    }

    @Test
    fun saveFailures_showTheirKind_keepTheEdits_andResetSaving() = runTest(dispatcher) {
        listOf(
            CoachException(CoachFailure.Conflict) to BuilderSaveError.PlanClosed,
            CoachException(CoachFailure.InvalidBlock) to BuilderSaveError.Generic,
            AppException(AppError.Network) to BuilderSaveError.Offline,
        ).forEach { (failure, kind) ->
            routines.routines.clear()
            routines.folders.clear()
            folder()
            blocks.builtSaveResult = Result.failure(failure)
            val model = viewModel()

            send(model, FolderBlockEvent.Save)

            assertEquals(kind, model.content().saveError)
            assertFalse(model.content().saving)
            assertEquals(100, model.content().status.percent)
            assertEquals(kind != BuilderSaveError.PlanClosed, model.content().canSave)
        }
    }

    @Test
    fun save_below100Percent_isIgnored() = runTest(dispatcher) {
        folder(skip = MuscleGroup.Hamstrings)
        val model = viewModel()

        send(model, FolderBlockEvent.Save)

        assertTrue(blocks.builtSaves.isEmpty())
    }

    @Test
    fun failedRead_isShownOfflineOrGeneric_andRetryLoadsAgain() = runTest(dispatcher) {
        folder()
        routines.failure = AppError.Network
        val model = viewModel()
        assertEquals(FolderBlockUiState.Failed(offline = true), model.uiState.value)

        routines.failure = AppError.Unknown
        send(model, FolderBlockEvent.Retry)
        assertEquals(FolderBlockUiState.Failed(offline = false), model.uiState.value)

        routines.failure = null
        send(model, FolderBlockEvent.Retry)
        assertIs<FolderBlockUiState.Content>(model.uiState.value)
    }

    @Test
    fun missingDays_isAPlainFailure() = runTest(dispatcher) {
        folder()
        prefs.result = Result.success(TrainingPreferences(daysPerWeek = null, sessionMinutes = 60))

        assertEquals(FolderBlockUiState.Failed(offline = false), viewModel().uiState.value)
    }

    @Test
    fun recreatedFromTheSameSavedState_replaysTheEditsAndRestoresThePicker() = runTest(dispatcher) {
        folder(skip = MuscleGroup.Hamstrings)
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        send(
            model,
            FolderBlockEvent.OpenAdd,
            FolderBlockEvent.ChooseRoutine(0),
            FolderBlockEvent.PickExercise("hamstrings-iso1"),
        )
        send(model, FolderBlockEvent.OpenAdd, FolderBlockEvent.ChooseRoutine(1))
        val expected = model.content()

        val restored = viewModel(saved).content()

        assertEquals(expected.status.percent, restored.status.percent)
        assertEquals(expected.draft, restored.draft)
        assertEquals(FolderPickerTarget.Add(MuscleGroup.Hamstrings, 1), restored.pickerTarget)
        assertTrue(restored.edited)
    }

    @Test
    fun recreatedWithTheDiscardDialogOpen_keepsIt() = runTest(dispatcher) {
        folder(skip = MuscleGroup.Hamstrings)
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        send(
            model,
            FolderBlockEvent.OpenAdd,
            FolderBlockEvent.ChooseRoutine(0),
            FolderBlockEvent.PickExercise("hamstrings-iso1"),
        )
        send(model, FolderBlockEvent.Back)

        assertTrue(viewModel(saved).content().discardDialog)
    }

    @Test
    fun recreatedRefused_staysRefused() = runTest(dispatcher) {
        folder()
        prefs.result = Result.success(prefs.result.getOrThrow().copy(daysPerWeek = 3))
        val saved = SavedStateHandle()
        viewModel(saved)

        assertIs<FolderBlockUiState.Refused>(viewModel(saved).uiState.value)
    }
}
