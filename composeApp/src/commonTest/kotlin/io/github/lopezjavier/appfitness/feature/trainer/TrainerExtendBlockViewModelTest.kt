package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** T-274 (US-134): "Cambiar el último día del bloque" from the Trainer. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerExtendBlockViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val plans = FakeCoachPlanRepository().apply {
        current = Result.success(coachPlan(status = PlanStatus.Accepted))
    }
    private val blocks = FakeCoachBlockRepository().apply { current = Result.success(coachBlock(saved = true)) }
    private val weeks = FakeCoachWeeksRepository().apply { weeksResult = Result.success(listOf(planWeek(1))) }
    private val original = LocalDate(2026, 11, 15)
    private val running = BlockPause(
        paused = false,
        pausedWeeks = 0,
        blockEnd = original,
        extensionDays = 0,
        minEnd = LocalDate(2026, 10, 19),
        maxEnd = LocalDate(2026, 11, 29),
        extendable = true,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        weeks.pauseResult = Result.success(running)
    }

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

    private fun extended(end: LocalDate) =
        running.copy(blockEnd = end, extensionDays = (end.toEpochDays() - original.toEpochDays()).toInt())

    @Test
    fun pickWithinLimits_callsTheRepositoryAndShowsTheNewEnd() = runTest(dispatcher) {
        val newEnd = LocalDate(2026, 11, 18)
        weeks.setBlockEndResult = Result.success(extended(newEnd))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenExtendPicker)

        model.onEvent(TrainerEvent.ExtendBlock(newEnd))
        advanceUntilIdle()

        assertEquals(listOf(Triple("plan-1", newEnd, LocalDate(2026, 10, 19))), weeks.blockEndCalls)
        assertEquals(newEnd, model.content().pause?.blockEnd)
        assertEquals(3, model.content().pause?.extensionDays)
        assertFalse(model.content().extendPickerOpen)
        assertFalse(model.content().extendingBlock)
    }

    @Test
    fun success_rereadsTheWeeksAndEmitsTheEffect() = runTest(dispatcher) {
        weeks.setBlockEndResult = Result.success(extended(LocalDate(2026, 11, 18)))
        val model = viewModel()
        advanceUntilIdle()
        val readsBefore = weeks.weeksReads.size

        model.effects.test {
            model.onEvent(TrainerEvent.ExtendBlock(LocalDate(2026, 11, 18)))
            advanceUntilIdle()

            assertEquals(TrainerEffect.BlockEndChanged, awaitItem())
        }
        assertEquals(readsBefore + 1, weeks.weeksReads.size)
    }

    @Test
    fun pickingTheOriginalEnd_isSentAndRemovesTheExtension() = runTest(dispatcher) {
        weeks.pauseResult = Result.success(extended(LocalDate(2026, 11, 18)))
        weeks.setBlockEndResult = Result.success(running)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.ExtendBlock(original))
        advanceUntilIdle()

        assertEquals(1, weeks.blockEndCalls.size)
        assertEquals(original, model.content().pause?.blockEnd)
        assertEquals(0, model.content().pause?.extensionDays)
    }

    @Test
    fun pickOutsideTheLimits_isNotSent() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenExtendPicker)

        model.onEvent(TrainerEvent.ExtendBlock(LocalDate(2026, 10, 18)))
        model.onEvent(TrainerEvent.ExtendBlock(LocalDate(2026, 11, 30)))
        advanceUntilIdle()

        assertTrue(weeks.blockEndCalls.isEmpty())
        assertEquals(original, model.content().pause?.blockEnd)
        assertFalse(model.content().extendPickerOpen)
    }

    @Test
    fun limitsAreInclusive() = runTest(dispatcher) {
        weeks.setBlockEndResult = Result.success(extended(LocalDate(2026, 11, 29)))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.ExtendBlock(LocalDate(2026, 11, 29)))
        advanceUntilIdle()
        model.onEvent(TrainerEvent.ExtendBlock(LocalDate(2026, 10, 19)))
        advanceUntilIdle()

        assertEquals(listOf(LocalDate(2026, 11, 29), LocalDate(2026, 10, 19)), weeks.blockEndCalls.map { it.second })
    }

    @Test
    fun notExtendable_ignoresPickerAndPick() = runTest(dispatcher) {
        weeks.pauseResult = Result.success(running.copy(extendable = false, minEnd = null, maxEnd = null))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.OpenExtendPicker)
        model.onEvent(TrainerEvent.ExtendBlock(LocalDate(2026, 11, 18)))
        advanceUntilIdle()

        assertFalse(model.content().extendPickerOpen)
        assertTrue(weeks.blockEndCalls.isEmpty())
    }

    @Test
    fun repositoryError_keepsTheOldEndAndEmitsTheFailure() = runTest(dispatcher) {
        weeks.setBlockEndResult = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.ExtendBlock(LocalDate(2026, 11, 18)))
            advanceUntilIdle()

            assertEquals(TrainerEffect.BlockEndFailed(offline = false), awaitItem())
        }
        assertEquals(original, model.content().pause?.blockEnd)
        assertFalse(model.content().extendingBlock)
        assertFalse(model.content().extendPickerOpen)
    }

    @Test
    fun networkError_isFlaggedOffline() = runTest(dispatcher) {
        weeks.setBlockEndResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.ExtendBlock(LocalDate(2026, 11, 18)))
            advanceUntilIdle()

            assertEquals(TrainerEffect.BlockEndFailed(offline = true), awaitItem())
        }
    }

    @Test
    fun whileInFlight_busyIsSetAndASecondPickIsIgnored() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        weeks.blockEndGate = gate
        weeks.setBlockEndResult = Result.success(extended(LocalDate(2026, 11, 18)))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.ExtendBlock(LocalDate(2026, 11, 18)))
        advanceUntilIdle()
        assertTrue(model.content().extendingBlock)
        model.onEvent(TrainerEvent.ExtendBlock(LocalDate(2026, 11, 20)))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, weeks.blockEndCalls.size)
        assertFalse(model.content().extendingBlock)
    }

    @Test
    fun openAndDismissPicker_toggleTheState() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.OpenExtendPicker)
        assertTrue(model.content().extendPickerOpen)
        model.onEvent(TrainerEvent.DismissExtendPicker)

        assertFalse(model.content().extendPickerOpen)
        assertTrue(weeks.blockEndCalls.isEmpty())
    }

    @Test
    fun recreatedModel_restoresTheOpenPicker() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.OpenExtendPicker)

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertTrue(recreated.content().extendPickerOpen)
    }

    @Test
    fun recreatedModel_afterDismiss_hasNoPicker() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.OpenExtendPicker)
        first.onEvent(TrainerEvent.DismissExtendPicker)

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertFalse(recreated.content().extendPickerOpen)
    }

    @Test
    fun restoredPicker_isDroppedWhenTheBlockIsNoLongerExtendable() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.OpenExtendPicker)
        weeks.pauseResult = Result.success(running.copy(extendable = false))

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertFalse(recreated.content().extendPickerOpen)
    }

    @Test
    fun refresh_keepsTheOpenPicker() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenExtendPicker)

        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertTrue(model.content().extendPickerOpen)
    }
}
