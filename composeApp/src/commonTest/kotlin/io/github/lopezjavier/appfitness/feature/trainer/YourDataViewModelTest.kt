package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.PlanInput
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputSource
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
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

@OptIn(ExperimentalCoroutinesApi::class)
class YourDataViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val coach = FakeCoachRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        YourDataViewModel(coach, MutableClock(), saved)

    private fun YourDataViewModel.content() = assertIs<YourDataUiState.Content>(uiState.value)

    private fun plan(vararg inputs: PlanInput) {
        coach.inputs[PlanInputKind.Plan] = Result.success(PlanInputs(PlanInputKind.Plan, null, inputs.toList()))
    }

    private fun block(vararg inputs: PlanInput) {
        coach.inputs[PlanInputKind.Block] = Result.success(PlanInputs(PlanInputKind.Block, null, inputs.toList()))
    }

    @Test
    fun groupsRowsByOrigin_inScreenOrder_skippingEmptySections() = runTest(dispatcher) {
        plan(
            planInput(PlanInputKey.Weight, source = PlanInputSource.WeighIn),
            planInput(PlanInputKey.Sex, source = PlanInputSource.Profile),
            planInput(PlanInputKey.TotalExpenditure, source = PlanInputSource.CoachEstimate),
        )
        block(planInput(PlanInputKey.Place, source = PlanInputSource.MyTraining))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(
            listOf(
                PlanInputOrigin.Profile,
                PlanInputOrigin.MyTraining,
                PlanInputOrigin.Weighing,
                PlanInputOrigin.Estimate,
            ),
            model.content().sections.map { it.origin },
        )
    }

    @Test
    fun emptyOrigin_hasNoSection() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.Sex, source = PlanInputSource.Profile))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(listOf(PlanInputOrigin.Profile), model.content().sections.map { it.origin })
    }

    @Test
    fun mergesPlanAndBlock_dedupingSharedKeys() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.Intention), planInput(PlanInputKey.Sex))
        block(planInput(PlanInputKey.Intention), planInput(PlanInputKey.Place, source = PlanInputSource.MyTraining))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(
            listOf(PlanInputKey.Intention, PlanInputKey.Sex, PlanInputKey.Place),
            model.content().rows.map { it.key },
        )
    }

    @Test
    fun countsTheProvisionalRows() = runTest(dispatcher) {
        plan(
            planInput(PlanInputKey.ActivityLevel, PlanInputStatus.Provisional),
            planInput(PlanInputKey.TotalExpenditure, PlanInputStatus.Provisional),
            planInput(PlanInputKey.Sex),
        )
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(2, model.content().provisionalCount)
    }

    @Test
    fun failedBlockList_keepsThePlanRows() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.Sex))
        coach.inputs[PlanInputKind.Block] = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(listOf(PlanInputKey.Sex), model.content().rows.map { it.key })
    }

    @Test
    fun bothListsOffline_isOffline() = runTest(dispatcher) {
        coach.inputs[PlanInputKind.Plan] = Result.failure(AppException(AppError.Network))
        coach.inputs[PlanInputKind.Block] = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(YourDataUiState.Offline, model.uiState.value)
    }

    @Test
    fun bothListsFailing_isError_andRetryRecovers() = runTest(dispatcher) {
        coach.inputs[PlanInputKind.Plan] = Result.failure(AppException(AppError.Unknown))
        coach.inputs[PlanInputKind.Block] = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(YourDataUiState.Error, model.uiState.value)

        coach.inputs.clear()
        plan(planInput(PlanInputKey.Sex))
        model.onEvent(YourDataEvent.Retry)
        advanceUntilIdle()

        assertIs<YourDataUiState.Content>(model.uiState.value)
    }

    @Test
    fun refresh_reloadsSilently_andKeepsTheSheet() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.ActivityLevel, value = PlanInputValue.Text("high")))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(YourDataEvent.OpenSheet)
        plan(planInput(PlanInputKey.ActivityLevel, value = PlanInputValue.Text("high")), planInput(PlanInputKey.Sex))

        model.uiState.test {
            assertIs<YourDataUiState.Content>(awaitItem())
            model.onEvent(YourDataEvent.Refresh)
            advanceUntilIdle()
            val next = assertIs<YourDataUiState.Content>(awaitItem())
            assertEquals(2, next.rows.size)
            assertEquals(ActivityLevel.High, next.sheet)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pickLevel_confirmsAndReloadsTheRows() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.ActivityLevel, PlanInputStatus.Provisional))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(YourDataEvent.OpenSheet)

        model.onEvent(YourDataEvent.PickLevel(ActivityLevel.VeryHigh))
        advanceUntilIdle()

        assertEquals(ActivityLevel.VeryHigh, coach.confirmCalls.single().second)
        assertNull(model.content().sheet)
        assertEquals(0, model.content().provisionalCount)
    }

    @Test
    fun pickLevelFailure_keepsTheSheet_andEmitsFailure() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.ActivityLevel, PlanInputStatus.Provisional))
        coach.confirmResult = { Result.failure<CoachDerivedValues>(AppException(AppError.Network)) }
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(YourDataEvent.OpenSheet)

        model.effects.test {
            model.onEvent(YourDataEvent.PickLevel(ActivityLevel.High))
            advanceUntilIdle()
            assertEquals(YourDataEffect.SaveFailed(offline = true), awaitItem())
        }

        assertEquals(ActivityLevel.Moderate, model.content().sheet)
        assertFalse(model.content().savingLevel)
    }

    @Test
    fun sheetSurvivesRecreation() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.ActivityLevel))
        val saved = SavedStateHandle()
        viewModel(saved).also {
            advanceUntilIdle()
            it.onEvent(YourDataEvent.OpenSheet)
        }

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(ActivityLevel.Moderate, second.content().sheet)
        assertEquals(0, coach.confirmCalls.size)
    }
}
