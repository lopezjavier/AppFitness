package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.model.BlockCardio
import io.github.lopezjavier.appfitness.domain.model.BlockEdit
import io.github.lopezjavier.appfitness.domain.model.BlockExercise
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.ExerciseAlternative
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun cardioExercise(id: String, minutes: Int = 50) = BlockExercise(
    exerciseId = id,
    exerciseKey = "key-$id",
    role = "cardio",
    muscleGroup = "cardio",
    sets = 1,
    repMin = 0,
    repMax = 0,
    restSeconds = 0,
    reason = "Cardio suave.",
    durationMin = minutes,
    pace = "suave",
)

/** T-183: every workout is [ex-1 (lifting), bike (cardio)]; 300 min a week means 60 each plus 120 on your own. */
@OptIn(ExperimentalCoroutinesApi::class)
class BlockPreviewCardioTest {
    private val dispatcher = StandardTestDispatcher()
    private val blocks = FakeCoachBlockRepository()
    private val exercises = FakeExerciseRepository(
        listOf(
            exerciseRow("ex-1", "bench"),
            exerciseRow("bike", "bike"),
            exerciseRow("incline", "incline"),
            exerciseRow("walk", "walk"),
        ),
    ).apply {
        alternativesById = mapOf(
            "bike" to listOf(ExerciseAlternative(rows[2], 0.5), ExerciseAlternative(rows[3], 0.4)),
            "ex-1" to emptyList(),
        )
    }
    private val library = FakeExerciseLibrary(
        mapOf(
            "bench" to exerciseInfo("bench", "Press banca"),
            "bike" to exerciseInfo("bike", "Bicicleta"),
            "incline" to exerciseInfo("incline", "Cinta inclinada"),
            "walk" to exerciseInfo("walk", "Caminar a paso ligero"),
        ),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun cardioBlock(onYourOwn: Int = 120, withCardio: Boolean = true): CoachBlock {
        val base = coachBlock()
        return if (!withCardio) {
            base
        } else {
            base.copy(
                training = base.training.copy(
                    cardio = BlockCardio(60 * 3 + onYourOwn, 180, onYourOwn, listOf(60, 60, 60), "suave"),
                    routineTotalMin = listOf(120, 120, 120),
                ),
                sessions = base.sessions.map {
                    it.copy(exercises = listOf(blockExercise("ex-1"), cardioExercise("bike", 60)))
                },
            )
        }
    }

    private fun viewModel() =
        BlockPreviewViewModel("plan-1", blocks, exercises, library, MutableClock(), SavedStateHandle())

    private fun BlockPreviewViewModel.preview() = assertIs<BlockPreviewUiState.Preview>(uiState.value)

    @Test
    fun theBlockExposesCardioLastWithMinutes_theRoutineTotalsAndTheOwnMinutes() = runTest(dispatcher) {
        blocks.current = Result.success(cardioBlock())
        val model = viewModel()
        advanceUntilIdle()

        val last = model.preview().sessions.first().exercises.last()
        assertTrue(last.isCardio)
        assertEquals(60, last.durationMin)
        assertEquals(listOf(120, 120, 120), model.preview().block.training.routineTotalMin)
        assertEquals(120, model.preview().block.training.cardio?.onYourOwnMin)
    }

    @Test
    fun aBlockWithoutCardio_hasNoCardioPlan() = runTest(dispatcher) {
        blocks.current = Result.success(cardioBlock(withCardio = false))
        val model = viewModel()
        advanceUntilIdle()

        assertNull(model.preview().block.training.cardio)
        assertTrue(model.preview().sessions.flatMap { it.exercises }.none { it.isCardio })
    }

    @Test
    fun theCardioSlotIsSwappable_evenWhenAlternativesAreStillLoading() = runTest(dispatcher) {
        blocks.current = Result.success(cardioBlock())
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.preview().canSwap(0, 1))
        assertFalse(model.preview().canSwap(0, 0))
    }

    @Test
    fun openingTheCardioSlot_flagsACardioSwap_andListsTheOtherCardioWithoutRanking() = runTest(dispatcher) {
        blocks.current = Result.success(cardioBlock())
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.OpenReplace(0, 1))
        advanceUntilIdle()

        assertTrue(model.preview().isCardioSwap)
        val rows = assertIs<AlternativesState.Content>(model.preview().alternatives).rows
        assertEquals(listOf("Caminar a paso ligero", "Cinta inclinada"), rows.map { it.exercise.name })
    }

    @Test
    fun openingALiftingSlot_isNotACardioSwap() = runTest(dispatcher) {
        blocks.current = Result.success(cardioBlock())
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.OpenReplace(0, 0))
        advanceUntilIdle()

        assertFalse(model.preview().isCardioSwap)
    }

    @Test
    fun swappingTheCardio_keepsMinutesAndPositionInEveryWeek_andSavesAWeekOneEdit() = runTest(dispatcher) {
        blocks.current = Result.success(cardioBlock())
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(BlockPreviewEvent.OpenReplace(0, 1))
        advanceUntilIdle()

        model.onEvent(BlockPreviewEvent.PickReplacement("walk"))
        advanceUntilIdle()

        model.preview().weeks.forEach { week ->
            val swapped = week.sessions.first { it.sessionIndex == 0 }.exercises.last()
            assertEquals("walk", swapped.exerciseId)
            assertEquals(60, swapped.durationMin)
            assertTrue(swapped.isCardio)
        }
        assertTrue(model.preview().isChanged(0, 1))
        model.onEvent(BlockPreviewEvent.Save)
        advanceUntilIdle()
        assertEquals(listOf(listOf(BlockEdit(0, 2, "walk"))), blocks.saveEdits)
    }
}
