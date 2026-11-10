package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DietBreakWeek
import io.github.lopezjavier.appfitness.domain.model.PhaseOptions
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-40: the "Descanso de dieta" option is the stored plan value, so a recreated screen shows it again. Nothing is
 * kept locally: the proposal view model and the Trainer tab re-read the plan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class DietBreakRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val plans = FakeCoachPlanRepository()
    private val coach = FakeCoachRepository(Result.success(coachValues(provisional = false)))
    private val saved = SavedStateHandle()
    private var current by mutableStateOf(PlanProposalViewModel(plans, coach, MutableClock(), saved))

    @Test
    fun proposalWithTheOptionChanged_keepsTheSwitchOnAfterRecreation() {
        plans.current = Result.success(coachPlan(phaseOptions = PhaseOptions(0.5, dietBreak = true)))
        current = PlanProposalViewModel(plans, coach, MutableClock(), saved)
        rule.setContent {
            AppTheme {
                PlanProposalRoot(
                    onBack = {},
                    onOpenBeforeStart = {},
                    onFix = {},
                    viewModel = current,
                    connectivity = FakeConnectivityObserver(),
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("plan_proposal_diet_break_switch").performScrollTo().assertIsOn()

        current = PlanProposalViewModel(plans, coach, MutableClock(), saved)
        rule.waitForIdle()

        rule.onNodeWithTag("plan_proposal_diet_break_switch").performScrollTo().assertIsOn()
    }

    @Test
    fun trainerPlanSwitch_rereadFromThePlan_showsTheStoredValueAfterRecreation() {
        val plan = coachPlan(
            status = PlanStatus.Accepted,
            phaseOptions = PhaseOptions(0.5, dietBreak = true),
            dietBreakWeek = DietBreakWeek(2400, 160, 280, 70),
        )
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                TrainerWithBoxes(TrainerUiState.Content(plan = PlanSummary.Accepted(plan)), flowOf(true), {})
            }
        }
        rule.openTrainerPlan()
        rule.onNodeWithTag("trainer_plan_diet_break_switch").assertIsOn()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_plan_diet_break_switch").assertIsOn()
    }
}
