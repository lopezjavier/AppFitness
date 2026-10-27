package io.github.lopezjavier.appfitness.feature.exercises

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.domain.usecase.SearchExercises
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
import kotlin.test.assertTrue

class ExercisePickerViewModelTest {
    private val library =
        FakeExerciseLibrary(
            mapOf(
                "bench" to exerciseInfo("bench", "Press de banca"),
                "incline" to exerciseInfo("incline", "Press inclinado con mancuernas"),
                "row" to exerciseInfo("row", "Remo con barra"),
            ),
        )
    private val repository =
        FakeExerciseRepository(
            listOf(
                exerciseRow("1", "bench", MuscleGroup.Chest, Equipment.Barbell),
                exerciseRow("2", "incline", MuscleGroup.Chest, Equipment.Dumbbell),
                exerciseRow("3", "row", MuscleGroup.Back, Equipment.Barbell),
            ),
        )
    private val connectivity = FakeConnectivityObserver()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(selectMode: Boolean = false, savedState: SavedStateHandle = SavedStateHandle()) =
        ExercisePickerViewModel(
            LoadExerciseCatalog(repository, library),
            SearchExercises(),
            repository,
            connectivity,
            selectMode,
            savedState,
        )

    private fun TestScope.loaded(selectMode: Boolean = false): ExercisePickerViewModel =
        viewModel(selectMode).also { advanceUntilIdle() }

    private fun ExercisePickerViewModel.content() = assertIs<ExercisePickerUiState.Content>(uiState.value)

    @Test
    fun startsLoading_thenShowsAllExercisesSorted() = runTest {
        val vm = viewModel()
        assertEquals(ExercisePickerUiState.Loading, vm.uiState.value)

        advanceUntilIdle()

        assertEquals(
            listOf("Press de banca", "Press inclinado con mancuernas", "Remo con barra"),
            vm.content().visible.map { it.name },
        )
        assertEquals(3, vm.content().totalCount)
    }

    @Test
    fun queryAndFilters_recomputeTheList_andTodosClearsAFilter() = runTest {
        val vm = loaded()

        vm.onEvent(ExercisePickerEvent.SetQuery("press"))
        vm.onEvent(ExercisePickerEvent.SelectEquipment(Equipment.Dumbbell))
        assertEquals(listOf("Press inclinado con mancuernas"), vm.content().visible.map { it.name })

        vm.onEvent(ExercisePickerEvent.SelectEquipment(null))
        vm.onEvent(ExercisePickerEvent.SelectMuscleGroup(MuscleGroup.Back))
        assertTrue(vm.content().visible.isEmpty())

        vm.onEvent(ExercisePickerEvent.SelectMuscleGroup(null))
        assertEquals(2, vm.content().visible.size)
    }

    @Test
    fun loadError_showsErrorState_andRetryRecovers() = runTest {
        repository.failure = AppError.Network
        val vm = loaded()
        assertEquals(ExercisePickerUiState.Error, vm.uiState.value)

        repository.failure = null
        vm.onEvent(ExercisePickerEvent.Retry)
        advanceUntilIdle()

        assertEquals(3, vm.content().visible.size)
    }

    @Test
    fun offline_disablesCreate_andDoesNotOpenTheSheet() = runTest {
        val vm = loaded()
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(vm.content().canCreate)
        vm.onEvent(ExercisePickerEvent.OpenCreate)
        assertEquals(null, vm.content().createForm)
    }

    @Test
    fun openCreate_prefillsTheNameWithTheQuery_andNeedsGroupAndEquipment() = runTest {
        val vm = loaded()
        vm.onEvent(ExercisePickerEvent.SetQuery("Press Hammer unilateral"))

        vm.onEvent(ExercisePickerEvent.OpenCreate)

        val form = assertIs<CreateExerciseForm>(vm.content().createForm)
        assertEquals("Press Hammer unilateral", form.name)
        assertFalse(form.canSubmit)
        vm.onEvent(ExercisePickerEvent.SelectCreateMuscleGroup(MuscleGroup.Chest))
        assertFalse(vm.content().createForm!!.canSubmit)
        vm.onEvent(ExercisePickerEvent.SelectCreateEquipment(Equipment.Machine))
        assertTrue(vm.content().createForm!!.canSubmit)
    }

    @Test
    fun blankName_keepsCreateDisabled() = runTest {
        val vm = loaded()
        vm.onEvent(ExercisePickerEvent.OpenCreate)
        vm.onEvent(ExercisePickerEvent.SelectCreateMuscleGroup(MuscleGroup.Chest))
        vm.onEvent(ExercisePickerEvent.SelectCreateEquipment(Equipment.Machine))
        vm.onEvent(ExercisePickerEvent.SetCreateName("   "))

        assertFalse(vm.content().createForm!!.canSubmit)
        vm.onEvent(ExercisePickerEvent.SubmitCreate)
        advanceUntilIdle()
        assertEquals(0, repository.createCalls)
    }

    @Test
    fun createInSelectMode_closesTheSheet_andEmitsSelected() = runTest {
        val vm = loaded(selectMode = true)
        vm.effects.test {
            fillAndSubmit(vm)

            assertEquals(ExercisePickerEffect.Selected("custom-1"), awaitItem())
        }
        assertEquals(null, vm.content().createForm)
        assertEquals(Triple("Press Hammer", MuscleGroup.Chest, Equipment.Machine), repository.lastCreated)
        assertTrue(vm.content().visible.any { it.name == "Press Hammer" })
    }

    @Test
    fun refresh_dropsAnExerciseDeletedElsewhere() = runTest {
        val vm = loaded()
        val victim = vm.content().visible.first()
        repository.rows = repository.rows.filter { it.id != victim.id }

        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.content().visible.none { it.id == victim.id })
    }

    @Test
    fun createInBrowseMode_emitsOpenDetail() = runTest {
        val vm = loaded(selectMode = false)
        vm.effects.test {
            fillAndSubmit(vm)

            assertEquals(ExercisePickerEffect.OpenDetail("custom-1"), awaitItem())
        }
    }

    @Test
    fun createFailure_keepsTheSheetOpenWithAnError() = runTest {
        repository.createFailure = AppError.Network
        val vm = loaded()

        fillAndSubmit(vm)

        val form = assertIs<CreateExerciseForm>(vm.content().createForm)
        assertFalse(form.isSubmitting)
        assertEquals("Press Hammer", form.name)
    }

    @Test
    fun createFailure_emitsCreateFailed() = runTest {
        repository.createFailure = AppError.Network
        val vm = loaded()
        vm.effects.test {
            fillAndSubmit(vm)

            assertEquals(ExercisePickerEffect.CreateFailed, awaitItem())
        }
    }

    @Test
    fun thumbnailTap_alwaysOpensTheDetail_evenInSelectMode() = runTest {
        val select = loaded(selectMode = true)
        select.effects.test {
            select.onEvent(ExercisePickerEvent.OpenDetail("1"))
            assertEquals(ExercisePickerEffect.OpenDetail("1"), awaitItem())
        }
    }

    @Test
    fun rowTap_inSelectMode_emitsSelected_andInBrowseMode_opensTheDetail() = runTest {
        val select = loaded(selectMode = true)
        select.effects.test {
            select.onEvent(ExercisePickerEvent.OpenExercise("1"))
            assertEquals(ExercisePickerEffect.Selected("1"), awaitItem())
        }
        val browse = loaded(selectMode = false)
        browse.effects.test {
            browse.onEvent(ExercisePickerEvent.OpenExercise("1"))
            assertEquals(ExercisePickerEffect.OpenDetail("1"), awaitItem())
        }
    }

    @Test
    fun listOnlyContainsWhatTheRepositoryReturned() = runTest {
        repository.rows = listOf(exerciseRow("c1", null, name = "Mi ejercicio", custom = true))

        val vm = loaded()

        assertEquals(listOf("Mi ejercicio"), vm.content().visible.map { it.name })
    }

    private fun TestScope.fillAndSubmit(vm: ExercisePickerViewModel) {
        vm.onEvent(ExercisePickerEvent.OpenCreate)
        vm.onEvent(ExercisePickerEvent.SetCreateName("Press Hammer"))
        vm.onEvent(ExercisePickerEvent.SelectCreateMuscleGroup(MuscleGroup.Chest))
        vm.onEvent(ExercisePickerEvent.SelectCreateEquipment(Equipment.Machine))
        vm.onEvent(ExercisePickerEvent.SubmitCreate)
        advanceUntilIdle()
    }

    // -- T-050: drafts survive process death --------------------------------------------------------------------

    @Test
    fun searchAndFilters_surviveARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(savedState = handle).also { advanceUntilIdle() }
        first.onEvent(ExercisePickerEvent.SetQuery("press"))
        first.onEvent(ExercisePickerEvent.SelectEquipment(Equipment.Dumbbell))
        first.onEvent(ExercisePickerEvent.SelectMuscleGroup(MuscleGroup.Chest))

        val second = viewModel(savedState = handle).also { advanceUntilIdle() }

        assertEquals("press", second.content().query)
        assertEquals(Equipment.Dumbbell, second.content().equipment)
        assertEquals(MuscleGroup.Chest, second.content().muscleGroup)
        assertEquals(listOf("Press inclinado con mancuernas"), second.content().visible.map { it.name })
    }

    @Test
    fun anOpenCreateForm_survivesARecreatedViewModel_andIsNotSubmitted() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(savedState = handle).also { advanceUntilIdle() }
        first.onEvent(ExercisePickerEvent.SetQuery("Curl"))
        first.onEvent(ExercisePickerEvent.OpenCreate)
        first.onEvent(ExercisePickerEvent.SetCreateName("Curl nórdico"))
        first.onEvent(ExercisePickerEvent.SelectCreateMuscleGroup(MuscleGroup.Back))

        val second = viewModel(savedState = handle).also { advanceUntilIdle() }

        val form = second.content().createForm
        assertEquals("Curl nórdico", form?.name)
        assertEquals(MuscleGroup.Back, form?.muscleGroup)
        assertEquals(null, form?.equipment)
        assertEquals(false, form?.isSubmitting)
        assertEquals(0, repository.createCalls)
    }

    @Test
    fun theCreateForm_defaultsToWeightAndReps() = runTest {
        val vm = loaded()

        vm.onEvent(ExercisePickerEvent.OpenCreate)

        assertEquals(ExerciseMetricType.WeightReps, vm.content().createForm?.metricType)
    }

    @Test
    fun aChosenMetricType_isSentWithTheCreateRequest_andTheCreatedExerciseKeepsIt() = runTest {
        val vm = loaded()
        vm.onEvent(ExercisePickerEvent.OpenCreate)
        vm.onEvent(ExercisePickerEvent.SetCreateName("Plancha lastrada"))
        vm.onEvent(ExercisePickerEvent.SelectCreateMuscleGroup(MuscleGroup.Quads))
        vm.onEvent(ExercisePickerEvent.SelectCreateEquipment(Equipment.Bodyweight))
        vm.onEvent(ExercisePickerEvent.SelectCreateMetricType(ExerciseMetricType.Duration))

        vm.onEvent(ExercisePickerEvent.SubmitCreate)
        advanceUntilIdle()

        assertEquals(ExerciseMetricType.Duration, repository.lastCreatedMetricType)
        assertEquals(
            ExerciseMetricType.Duration,
            repository.rows.single { it.name == "Plancha lastrada" }.metricType,
        )
    }

    @Test
    fun thePerHandFlag_defaultsToOff_isSentWithTheCreateRequest_andTheCreatedExerciseKeepsIt() = runTest {
        val vm = loaded()
        vm.onEvent(ExercisePickerEvent.OpenCreate)
        assertFalse(vm.content().createForm!!.perHand)
        vm.onEvent(ExercisePickerEvent.SetCreateName("Press Hammer"))
        vm.onEvent(ExercisePickerEvent.SelectCreateMuscleGroup(MuscleGroup.Chest))
        vm.onEvent(ExercisePickerEvent.SelectCreateEquipment(Equipment.Machine))
        vm.onEvent(ExercisePickerEvent.SetCreatePerHand(true))

        vm.onEvent(ExercisePickerEvent.SubmitCreate)
        advanceUntilIdle()

        assertEquals(true, repository.lastCreatedPerHand)
        assertTrue(repository.rows.single { it.name == "Press Hammer" }.perHand)
    }

    @Test
    fun thePerHandFlag_survivesARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(savedState = handle).also { advanceUntilIdle() }
        first.onEvent(ExercisePickerEvent.OpenCreate)
        first.onEvent(ExercisePickerEvent.SetCreatePerHand(true))

        val second = viewModel(savedState = handle).also { advanceUntilIdle() }

        assertTrue(second.content().createForm!!.perHand)
    }

    @Test
    fun aChosenMetricType_survivesARecreatedViewModel() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(savedState = handle).also { advanceUntilIdle() }
        first.onEvent(ExercisePickerEvent.OpenCreate)
        first.onEvent(ExercisePickerEvent.SelectCreateMetricType(ExerciseMetricType.DistanceDuration))

        val second = viewModel(savedState = handle).also { advanceUntilIdle() }

        assertEquals(ExerciseMetricType.DistanceDuration, second.content().createForm?.metricType)
    }

    @Test
    fun aDismissedCreateForm_isNotRestored() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(savedState = handle).also { advanceUntilIdle() }
        first.onEvent(ExercisePickerEvent.SetQuery("Curl"))
        first.onEvent(ExercisePickerEvent.OpenCreate)
        first.onEvent(ExercisePickerEvent.DismissCreate)

        val second = viewModel(savedState = handle).also { advanceUntilIdle() }

        assertEquals(null, second.content().createForm)
    }
}
