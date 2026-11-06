package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.app.FakeCoachSummaryRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
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
import kotlin.test.assertTrue

/** T-231: the "Resumen semanal" row of the Trainer tab appears only when the latest finished week has a summary. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerWeekSummaryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val summaries = FakeCoachSummaryRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TrainerViewModel(FakeCoachRepository(), MutableClock(), summaries = summaries)

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    @Test
    fun withASummary_theRowIsAvailable() = runTest(dispatcher) {
        summaries.latest = Result.success(weekSummary())
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().hasWeekSummary)
    }

    @Test
    fun withoutASummary_noRow() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().hasWeekSummary)
    }

    @Test
    fun aFailedSummaryRead_hidesTheRowWithoutBreakingTheTab() = runTest(dispatcher) {
        summaries.latest = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().hasWeekSummary)
        assertEquals(PlanSummary.None, model.content().plan)
    }
}
