package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: leaving the app and coming back keeps the review, its scroll, an open help and the decision. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h400dp-xxhdpi")
class BlockReviewRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private fun state(decision: PhaseDecision? = null) = BlockReviewUiState.Content(
        review = blockReview(phaseDecision = decision),
        exerciseNames = mapOf("id-bench" to "Press banca"),
    )

    private fun StateRestorationTester.show(state: BlockReviewUiState) = setContent {
        AppTheme { BlockReviewScreen(state, flowOf(true), {}, {}, { _, _, _ -> }) }
    }

    @Test
    fun recreation_keepsTheScrollPosition() {
        val restoration = StateRestorationTester(rule)
        restoration.show(state())
        rule.onNodeWithTag("block_review_phase").performScrollTo()
        rule.onNodeWithTag("block_review_section_sessions").assertIsNotDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("block_review_phase").assertIsDisplayed()
        rule.onNodeWithTag("block_review_section_sessions").assertIsNotDisplayed()
    }

    @Test
    fun recreation_keepsTheHelpDialogOpen() {
        val restoration = StateRestorationTester(rule)
        restoration.show(state())
        rule.onNodeWithTag("block_review_help_lifts").performClick()
        rule.onNodeWithTag("block_review_help_lifts_sheet_body").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("block_review_help_lifts_sheet_body").assertIsDisplayed()
    }

    @Test
    fun recreation_showsTheDecisionReadFromTheReview() {
        val restoration = StateRestorationTester(rule)
        restoration.show(state(PhaseDecision.Kept))
        rule.onNodeWithTag("block_review_phase_status").performScrollTo().assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("block_review_phase_status").assertIsDisplayed()
    }
}
