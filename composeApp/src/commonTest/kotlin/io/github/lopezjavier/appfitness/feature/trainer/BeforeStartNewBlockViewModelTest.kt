package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.GoalSwitchPreview
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.domain.usecase.OpenNewBlockUseCase
import io.github.lopezjavier.appfitness.domain.usecase.PreviewGoalSwitchUseCase
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-240: "Antes de empezar" in new-block mode (US-110): the goal-change figures and the way into the builder. */
@OptIn(ExperimentalCoroutinesApi::class)
class BeforeStartNewBlockViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val coach = FakeCoachRepository()
    private val reviews = FakeCoachReviewRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coach.inputs[PlanInputKind.Block] = Result.success(
            PlanInputs(PlanInputKind.Block, null, listOf(planInput(PlanInputKey.Place))),
        )
        reviews.newBlock = Result.success(newBlockEntry(changed = true))
        reviews.preview = Result.success(GoalSwitchPreview("plan-2", 2800, 2300, Phase.Cut))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun args() = SavedStateHandle().apply {
        set("mode", "new_block")
        set("planId", "plan-1")
    }

    private fun viewModel(saved: SavedStateHandle = args()) = BeforeStartViewModel(
        coach,
        FakeCoachIntakeRepository(),
        MutableClock(),
        FakeRoutineRepository(),
        saved,
        openNewBlock = OpenNewBlockUseCase(reviews),
        previewGoalSwitch = PreviewGoalSwitchUseCase(reviews),
    )

    private fun BeforeStartViewModel.content() = assertIs<BeforeStartUiState.Content>(uiState.value)

    @Test
    fun theArgument_mapsToTheNewBlockMode_thatReadsTheBlockInputs() = runTest(dispatcher) {
        assertEquals(BeforeStartMode.NewBlock, BeforeStartMode.fromArgument("new_block"))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(BeforeStartMode.NewBlock, model.content().mode)
        assertEquals(listOf(PlanInputKind.Block), coach.inputsCalls)
        assertNull(model.content().replaces)
    }

    @Test
    fun goalUnchanged_hasNoNote_andNeverPreviews() = runTest(dispatcher) {
        reviews.newBlock = Result.success(newBlockEntry(changed = false))
        val model = viewModel()

        advanceUntilIdle()

        assertNull(model.content().goalChange)
        assertEquals(0, reviews.previewCalls)
    }

    @Test
    fun goalChanged_previewsAndExposesTheFigures() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(GoalChangeNote(2800, 2300, Phase.Cut), model.content().goalChange)
        assertEquals("plan-2", model.content().previewPlanId)
        assertEquals(1, reviews.previewCalls)
    }

    @Test
    fun aPreviewWithoutFigures_keepsTheNullsForTheFallbackText() = runTest(dispatcher) {
        reviews.preview = Result.success(GoalSwitchPreview("plan-2", null, null, null))
        reviews.newBlock = Result.success(newBlockEntry(changed = true, currentKcal = null))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(GoalChangeNote(null, null, null), model.content().goalChange)
    }

    @Test
    fun theCurrentKcalFallsBackToTheEntryWhenThePreviewHasNone() = runTest(dispatcher) {
        reviews.preview = Result.success(GoalSwitchPreview("plan-2", null, 2300, Phase.Cut))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(GoalChangeNote(2800, 2300, Phase.Cut), model.content().goalChange)
    }

    @Test
    fun footer_opensTheBuilderOnThePreviewPlan_withTheAcceptedPlanAsPrevious() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BeforeStartEvent.Create)
            advanceUntilIdle()

            assertEquals(
                BeforeStartEffect.Created(BeforeStartMode.NewBlock, planId = "plan-2", replacingPlanId = "plan-1"),
                awaitItem(),
            )
        }
    }

    @Test
    fun footerWithoutAGoalChange_opensTheBuilderOnTheAcceptedPlan() = runTest(dispatcher) {
        reviews.newBlock = Result.success(newBlockEntry(changed = false))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BeforeStartEvent.Create)
            advanceUntilIdle()

            assertEquals(
                BeforeStartEffect.Created(BeforeStartMode.NewBlock, planId = "plan-1", replacingPlanId = "plan-1"),
                awaitItem(),
            )
        }
    }

    @Test
    fun footer_doesNotAskHowTheBlockStarts() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BeforeStartEvent.Create)
        advanceUntilIdle()

        assertEquals(StartChoiceStep.None, model.content().startChoice)
    }

    @Test
    fun noSavedBlock_closesTheScreen() = runTest(dispatcher) {
        reviews.newBlock = Result.success(null)
        val model = viewModel()

        model.effects.test {
            advanceUntilIdle()

            assertEquals(BeforeStartEffect.Close, awaitItem())
        }
        assertEquals(BeforeStartUiState.Loading, model.uiState.value)
    }

    @Test
    fun aFailedPreview_isAnErrorWithRetry_andTellsTheUser() = runTest(dispatcher) {
        reviews.preview = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()

        model.effects.test {
            advanceUntilIdle()

            assertEquals(BeforeStartEffect.NewBlockPrepareFailed(offline = false), awaitItem())
        }
        assertEquals(BeforeStartUiState.Error, model.uiState.value)
    }

    @Test
    fun anOfflinePreview_isOffline_andRetryRecovers() = runTest(dispatcher) {
        reviews.preview = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(BeforeStartUiState.Offline, model.uiState.value)

        reviews.preview = Result.success(GoalSwitchPreview("plan-2", 2800, 2300, Phase.Cut))
        model.onEvent(BeforeStartEvent.Retry)
        advanceUntilIdle()

        assertEquals("plan-2", model.content().previewPlanId)
    }

    @Test
    fun aFailedEntryRead_isOffline_withoutPreviewing() = runTest(dispatcher) {
        reviews.newBlock = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(BeforeStartUiState.Offline, model.uiState.value)
        assertEquals(0, reviews.previewCalls)
    }

    @Test
    fun leavingWritesNothing_noConfirmNorSave() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(coach.confirmCalls.isEmpty())
    }

    @Test
    fun aNewInstanceOnTheSameHandle_refetchesThePreviewAndGetsTheSameFigures() = runTest(dispatcher) {
        val saved = args()
        viewModel(saved)
        advanceUntilIdle()

        val restored = viewModel(saved)
        advanceUntilIdle()

        assertEquals("plan-2", restored.content().previewPlanId)
        assertEquals(GoalChangeNote(2800, 2300, Phase.Cut), restored.content().goalChange)
        assertEquals(2, reviews.previewCalls)
    }

    @Test
    fun whenTheGoalIsNoLongerChanged_thePreviewIdIsDropped() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        reviews.newBlock = Result.success(newBlockEntry(changed = false))
        model.onEvent(BeforeStartEvent.Refresh)
        advanceUntilIdle()

        assertNull(model.content().previewPlanId)
    }
}
