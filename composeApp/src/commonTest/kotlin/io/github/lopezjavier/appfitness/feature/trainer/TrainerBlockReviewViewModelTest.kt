package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.usecase.LoadBlockReviewUseCase
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

/** T-234: the "Revisión del bloque" card of the Trainer tab appears only when the block's review is open. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerBlockReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val reviews = FakeCoachReviewRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        TrainerViewModel(FakeCoachRepository(), MutableClock(), loadBlockReview = LoadBlockReviewUseCase(reviews))

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    @Test
    fun afterTheLastDay_theReviewIsDue() = runTest(dispatcher) {
        reviews.review = Result.success(blockReview())
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().blockReviewDue)
    }

    @Test
    fun beforeTheEnd_noReviewIsDue() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().blockReviewDue)
    }

    @Test
    fun aFailedRead_hidesTheCardWithoutBreakingTheTab() = runTest(dispatcher) {
        reviews.review = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().blockReviewDue)
        assertEquals(PlanSummary.None, model.content().plan)
    }

    @Test
    fun withoutTheUseCase_noReviewIsDue() = runTest(dispatcher) {
        val model = TrainerViewModel(FakeCoachRepository(), MutableClock())

        advanceUntilIdle()

        assertFalse(model.content().blockReviewDue)
    }
}
