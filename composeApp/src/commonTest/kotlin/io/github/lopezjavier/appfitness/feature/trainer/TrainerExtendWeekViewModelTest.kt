package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-276 (US-134): "Cambiar el último día de la semana" from the Trainer's active week. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerExtendWeekViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val plans = FakeCoachPlanRepository().apply {
        current = Result.success(coachPlan(status = PlanStatus.Accepted))
    }
    private val blocks = FakeCoachBlockRepository().apply { current = Result.success(coachBlock(saved = true)) }
    private val weeks = FakeCoachWeeksRepository()

    // Week 3 is the active one (Nov 2 to Nov 8); the block ends Nov 22 and the server accepts up to Dec 6.
    private val weekEnd = LocalDate(2026, 11, 8)
    private val original = LocalDate(2026, 11, 22)
    private val activeWeek = planWeek(3)
    private val running = BlockPause(
        paused = false,
        pausedWeeks = 0,
        blockEnd = original,
        extensionDays = 0,
        minEnd = original,
        maxEnd = LocalDate(2026, 12, 6),
        extendable = true,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        weeks.weeksResult = Result.success(listOf(planWeek(2, WeekPhase.Past), activeWeek))
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

    private fun day(dayOfNovember: Int) = LocalDate(2026, 11, dayOfNovember)

    /** The server's state once the active week ends on [end]: the whole block moves by the same days. */
    private fun extended(end: LocalDate): Pair<BlockPause, List<PlanWeek>> {
        val days = (end.toEpochDays() - weekEnd.toEpochDays()).toInt()
        val newEnd = LocalDate.fromEpochDays(original.toEpochDays() + days)
        return running.copy(blockEnd = newEnd, extensionDays = days) to
            listOf(planWeek(2, WeekPhase.Past), activeWeek.copy(endDate = end))
    }

    @Test
    fun pick_sendsTheBlockEndMovedByTheSameDaysAndShowsTheNewWeek() = runTest(dispatcher) {
        val (pause, newWeeks) = extended(day(11))
        weeks.setBlockEndResult = Result.success(pause)
        val model = viewModel()
        advanceUntilIdle()
        weeks.weeksResult = Result.success(newWeeks) // What the re-read after the change returns.
        model.onEvent(TrainerEvent.OpenExtendWeekPicker)

        model.onEvent(TrainerEvent.ExtendWeek(day(11)))
        advanceUntilIdle()

        assertEquals(listOf(Triple("plan-1", day(25), LocalDate(2026, 10, 19))), weeks.blockEndCalls)
        assertEquals(day(25), model.content().pause?.blockEnd)
        assertEquals(day(11), model.content().weeks.first { it.phase == WeekPhase.Current }.endDate)
        assertFalse(model.content().extendWeekPickerOpen)
        assertFalse(model.content().extendingWeek)
    }

    @Test
    fun success_rereadsTheWeeksAndEmitsTheEffect() = runTest(dispatcher) {
        weeks.setBlockEndResult = Result.success(extended(day(11)).first)
        val model = viewModel()
        advanceUntilIdle()
        val readsBefore = weeks.weeksReads.size

        model.effects.test {
            model.onEvent(TrainerEvent.ExtendWeek(day(11)))
            advanceUntilIdle()

            assertEquals(TrainerEffect.WeekEndChanged, awaitItem())
        }
        assertEquals(readsBefore + 1, weeks.weeksReads.size)
    }

    @Test
    fun pickingTheWeeksOriginalLastDay_restoresTheBlockEnd() = runTest(dispatcher) {
        val (pause, newWeeks) = extended(day(11))
        weeks.pauseResult = Result.success(pause)
        weeks.weeksResult = Result.success(newWeeks)
        weeks.setBlockEndResult = Result.success(running)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.ExtendWeek(weekEnd))
        advanceUntilIdle()

        assertEquals(listOf(original), weeks.blockEndCalls.map { it.second })
        assertEquals(0, model.content().pause?.extensionDays)
    }

    @Test
    fun theWeeksOwnLastDayWithoutExtension_sendsTheUnchangedBlockEnd() = runTest(dispatcher) {
        weeks.setBlockEndResult = Result.success(running)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.ExtendWeek(weekEnd))
        advanceUntilIdle()

        assertEquals(listOf(original), weeks.blockEndCalls.map { it.second })
    }

    @Test
    fun pickOutsideTheLimits_isNotSent() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenExtendWeekPicker)

        model.onEvent(TrainerEvent.ExtendWeek(day(7)))
        model.onEvent(TrainerEvent.ExtendWeek(day(23)))
        advanceUntilIdle()

        assertTrue(weeks.blockEndCalls.isEmpty())
        assertEquals(original, model.content().pause?.blockEnd)
        assertFalse(model.content().extendWeekPickerOpen)
    }

    @Test
    fun limitsAreInclusive_andTheMaximumMapsToTheServersMaximum() = runTest(dispatcher) {
        weeks.setBlockEndResult = Result.success(running)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.ExtendWeek(day(22)))
        advanceUntilIdle()
        model.onEvent(TrainerEvent.ExtendWeek(weekEnd))
        advanceUntilIdle()

        assertEquals(listOf(LocalDate(2026, 12, 6), original), weeks.blockEndCalls.map { it.second })
    }

    @Test
    fun earlierExtension_leavesFewerThanFourteenDays() = runTest(dispatcher) {
        // An earlier week already took 8 days: the server's maximum is Nov 30, so this week gets 8 more at most.
        weeks.pauseResult = Result.success(
            running.copy(blockEnd = day(30), extensionDays = 8, minEnd = day(30), maxEnd = LocalDate(2026, 12, 6)),
        )
        weeks.setBlockEndResult = Result.success(running)
        val model = viewModel()
        advanceUntilIdle()

        val extension = assertNotNull(model.content().weekExtension)
        assertEquals(day(14), extension.maxDay)
        model.onEvent(TrainerEvent.ExtendWeek(day(15)))
        advanceUntilIdle()
        assertTrue(weeks.blockEndCalls.isEmpty())
        model.onEvent(TrainerEvent.ExtendWeek(day(14)))
        advanceUntilIdle()

        assertEquals(listOf(LocalDate(2026, 12, 6)), weeks.blockEndCalls.map { it.second })
    }

    @Test
    fun notExtendable_ignoresPickerAndPick() = runTest(dispatcher) {
        weeks.pauseResult = Result.success(running.copy(extendable = false, minEnd = null, maxEnd = null))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.OpenExtendWeekPicker)
        model.onEvent(TrainerEvent.ExtendWeek(day(11)))
        advanceUntilIdle()

        assertNull(model.content().weekExtension)
        assertFalse(model.content().extendWeekPickerOpen)
        assertTrue(weeks.blockEndCalls.isEmpty())
    }

    @Test
    fun noCurrentWeek_ignoresPickerAndPick() = runTest(dispatcher) {
        weeks.weeksResult = Result.success(listOf(planWeek(3, WeekPhase.Upcoming)))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.OpenExtendWeekPicker)
        model.onEvent(TrainerEvent.ExtendWeek(day(11)))
        advanceUntilIdle()

        assertFalse(model.content().extendWeekPickerOpen)
        assertTrue(weeks.blockEndCalls.isEmpty())
    }

    @Test
    fun repositoryError_keepsTheOldWeekAndEmitsTheFailure() = runTest(dispatcher) {
        weeks.setBlockEndResult = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.ExtendWeek(day(11)))
            advanceUntilIdle()

            assertEquals(TrainerEffect.WeekEndFailed(offline = false), awaitItem())
        }
        assertEquals(original, model.content().pause?.blockEnd)
        assertFalse(model.content().extendingWeek)
        assertFalse(model.content().extendWeekPickerOpen)
    }

    @Test
    fun networkError_isFlaggedOffline() = runTest(dispatcher) {
        weeks.setBlockEndResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.ExtendWeek(day(11)))
            advanceUntilIdle()

            assertEquals(TrainerEffect.WeekEndFailed(offline = true), awaitItem())
        }
    }

    @Test
    fun whileInFlight_busyIsSetAndASecondPickIsIgnored() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        weeks.blockEndGate = gate
        weeks.setBlockEndResult = Result.success(extended(day(11)).first)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.ExtendWeek(day(11)))
        advanceUntilIdle()
        assertTrue(model.content().extendingWeek)
        model.onEvent(TrainerEvent.ExtendWeek(day(13)))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, weeks.blockEndCalls.size)
        assertFalse(model.content().extendingWeek)
    }

    @Test
    fun openAndDismissPicker_toggleTheState() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.OpenExtendWeekPicker)
        assertTrue(model.content().extendWeekPickerOpen)
        model.onEvent(TrainerEvent.DismissExtendWeekPicker)

        assertFalse(model.content().extendWeekPickerOpen)
        assertTrue(weeks.blockEndCalls.isEmpty())
    }

    @Test
    fun recreatedModel_restoresTheOpenPicker() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.OpenExtendWeekPicker)

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertTrue(recreated.content().extendWeekPickerOpen)
    }

    @Test
    fun recreatedModel_afterDismiss_hasNoPicker() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.OpenExtendWeekPicker)
        first.onEvent(TrainerEvent.DismissExtendWeekPicker)

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertFalse(recreated.content().extendWeekPickerOpen)
    }

    @Test
    fun restoredPicker_isDroppedWhenTheBlockIsNoLongerExtendable() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.OpenExtendWeekPicker)
        weeks.pauseResult = Result.success(running.copy(extendable = false))

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertFalse(recreated.content().extendWeekPickerOpen)
    }

    @Test
    fun restoredPicker_isDroppedWhenThereIsNoCurrentWeekAnymore() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.OpenExtendWeekPicker)
        weeks.weeksResult = Result.success(listOf(planWeek(2, WeekPhase.Past)))

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertFalse(recreated.content().extendWeekPickerOpen)
    }

    @Test
    fun refresh_keepsTheOpenPicker() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenExtendWeekPicker)

        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertTrue(model.content().extendWeekPickerOpen)
    }
}
