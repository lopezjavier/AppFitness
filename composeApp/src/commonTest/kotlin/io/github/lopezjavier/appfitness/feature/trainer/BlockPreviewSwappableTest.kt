package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.FakeFavoriteExercisesRepository
import io.github.lopezjavier.appfitness.feature.exercises.SimilarExerciseRow
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-180: the swap button is hidden on an exercise with no alternative to swap to. Every workout is [ex-1, ex-2]. */
@OptIn(ExperimentalCoroutinesApi::class)
class BlockPreviewSwappableTest {
    private val favorites = FakeFavoriteExercisesRepository()
    private val dispatcher = StandardTestDispatcher()
    private val blocks = FakeCoachBlockRepository().apply { current = Result.success(coachBlock()) }
    private val exercises = FakeExerciseRepository(
        listOf(
            exerciseRow("ex-1", "bench"),
            exerciseRow("ex-2", "row"),
            exerciseRow("ex-3", "fly", equipment = Equipment.Cable),
        ),
    ).apply {
        alternativesById = mapOf("ex-1" to listOf(ExerciseAlternative(rows[2], 0.8)))
    }
    private val library = FakeExerciseLibrary(
        mapOf(
            "bench" to exerciseInfo("bench", "Press banca"),
            "row" to exerciseInfo("row", "Remo"),
            "fly" to exerciseInfo("fly", "Aperturas"),
        ),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = BlockPreviewViewModel(
        "plan-1",
        blocks,
        exercises,
        library,
        FakeTrainingPreferencesRepository(),
        favorites,
        saved,
    )

    private fun BlockPreviewViewModel.preview() = assertIs<BlockPreviewUiState.Preview>(uiState.value)

    private fun TestScope.swapFirst(model: BlockPreviewViewModel) {
        model.onEvent(BlockPreviewEvent.OpenReplace(0, 0))
        advanceUntilIdle()
        model.onEvent(BlockPreviewEvent.PickReplacement("ex-3"))
        advanceUntilIdle()
    }

    @Test
    fun isUnknownWhileTheAlternativesLoad_thenListsOnlySlotsWithAlternatives() = runTest(dispatcher) {
        exercises.alternativesGate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        assertNull(model.preview().swappable)
        assertTrue(model.preview().canSwap(0, 1))

        exercises.alternativesGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(setOf("0/0", "1/0", "2/0"), model.preview().swappable)
        assertTrue(model.preview().canSwap(1, 0))
        assertFalse(model.preview().canSwap(1, 1))
    }

    @Test
    fun fetchesEachDistinctExerciseOnce() = runTest(dispatcher) {
        viewModel()
        advanceUntilIdle()

        assertEquals(listOf("ex-1", "ex-2"), exercises.alternativesCalls.sorted())
    }

    @Test
    fun anExerciseWhoseOnlyAlternativesAreInTheSession_isNotSwappable() = runTest(dispatcher) {
        exercises.alternativesById = mapOf("ex-1" to listOf(ExerciseAlternative(exercises.rows[1], 0.9)))
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(emptySet(), model.preview().swappable)
        assertFalse(model.preview().canSwap(0, 0))
    }

    @Test
    fun aFailedRead_keepsTheButton() = runTest(dispatcher) {
        exercises.alternativesFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.preview().canSwap(0, 0))
        assertTrue(model.preview().canSwap(0, 1))
    }

    @Test
    fun aSlotThatWasSwapped_staysSwappableToGoBack() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        swapFirst(model)

        assertTrue(model.preview().isChanged(0, 0))
        assertTrue(model.preview().canSwap(0, 0))
        assertFalse(model.preview().canSwap(0, 1))
    }

    private fun TestScope.swapRows(): List<SimilarExerciseRow> {
        val extra = exerciseRow("ex-4", "press", equipment = Equipment.Cable)
        exercises.rows = exercises.rows + extra
        library.entries = library.entries + ("press" to exerciseInfo("press", "Cruces"))
        exercises.alternativesById = mapOf(
            "ex-1" to listOf(ExerciseAlternative(exercises.rows[2], 0.8), ExerciseAlternative(extra, 0.9)),
        )
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(BlockPreviewEvent.OpenReplace(0, 0))
        advanceUntilIdle()
        return assertIs<AlternativesState.Content>(model.preview().alternatives).rows
    }

    @Test
    fun swapList_withoutFavourites_isBySimilarityThenName() = runTest(dispatcher) {
        assertEquals(listOf("ex-4", "ex-3"), swapRows().map { it.exercise.id })
    }

    @Test
    fun swapList_putsAFavouriteFirst_andMarksIt() = runTest(dispatcher) {
        favorites.set(setOf("ex-3"))

        val rows = swapRows()

        assertEquals(listOf("ex-3", "ex-4"), rows.map { it.exercise.id })
        assertEquals(listOf(true, false), rows.map { it.isFavorite })
    }

    @Test
    fun swapList_neverShowsAFavouriteThatIsNotAnAlternative() = runTest(dispatcher) {
        favorites.set(setOf("ex-2", "ex-99"))

        assertEquals(listOf("ex-4", "ex-3"), swapRows().map { it.exercise.id })
    }

    @Test
    fun aRecreatedModel_recomputesIt_andKeepsThePendingSwap() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        swapFirst(first)

        val second = viewModel(saved)
        advanceUntilIdle()

        assertTrue(second.preview().isChanged(0, 0))
        assertEquals(setOf("0/0", "1/0", "2/0"), second.preview().swappable)
    }
}
