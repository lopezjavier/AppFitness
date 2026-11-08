package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-242 (US-106): the Trainer reads the pause of the saved block together with its weeks. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerPauseViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val plans = FakeCoachPlanRepository().apply {
        current = Result.success(coachPlan(status = PlanStatus.Accepted))
    }
    private val blocks = FakeCoachBlockRepository().apply { current = Result.success(coachBlock(saved = true)) }
    private val weeks = FakeCoachWeeksRepository().apply { weeksResult = Result.success(listOf(planWeek(1))) }
    private val pausedBlock = BlockPause(paused = true, pausedWeeks = 1, blockEnd = LocalDate(2026, 12, 6))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = TrainerViewModel(
        FakeCoachRepository(),
        MutableClock(),
        plans = plans,
        blocks = blocks,
        coachWeeks = weeks,
        savedState = saved,
    )

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    @Test
    fun pause_isLoadedWithTheWeeks() = runTest(dispatcher) {
        weeks.pauseResult = Result.success(pausedBlock)
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(pausedBlock, model.content().pause)
        assertEquals(listOf(1), model.content().weeks.map { it.week })
        assertEquals(1, weeks.pauseReads.size)
    }

    @Test
    fun resumedBlock_keepsThePausedWeeksAndTheMovedEnd() = runTest(dispatcher) {
        val resumed = pausedBlock.copy(paused = false)
        weeks.pauseResult = Result.success(resumed)
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(resumed, model.content().pause)
    }

    @Test
    fun failedPauseRead_hidesThePauseButNotTheWeeks() = runTest(dispatcher) {
        weeks.pauseResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertNull(model.content().pause)
        assertEquals(listOf(1), model.content().weeks.map { it.week })
    }

    @Test
    fun withoutASavedBlock_thePauseIsNotRead() = runTest(dispatcher) {
        blocks.current = Result.success(coachBlock(saved = false))
        val model = viewModel()

        advanceUntilIdle()

        assertNull(model.content().pause)
        assertTrue(weeks.pauseReads.isEmpty())
    }

    @Test
    fun pausedState_neverWritesNutrition() = runTest(dispatcher) {
        weeks.pauseResult = Result.success(pausedBlock)
        val model = viewModel()

        advanceUntilIdle()

        assertNull(model.content().calorieProposal)
        assertTrue(plans.decisions.isEmpty())
        assertTrue(plans.cardioCalls.isEmpty())
        assertTrue(plans.acceptCalls.isEmpty())
    }

    @Test
    fun recreatedModel_readsThePauseAgainFromTheServer() = runTest(dispatcher) {
        weeks.pauseResult = Result.success(pausedBlock)
        val saved = SavedStateHandle()
        viewModel(saved)
        advanceUntilIdle()

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertEquals(pausedBlock, recreated.content().pause)
    }

    @Test
    fun refresh_picksUpTheResumedBlock() = runTest(dispatcher) {
        weeks.pauseResult = Result.success(pausedBlock)
        val model = viewModel()
        advanceUntilIdle()
        weeks.pauseResult = Result.success(pausedBlock.copy(paused = false))

        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertEquals(false, model.content().pause?.paused)
        assertEquals(1, model.content().pause?.pausedWeeks)
    }
}
