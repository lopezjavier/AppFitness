package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.app.ReminderEnv
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.usecase.DecideCalorieProposalUseCase
import io.github.lopezjavier.appfitness.domain.usecase.DecidePhaseChangeUseCase
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.LoadBlockReviewUseCase
import io.github.lopezjavier.appfitness.domain.usecase.MarkBlockReviewSeen
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TIMEOUT_MS = 5_000L

/** ADR-40: leaving the app keeps the review, its scroll, an open help, the group sheet and the decision. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h400dp-xxhdpi")
class BlockReviewRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val env = ReminderEnv(LocalDate(2026, 10, 19))

    private fun state(decision: PhaseDecision? = null) =
        reviewContent(v2Review(review = blockReview(phaseDecision = decision)))

    private fun StateRestorationTester.show(state: BlockReviewUiState) = setContent {
        AppTheme { BlockReviewScreen(state, flowOf(true), {}, {}, { _, _, _ -> }) }
    }

    private fun viewModel(saved: SavedStateHandle) = BlockReviewViewModel(
        loadReview = LoadBlockReviewUseCase(env.reviews),
        decidePhase = DecidePhaseChangeUseCase(env.reviews),
        plans = FakeCoachPlanRepository(),
        decideCalorie = DecideCalorieProposalUseCase(FakeCoachPlanRepository()),
        markSeen = MarkBlockReviewSeen(env.reviews),
        reminders = ReminderCoordinator(env.load, CoroutineScope(Dispatchers.Unconfined)),
        names = ExerciseNameResolver(FakeExerciseRepository(emptyList()), FakeExerciseLibrary(emptyMap())),
        clock = MutableClock(),
        savedState = saved,
    )

    @Test
    fun recreation_keepsTheScrollPosition() {
        val restoration = StateRestorationTester(rule)
        restoration.show(state())
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("block_review_phase"))
        rule.onNodeWithTag("block_review_coach_card").assertIsNotDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("block_review_phase").assertIsDisplayed()
        rule.onNodeWithTag("block_review_coach_card").assertIsNotDisplayed()
    }

    @Test
    fun recreation_keepsTheHelpOpen() {
        val restoration = StateRestorationTester(rule)
        restoration.show(state())
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("block_review_strength_help"))
        rule.onNodeWithTag("block_review_strength_help").performClick()
        rule.onNodeWithTag("block_review_strength_help_sheet_body").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("block_review_strength_help_sheet_body").assertIsDisplayed()
    }

    @Test
    fun recreation_keepsTheGroupSheetOpen() {
        val restoration = StateRestorationTester(rule)
        restoration.show(state())
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("block_review_strength_group_chest"))
        rule.onNodeWithTag("block_review_strength_group_chest").performClick()
        rule.onNodeWithTag("block_review_strength_sheet_exercise_id-bench").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("block_review_strength_sheet_exercise_id-bench").assertIsDisplayed()
    }

    @Test
    fun recreation_showsTheDecisionReadFromTheReview() {
        val restoration = StateRestorationTester(rule)
        restoration.show(state(PhaseDecision.Kept))
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("block_review_phase_status"))
        rule.onNodeWithTag("block_review_phase_status").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("block_review_phase_status").assertIsDisplayed()
    }

    @Test
    fun recreatedViewModelOverTheSameHandle_keepsTheNextPlanAndDoesNotMarkSeenAgain() {
        env.reviews.review = Result.success(v2Review(review = blockReview()))
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        rule.waitUntil(TIMEOUT_MS) { first.uiState.value is BlockReviewUiState.Content }
        first.onEvent(BlockReviewEvent.DecidePhase(PhaseDecision.Accepted))
        rule.waitUntil(TIMEOUT_MS) { (first.uiState.value as? BlockReviewUiState.Content)?.nextPlanId != null }

        val second = viewModel(saved)
        rule.waitUntil(TIMEOUT_MS) { second.uiState.value is BlockReviewUiState.Content }

        assertEquals("plan-2", (second.uiState.value as BlockReviewUiState.Content).nextPlanId)
        assertEquals(listOf("review-1"), env.reviews.markedSeen)
    }
}
