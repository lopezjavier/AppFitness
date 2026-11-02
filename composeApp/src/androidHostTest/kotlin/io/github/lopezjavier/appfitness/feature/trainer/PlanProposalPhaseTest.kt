package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseOptions
import io.github.lopezjavier.appfitness.domain.model.PhaseReason
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-82: the phase row, the cut rate and the diet break on the plan proposal, one test per scenario. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class PlanProposalPhaseTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<PlanProposalEvent>()

    private fun show(plan: CoachPlan, online: Boolean = true) {
        rule.setContent {
            AppTheme {
                PlanProposalScreen(
                    state = PlanProposalUiState.Proposal(plan),
                    isOnline = flowOf(online),
                    onEvent = { events += it },
                    onBack = {},
                    onOpenBeforeStart = {},
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun cutPlan_showsTheLabelAndTheReasonFromTheIntention() {
        show(coachPlan(phase = Phase.Cut))

        rule.onNodeWithTag("plan_proposal_phase_row").assertExists()
        rule.onNodeWithText("Definición").assertExists()
        rule.onNodeWithText("Tu objetivo es perder grasa.").assertExists()
    }

    @Test
    fun legacyRegainPlan_readsAsMantenimiento_andNeverShowsRecuperar() {
        show(coachPlan(phase = Phase.Maintenance, phaseReason = PhaseReason.Intention))

        rule.onNodeWithTag("plan_proposal_phase_row").assertExists()
        rule.onNodeWithText("Mantenimiento").assertExists()
        rule.onNodeWithText("Recuperar", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Vuelves después de un parón", substring = true).assertDoesNotExist()
    }

    @Test
    fun phaseHelp_opensAnInfoSheetWithTheExplanation() {
        show(coachPlan(phase = Phase.Gain, phaseReason = PhaseReason.Intention))

        rule.onNodeWithTag("plan_proposal_phase_help").performClick()
        rule.waitForIdle()

        rule.onNodeWithText(
            "Comes algo más de lo que gastas para ganar músculo poco a poco, sin sumar grasa de más.",
        ).assertExists()
    }

    @Test
    fun cutPlan_selectsTheStoredRateInTheSelector() {
        show(coachPlan(phaseOptions = PhaseOptions(0.75, dietBreak = false)))

        rule.onNodeWithTag("plan_proposal_cut_rate_selector").assertExists()
        rule.onNodeWithTag("plan_proposal_cut_rate_option_0.75").assertIsSelected()
    }

    @Test
    fun pickingARate_sendsTheEvent() {
        show(coachPlan())

        rule.onNodeWithTag("plan_proposal_cut_rate_option_1.0").performClick()

        assertEquals(listOf<PlanProposalEvent>(PlanProposalEvent.CutRatePicked(1.0)), events)
    }

    @Test
    fun togglingTheDietBreak_sendsTheEvent() {
        show(coachPlan())

        rule.onNodeWithTag("plan_proposal_diet_break_switch").performClick()

        assertEquals(listOf<PlanProposalEvent>(PlanProposalEvent.DietBreakToggled(true)), events)
    }

    @Test
    fun dietBreakHelp_explainsTheOptionalWeek() {
        show(coachPlan())

        rule.onNodeWithTag("plan_proposal_diet_break_help").performClick()
        rule.waitForIdle()

        rule.onNodeWithText(
            "Opcional: la última semana de cada bloque comes lo que gastas. En los estudios se pierde la misma " +
                "grasa y se pasa menos hambre, pero la definición dura más.",
        ).assertExists()
    }

    @Test
    fun nonCutPlan_hasNeitherTheRateSelectorNorTheSwitch() {
        show(coachPlan(phase = Phase.Maintenance))

        rule.onNodeWithTag("plan_proposal_phase_row").assertExists()
        rule.onNodeWithTag("plan_proposal_cut_rate_selector").assertDoesNotExist()
        rule.onNodeWithTag("plan_proposal_diet_break_switch").assertDoesNotExist()
    }

    @Test
    fun offline_disablesTheOptionsAndSaysWhy() {
        show(coachPlan(), online = false)

        rule.onNodeWithTag("plan_proposal_cut_rate_option_1.0").assertIsNotEnabled()
        rule.onNodeWithTag("plan_proposal_diet_break_switch").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede cambiar el ritmo").assertExists()
        rule.onNodeWithText("Sin conexión: no se puede cambiar la semana de descanso").assertExists()
    }

    @Test
    fun whileSaving_theRowShowsProgress() {
        rule.setContent {
            AppTheme {
                PlanProposalScreen(
                    state = PlanProposalUiState.Proposal(coachPlan(), optionsSaving = true),
                    isOnline = flowOf(true),
                    onEvent = {},
                    onBack = {},
                    onOpenBeforeStart = {},
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("plan_proposal_options_progress").assertExists()
    }

    @Test
    fun pickingARate_updatesTheCaloriesInPlaceWithoutAskingForANewPlan() {
        val plans = FakeCoachPlanRepository()
        val cheaper = coachPlan(phaseOptions = PhaseOptions(1.0, dietBreak = false))
        plans.optionsResult = Result.success(cheaper.copy(nutrition = cheaper.nutrition.copy(targetKcal = 1800)))
        plans.current = Result.success(coachPlan())
        val model = PlanProposalViewModel(
            plans,
            FakeCoachRepository(Result.success(coachValues(provisional = false))),
            MutableClock(),
            SavedStateHandle(),
        )
        rule.setContent {
            AppTheme {
                PlanProposalRoot(
                    onBack = {},
                    onOpenBeforeStart = {},
                    viewModel = model,
                    connectivity = FakeConnectivityObserver(),
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("plan_proposal_cut_rate_option_1.0").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("1.800 kcal al día").assertExists()
        assertEquals(1, plans.optionsCalls.size)
        assertEquals(0, plans.requests.size)
    }

    @Test
    fun failedUpdate_keepsThePreviousPlan() {
        val plans = FakeCoachPlanRepository()
        plans.optionsResult = Result.failure(IllegalStateException("boom"))
        plans.current = Result.success(coachPlan())
        val model = PlanProposalViewModel(
            plans,
            FakeCoachRepository(Result.success(coachValues(provisional = false))),
            MutableClock(),
            SavedStateHandle(),
        )
        rule.setContent {
            AppTheme {
                PlanProposalRoot(
                    onBack = {},
                    onOpenBeforeStart = {},
                    viewModel = model,
                    connectivity = FakeConnectivityObserver(),
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("plan_proposal_cut_rate_option_1.0").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("2.000 kcal al día").assertExists()
        rule.onNodeWithTag("plan_proposal_cut_rate_option_0.5").assertIsSelected()
    }
}
