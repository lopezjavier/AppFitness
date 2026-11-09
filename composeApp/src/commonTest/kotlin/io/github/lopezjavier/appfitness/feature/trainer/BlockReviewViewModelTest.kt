package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.app.ReminderEnv
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import io.github.lopezjavier.appfitness.domain.usecase.DecideCalorieProposalUseCase
import io.github.lopezjavier.appfitness.domain.usecase.DecidePhaseChangeUseCase
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.LoadBlockReviewUseCase
import io.github.lopezjavier.appfitness.domain.usecase.MarkBlockReviewSeen
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
class BlockReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val env = ReminderEnv(LocalDate(2026, 10, 19))
    private val reviews = env.reviews.apply { review = Result.success(blockReview()) }
    private val plans = FakeCoachPlanRepository()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("id-bench", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press banca")))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        saved: SavedStateHandle = SavedStateHandle(),
        coordinator: ReminderCoordinator = ReminderCoordinator(env.load, backgroundScope),
    ) = BlockReviewViewModel(
        loadReview = LoadBlockReviewUseCase(reviews),
        decidePhase = DecidePhaseChangeUseCase(reviews),
        plans = plans,
        decideCalorie = DecideCalorieProposalUseCase(plans),
        markSeen = MarkBlockReviewSeen(reviews),
        reminders = coordinator,
        names = ExerciseNameResolver(exercises, library),
        clock = MutableClock(),
        savedState = saved,
    )

    private fun BlockReviewViewModel.content() = assertIs<BlockReviewUiState.Content>(uiState.value)

    @Test
    fun load_showsTheReviewWithTheResolvedNames() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(BlockReviewUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = model.content()
        assertEquals("review-1", content.review.id)
        assertEquals("Press banca", content.exerciseNames["id-bench"])
        assertNull(content.calorieProposal)
        assertNull(content.nextPlanId)
    }

    @Test
    fun unseenStoredReview_isMarkedSeenOnceAndItsNoticeClears() = runTest(dispatcher) {
        val coordinator = ReminderCoordinator(env.load, backgroundScope)
        coordinator.refresh()
        assertEquals(listOf(ReminderKind.BlockEnd), coordinator.reminders.value.map { it.kind })

        viewModel(coordinator = coordinator)
        advanceUntilIdle()

        assertEquals(listOf("review-1"), reviews.markedSeen)
        assertTrue(coordinator.reminders.value.none { it.kind == ReminderKind.BlockEnd })
    }

    @Test
    fun alreadySeenReview_isNotMarkedAgain() = runTest(dispatcher) {
        reviews.review = Result.success(blockReview(seen = true))
        viewModel()

        advanceUntilIdle()

        assertTrue(reviews.markedSeen.isEmpty())
    }

    @Test
    fun earlyReviewWithoutId_isNeverMarked() = runTest(dispatcher) {
        reviews.review = Result.success(blockReview().copy(id = null, early = true))
        viewModel()

        advanceUntilIdle()

        assertTrue(reviews.markedSeen.isEmpty())
    }

    @Test
    fun failedMark_isSilentAndTheContentStays() = runTest(dispatcher) {
        reviews.markSeenResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertIs<BlockReviewUiState.Content>(model.uiState.value)
        assertEquals(listOf("review-1"), reviews.markedSeen)
    }

    @Test
    fun recreatedModelOverTheSameHandle_doesNotMarkAgain() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        viewModel(saved)
        advanceUntilIdle()
        reviews.review = Result.success(blockReview(seen = false))

        viewModel(saved)
        advanceUntilIdle()

        assertEquals(1, reviews.markedSeen.size)
    }

    @Test
    fun beforeTheEnd_isEmpty() = runTest(dispatcher) {
        reviews.review = Result.success(null)
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(BlockReviewUiState.Empty, model.uiState.value)
    }

    @Test
    fun networkFailure_isOffline() = runTest(dispatcher) {
        reviews.review = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(BlockReviewUiState.Offline, model.uiState.value)
    }

    @Test
    fun otherFailure_isError() = runTest(dispatcher) {
        reviews.review = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(BlockReviewUiState.Error, model.uiState.value)
    }

    @Test
    fun retry_loadsAgainAfterAFailure() = runTest(dispatcher) {
        reviews.review = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()
        reviews.review = Result.success(blockReview())

        model.onEvent(BlockReviewEvent.Retry)
        advanceUntilIdle()

        assertIs<BlockReviewUiState.Content>(model.uiState.value)
    }

    @Test
    fun aFailedNameRead_keepsTheReviewWithoutNames() = runTest(dispatcher) {
        exercises.failure = AppError.Network
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().exerciseNames.isEmpty())
        assertEquals(1, model.content().review.lifts.size)
    }

    @Test
    fun accept_storesTheDecision_keepsTheNewPlan_andReadsTheServerDecision() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BlockReviewEvent.DecidePhase(PhaseDecision.Accepted))
            assertEquals(PhaseDecision.Accepted, model.content().deciding)
            advanceUntilIdle()

            assertEquals(BlockReviewEffect.PhaseDecided(PhaseDecision.Accepted), awaitItem())
        }
        val content = model.content()
        assertEquals(listOf("accept:review-1"), reviews.calls)
        assertEquals(PhaseDecision.Accepted, content.review.phaseDecision)
        assertNull(content.deciding)
        assertEquals("plan-2", content.nextPlanId)
        assertEquals("plan-2", saved.get<String>("block_review_next_plan_id"))
    }

    @Test
    fun keep_storesTheDecisionAndBuildsNoPlan() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockReviewEvent.DecidePhase(PhaseDecision.Kept))
        advanceUntilIdle()

        assertEquals(listOf("keep:review-1"), reviews.calls)
        assertEquals(PhaseDecision.Kept, model.content().review.phaseDecision)
        assertNull(model.content().nextPlanId)
    }

    @Test
    fun aDecidedReview_cannotBeDecidedAgain() = runTest(dispatcher) {
        reviews.review = Result.success(blockReview(phaseDecision = PhaseDecision.Kept))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockReviewEvent.DecidePhase(PhaseDecision.Accepted))
        advanceUntilIdle()

        assertTrue(reviews.calls.isEmpty())
    }

    @Test
    fun withoutAProposal_nothingCanBeDecided() = runTest(dispatcher) {
        reviews.review = Result.success(blockReview(phaseProposal = null))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockReviewEvent.DecidePhase(PhaseDecision.Accepted))
        advanceUntilIdle()

        assertTrue(reviews.calls.isEmpty())
        assertNull(model.content().review.phaseProposal)
    }

    @Test
    fun aFailedDecision_keepsTheChoice_andRetryRepeatsIt() = runTest(dispatcher) {
        reviews.acceptResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BlockReviewEvent.DecidePhase(PhaseDecision.Accepted))
            advanceUntilIdle()
            assertEquals(BlockReviewEffect.PhaseDecisionFailed, awaitItem())

            assertEquals(PhaseDecision.Accepted, model.content().decisionFailed)
            assertNull(model.content().deciding)
            assertNull(model.content().review.phaseDecision)

            reviews.acceptResult = Result.success("plan-2")
            model.onEvent(BlockReviewEvent.RetryDecision)
            advanceUntilIdle()
            assertEquals(BlockReviewEffect.PhaseDecided(PhaseDecision.Accepted), awaitItem())
        }
        assertEquals(listOf("accept:review-1", "accept:review-1"), reviews.calls)
        assertNull(model.content().decisionFailed)
        assertEquals(PhaseDecision.Accepted, model.content().review.phaseDecision)
    }

    @Test
    fun recreatedFromTheSameState_keepsTheNewPlan_andReadsTheStoredDecision() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(BlockReviewEvent.DecidePhase(PhaseDecision.Accepted))
        advanceUntilIdle()

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals("plan-2", second.content().nextPlanId)
        assertEquals(PhaseDecision.Accepted, second.content().review.phaseDecision)
    }

    @Test
    fun pendingCalorieProposal_isShownAndApplied() = runTest(dispatcher) {
        plans.pending = Result.success(calorieProposal("change-9"))
        val model = viewModel()
        advanceUntilIdle()
        assertEquals("change-9", model.content().calorieProposal?.id)

        model.onEvent(BlockReviewEvent.ApplyCalorie)
        advanceUntilIdle()

        assertEquals(listOf("change-9" to true), plans.decisions)
        assertNull(model.content().calorieProposal)
        assertEquals(false, model.content().decidingCalorie)
    }

    @Test
    fun pendingCalorieProposal_notNowDeclinesIt() = runTest(dispatcher) {
        plans.pending = Result.success(calorieProposal("change-9"))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockReviewEvent.NotNowCalorie)
        advanceUntilIdle()

        assertEquals(listOf("change-9" to false), plans.decisions)
        assertNull(model.content().calorieProposal)
    }

    @Test
    fun aFailedCalorieDecision_keepsTheProposalAndTheChoice() = runTest(dispatcher) {
        plans.pending = Result.success(calorieProposal("change-9"))
        plans.decideResult = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BlockReviewEvent.ApplyCalorie)
            advanceUntilIdle()
            assertEquals(BlockReviewEffect.CalorieFailed, awaitItem())
        }
        assertEquals("change-9", model.content().calorieProposal?.id)
        assertEquals(CalorieChoice.Apply, model.content().calorieFailed)
        assertEquals(false, model.content().decidingCalorie)
    }

    @Test
    fun aFailedCalorieRead_degradesToNoCard() = runTest(dispatcher) {
        plans.pending = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()

        advanceUntilIdle()

        assertNull(model.content().calorieProposal)
    }

    @Test
    fun withoutAProposal_aCalorieEventDoesNothing() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BlockReviewEvent.ApplyCalorie)
        advanceUntilIdle()

        assertTrue(plans.decisions.isEmpty())
    }

    @Test
    fun calorieChoice_toEvent_mapsEachChoiceToItsEvent() {
        assertEquals(BlockReviewEvent.ApplyCalorie, CalorieChoice.Apply.toEvent())
        assertEquals(BlockReviewEvent.NotNowCalorie, CalorieChoice.NotNow.toEvent())
    }
}
