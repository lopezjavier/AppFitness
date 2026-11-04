package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.usecase.DaysRow
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.FolderReviewCalculator
import io.github.lopezjavier.appfitness.domain.usecase.FolderReviewResult
import io.github.lopezjavier.appfitness.domain.usecase.GroupRow
import io.github.lopezjavier.appfitness.domain.usecase.OrderOffender
import io.github.lopezjavier.appfitness.domain.usecase.RepOffender
import io.github.lopezjavier.appfitness.domain.usecase.StrengthOrderRow
import io.github.lopezjavier.appfitness.domain.usecase.StrengthRepsRow
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class FolderReviewResultViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val routines = FakeRoutineRepository().apply { folders += RoutineFolder("folder-1", "Fuerza") }
    private val exercises =
        FakeExerciseRepository(listOf(exerciseRow("id-bench", "bench"), exerciseRow("id-row", "row")))
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press banca"), "row" to exerciseInfo("row", "Remo")),
    )
    private val asked = mutableListOf<String>()
    private var answer: Result<FolderReviewResult> = Result.success(result())

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun result(
        score: Double = 7.5,
        rows: List<io.github.lopezjavier.appfitness.domain.usecase.ChecklistRow> = FolderReviewCalculator.GROUPS.map {
            GroupRow(it, 12.0)
        } + DaysRow(3),
        routineCount: Int = 3,
        strength: Boolean = false,
    ) = FolderReviewResult(score, rows, rows.filter { !it.met }, liftingRoutines = 3, routineCount, strength)

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle(mapOf("folderId" to "folder-1"))) =
        FolderReviewResultViewModel(
            review = {
                asked += it
                answer
            },
            names = ExerciseNameResolver(exercises, library),
            routines = routines,
            savedState = saved,
        )

    private fun FolderReviewResultViewModel.content() = assertIs<FolderReviewResultUiState.Content>(uiState.value)

    @Test
    fun creation_computesTheReviewOfTheRouteFolderAndShowsTheScoreAndRowsInOrder() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(FolderReviewResultUiState.Loading, model.uiState.value)
        dispatcher.scheduler.advanceUntilIdle()

        val content = model.content()
        assertEquals(listOf("folder-1"), asked)
        assertEquals(7.5, content.score)
        assertEquals("Fuerza", content.folderName)
        assertEquals(7, content.items.size)
        assertEquals(MuscleGroup.Chest, (content.items.first() as ReviewItem.Group).group)
        assertIs<ReviewItem.Days>(content.items.last())
    }

    @Test
    fun over_isMappedFromTheGroupRow() = runTest(dispatcher) {
        answer = Result.success(
            result(rows = listOf(GroupRow(MuscleGroup.Back, 25.0), GroupRow(MuscleGroup.Chest, 12.0))),
        )
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val (back, chest) = model.content().items.map { it as ReviewItem.Group }
        assertEquals(true, back.over)
        assertEquals(false, chest.over)
    }

    @Test
    fun anUnmetGroup_carriesTheSetsAndTheMissingAmount() = runTest(dispatcher) {
        answer =
            Result.success(result(rows = listOf(GroupRow(MuscleGroup.Back, 7.5), GroupRow(MuscleGroup.Chest, 0.0))))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val (back, chest) = model.content().items.map { it as ReviewItem.Group }
        assertEquals(3, back.missing)
        assertEquals(false, back.met)
        assertEquals(true, chest.none)
    }

    @Test
    fun strengthRows_resolveTheExerciseNames() = runTest(dispatcher) {
        val reps = StrengthRepsRow(listOf(RepOffender("Torso", "id-bench", 10, 12)))
        val order = StrengthOrderRow(listOf(OrderOffender("Torso", "id-bench", "id-row", listOf("id-bench"))))
        answer = Result.success(result(rows = listOf(reps, order), strength = true))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val (repsItem, orderItem) = model.content().items
        assertEquals(listOf(RepLine("Torso", "Press banca", 10, 12)), (repsItem as ReviewItem.Reps).lines)
        assertEquals(
            listOf(OrderLine("Torso", "Press banca", "Remo", listOf("Press banca"))),
            (orderItem as ReviewItem.Order).lines,
        )
    }

    @Test
    fun aFolderWithoutRoutines_isEmptyAndShowsNoScore() = runTest(dispatcher) {
        answer = Result.success(result(score = 0.0, routineCount = 0))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FolderReviewResultUiState.Empty, model.uiState.value)
    }

    @Test
    fun aFailedLoad_isAnError_andRetryComputesAgain() = runTest(dispatcher) {
        answer = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FolderReviewResultUiState.Error, model.uiState.value)

        answer = Result.success(result())
        model.onEvent(FolderReviewResultEvent.Retry)
        dispatcher.scheduler.advanceUntilIdle()

        model.content()
        assertEquals(2, asked.size)
    }

    @Test
    fun aMissingExerciseName_failsInsteadOfInventingOne() = runTest(dispatcher) {
        answer =
            Result.success(result(rows = listOf(StrengthRepsRow(listOf(RepOffender("Torso", "id-unknown", 10, 12))))))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FolderReviewResultUiState.Error, model.uiState.value)
    }

    @Test
    fun aRecreatedModel_withTheSameSavedState_recomputesTheSameFolderWithoutTheNetwork() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("folderId" to "folder-1"))
        viewModel(saved)
        dispatcher.scheduler.advanceUntilIdle()

        val second = viewModel(saved)
        dispatcher.scheduler.advanceUntilIdle()

        second.content()
        assertEquals(listOf("folder-1", "folder-1"), asked)
    }

    @Test
    fun theScoreBands_changeAtFiveEightAndTen() {
        assertEquals(ScoreBand.Poor, ScoreBand.of(4.9))
        assertEquals(ScoreBand.Fair, ScoreBand.of(5.0))
        assertEquals(ScoreBand.Fair, ScoreBand.of(7.9))
        assertEquals(ScoreBand.Good, ScoreBand.of(8.0))
        assertEquals(ScoreBand.Good, ScoreBand.of(9.9))
        assertEquals(ScoreBand.Complete, ScoreBand.of(10.0))
    }
}
