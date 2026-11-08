package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the plan proposal survives recreation (scroll position, open Info sheet, a request cut mid-way). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h480dp-xxhdpi")
class PlanProposalRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val plans = FakeCoachPlanRepository()
    private val coach = FakeCoachRepository(Result.success(coachValues(provisional = false)))

    private fun StateRestorationTester.setProposal() {
        setContent {
            AppTheme {
                PlanProposalScreen(
                    state = PlanProposalUiState.Proposal(coachPlan()),
                    isOnline = flowOf(true),
                    onEvent = {},
                    onBack = {},
                    onOpenBeforeStart = {},
                )
            }
        }
    }

    @Test
    fun scrollPosition_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setProposal()
        rule.onNodeWithTag("plan_proposal_outcome_weight").performScrollTo()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("plan_proposal_outcome_weight").assertIsDisplayed()
    }

    @Test
    fun openInfoSheet_staysOpenAfterRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setProposal()
        rule.onNodeWithTag("plan_proposal_outcome_help_weight").performScrollTo().performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithText("Bajar de forma gradual.").assertExists()
    }

    private val saved = SavedStateHandle()
    private var current by mutableStateOf(PlanProposalViewModel(plans, coach, MutableClock(), saved))

    private fun startMidRequest() {
        plans.requestGate = CompletableDeferred()
        rule.setContent {
            AppTheme {
                PlanProposalRoot(onBack = {
                }, onOpenBeforeStart = {}, onFix = {}, viewModel = current, connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("plan_proposal_progress").assertExists()
    }

    private fun recreate() {
        current = PlanProposalViewModel(plans, coach, MutableClock(), saved)
        rule.waitForIdle()
    }

    @Test
    fun recreatedMidRequest_withAStoredPlan_landsOnTheProposal() {
        startMidRequest()
        plans.current = Result.success(coachPlan())

        recreate()

        rule.onNodeWithTag("plan_proposal_accept").assertExists()
    }

    @Test
    fun recreatedMidRequest_withoutAStoredPlan_offersReintentar_andDoesNotAskAgain() {
        startMidRequest()
        val requestsBefore = plans.requests.size

        recreate()

        rule.onNodeWithTag("plan_proposal_retry").assertExists()
        rule.onNodeWithText("No se recibió el plan. Inténtalo de nuevo.").assertExists()
        assertEquals(requestsBefore, plans.requests.size)
    }

    @Test
    fun cardioHelpSheet_staysOpenAfterRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                PlanProposalScreen(
                    state = PlanProposalUiState.Proposal(coachPlan(activity = defaultActivity)),
                    isOnline = flowOf(true),
                    onEvent = {},
                    onBack = {},
                    onOpenBeforeStart = {},
                )
            }
        }
        rule.onNodeWithTag("plan_proposal_activity_cardio_help").performScrollTo().performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("plan_proposal_activity_cardio_help_sheet_body").assertExists()
    }

    @Test
    fun recreatedViewModel_keepsTheCardioSheetOpenWithTheChangedDraft() {
        plans.current = Result.success(coachPlan(activity = defaultActivity))
        current = PlanProposalViewModel(plans, coach, MutableClock(), saved)
        rule.setContent {
            AppTheme {
                PlanProposalRoot(onBack = {
                }, onOpenBeforeStart = {}, onFix = {}, viewModel = current, connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("plan_proposal_activity_cardio_row").performScrollTo().performClick()
        rule.onNodeWithTag("cardio_sheet_stepper_decrement").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("cardio_sheet_stepper_value").assertTextEquals("145 min")

        // A new view model over the same saved state, as after process death.
        current = PlanProposalViewModel(plans, coach, MutableClock(), saved)
        rule.waitForIdle()

        rule.onNodeWithTag("cardio_sheet_stepper_value").assertTextEquals("145 min")
        rule.onNodeWithTag("cardio_sheet_commit").assertIsEnabled()
    }

    @Test
    fun recreatedViewModel_rereadsTheBlockedValuesAndKeepsTheActivitySheetOpen() {
        plans.current = Result.success(coachPlan())
        coach.inputs[PlanInputKind.Plan] = Result.success(
            PlanInputs(
                PlanInputKind.Plan,
                null,
                listOf(
                    planInput(
                        PlanInputKey.ActivityLevel,
                        PlanInputStatus.Provisional,
                        value = PlanInputValue.Text("moderate"),
                        fixTarget = PlanFixTarget.ActivitySheet,
                        blocksAccept = true,
                    ),
                ),
            ),
        )
        current = PlanProposalViewModel(plans, coach, MutableClock(), saved)
        rule.setContent {
            AppTheme {
                PlanProposalRoot(onBack = {
                }, onOpenBeforeStart = {}, onFix = {}, viewModel = current, connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("plan_proposal_unconfirmed_activity_level").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_sheet_note").assertExists()

        // A new view model over the same saved state, as after process death.
        current = PlanProposalViewModel(plans, coach, MutableClock(), saved)
        rule.waitForIdle()

        rule.onNodeWithTag("plan_proposal_unconfirmed_activity_level").assertExists()
        rule.onNodeWithTag("trainer_sheet_note").assertExists()
    }
}
