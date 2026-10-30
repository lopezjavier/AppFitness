package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.PlanLimit
import io.github.lopezjavier.appfitness.domain.model.PlanOutcome
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-69 and US-68: the plan proposal screen, one test per Gherkin scenario (display, gate, accept, discard). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class PlanProposalScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<PlanProposalEvent>()
    private var backs = 0

    private fun proposal(confirmed: Boolean = true, plan: CoachPlan = coachPlan(), dataChanged: Boolean = false) =
        PlanProposalUiState.Proposal(plan, coachValues(provisional = !confirmed), dataChanged = dataChanged)

    private fun show(state: PlanProposalUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme {
                PlanProposalScreen(state, flowOf(online), { events += it }, { backs++ })
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun weightLossPlan_showsCaloriesMacrosAndWeeklyRate() {
        show(proposal())

        rule.onNodeWithText("2.000 kcal al día").assertExists()
        rule.onNodeWithTag("plan_proposal_macro_protein").assertExists()
        rule.onNodeWithText("150 g").assertExists()
        rule.onNodeWithText("200 g").assertExists()
        rule.onNodeWithText("60 g").assertExists()
        rule.onNodeWithText("−0,4 kg/sem").assertExists()
        rule.onNodeWithTag("trainer_card").assertExists()
    }

    @Test
    fun weeklyRate_hasAHelpSheet() {
        show(proposal())

        tap("plan_proposal_rate_help")

        rule.onNodeWithText(
            "Es la variación de peso por semana que busca el plan. Se ajusta a tu tendencia real.",
        ).assertExists()
    }

    @Test
    fun calorieFloor_noteIsShownWhenTheFloorLimitsThePlan() {
        show(proposal(plan = coachPlan(limitedBy = PlanLimit.Floor)))

        rule.onNodeWithTag("plan_proposal_floor_note").assertExists()
        rule.onNodeWithText(
            "Tu plan se queda en 1.200 kcal, el mínimo recomendado. Un ritmo más rápido no sería seguro.",
        ).assertExists()
    }

    @Test
    fun calorieFloor_noteIsHiddenWhenNothingLimitsThePlan() {
        show(proposal())

        rule.onNodeWithTag("plan_proposal_floor_note").assertDoesNotExist()
    }

    @Test
    fun outcomes_showTheValueTheDeadlineAndTheReasonInAnInfoSheet() {
        show(proposal())

        rule.onNodeWithText("Objetivos del plan").assertExists()
        rule.onNodeWithText("Peso: 72,5 kg").assertExists()
        rule.onNodeWithText("Para el 16 de noviembre").assertExists()
        tap("plan_proposal_outcome_help_weight")
        rule.onNodeWithText("Por qué este objetivo").assertExists()
        rule.onNodeWithText("Bajar de forma gradual.").assertExists()
    }

    @Test
    fun strengthPlanWithoutAWeightOutcome_showsOnlyItsStrengthOutcome() {
        val strength = PlanOutcome(OutcomeKind.E1rm, "squat", 100.0, "Progresión lineal.", LocalDate(2026, 11, 16))
        show(proposal(plan = coachPlan(weightTarget = null).copy(outcomes = listOf(strength))))

        rule.onNodeWithTag("plan_proposal_outcome_weight").assertDoesNotExist()
        rule.onNodeWithTag("plan_proposal_outcome_squat").assertExists()
        rule.onNodeWithText("Fuerza (e1RM): 100,0 kg").assertExists()
    }

    @Test
    fun outcomesAreOptional_theHeaderIsHiddenWithoutThem() {
        show(proposal(plan = coachPlan(weightTarget = null)))

        rule.onNodeWithText("Objetivos del plan").assertDoesNotExist()
        rule.onNodeWithTag("plan_proposal_outcomes").assertDoesNotExist()
    }

    @Test
    fun proteinDistribution_isShownAsGuidance() {
        show(proposal())

        rule.onNodeWithTag("plan_proposal_distribution").assertExists()
        rule.onNodeWithText("Reparte la proteína: unos 30 g por comida, en 4 comidas o más.").assertExists()
    }

    @Test
    fun withUnconfirmedValues_acceptIsDisabledWithItsReason_thenEnabledAfterConfirmar() {
        var state by mutableStateOf<PlanProposalUiState>(proposal(confirmed = false))
        rule.setContent {
            AppTheme {
                PlanProposalScreen(
                    state = state,
                    isOnline = flowOf(true),
                    onEvent = {
                        events += it
                        if (it == PlanProposalEvent.Confirm) state = proposal(confirmed = true)
                    },
                    onBack = {},
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("plan_proposal_accept").assertIsNotEnabled()
        rule.onNodeWithText("Confirma tus valores para aceptar el plan.").assertExists()

        tap("trainer_confirm_button")

        rule.onNodeWithTag("plan_proposal_accept").assertIsEnabled()
        rule.onNodeWithTag("plan_proposal_accept_reason").assertDoesNotExist()
        assertEquals(listOf<PlanProposalEvent>(PlanProposalEvent.Confirm), events)
    }

    @Test
    fun whenTheServerRefusedForChangedData_theReasonAsksToConfirmAgain() {
        show(proposal(confirmed = false, dataChanged = true))

        rule.onNodeWithText(
            "Confirma tus valores para aceptar el plan. Tus datos han cambiado: vuelve a confirmar.",
        ).assertExists()
    }

    @Test
    fun accept_sendsTheAcceptEvent() {
        show(proposal())

        tap("plan_proposal_accept")

        assertEquals(listOf<PlanProposalEvent>(PlanProposalEvent.Accept), events)
    }

    @Test
    fun discard_sendsTheDiscardEvent_withoutAConfirmationDialog() {
        show(proposal())

        tap("plan_proposal_discard")

        assertEquals(listOf<PlanProposalEvent>(PlanProposalEvent.Discard), events)
    }

    @Test
    fun theScreenHasNoInputFieldsForTargetNumbers() {
        show(proposal())

        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun whileTheModelAnswers_aProgressStateIsShownInsteadOfASkeleton() {
        show(PlanProposalUiState.Requesting)

        rule.onNodeWithTag("plan_proposal_progress").assertExists()
        rule.onNodeWithText("Preparando tu plan…").assertExists()
        rule.onNodeWithTag("plan_proposal_loading").assertDoesNotExist()
    }

    @Test
    fun whileTheStoredPlanLoads_aSkeletonIsShown() {
        show(PlanProposalUiState.Loading)

        rule.onNodeWithTag("plan_proposal_loading").assertExists()
        rule.onNodeWithTag("plan_proposal_progress").assertDoesNotExist()
    }

    @Test
    fun monthlyLimit_showsTheLimitMessageAndNoProposal() {
        show(PlanProposalUiState.Failed(PlanFailureKind.Limit(CoachFailure.Scope.User)))

        rule.onNodeWithTag("plan_proposal_limit").assertExists()
        rule.onNodeWithText("Has alcanzado el límite mensual de IA. Vuelve a probar el mes que viene.").assertExists()
        rule.onNodeWithTag("plan_proposal_accept").assertDoesNotExist()
    }

    @Test
    fun failedRequest_offersReintentar() {
        show(PlanProposalUiState.Failed(PlanFailureKind.Other))

        rule.onNodeWithText("No se pudo crear el plan. Inténtalo de nuevo.").assertExists()
        tap("plan_proposal_retry")

        assertEquals(listOf<PlanProposalEvent>(PlanProposalEvent.Retry), events)
    }

    @Test
    fun interruptedRequest_saysNoPlanArrived_andOffersReintentar() {
        show(PlanProposalUiState.Failed(PlanFailureKind.Interrupted))

        rule.onNodeWithText("No se recibió el plan. Inténtalo de nuevo.").assertExists()
        rule.onNodeWithTag("plan_proposal_retry").assertExists()
    }

    @Test
    fun offline_showsTheOfflineState() {
        show(PlanProposalUiState.Failed(PlanFailureKind.Offline), online = false)

        rule.onNodeWithTag("plan_proposal_offline").assertExists()
        rule.onNodeWithText("Sin conexión").assertExists()
    }

    @Test
    fun offlineWithAProposal_disablesAcceptAndDiscardWithTheHint() {
        show(proposal(), online = false)

        rule.onNodeWithTag("plan_proposal_accept").assertIsNotEnabled()
        rule.onNodeWithTag("plan_proposal_discard").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede aceptar el plan").assertExists()
    }

    @Test
    fun backButton_goesBack() {
        show(proposal())

        tap("plan_proposal_back")

        assertEquals(1, backs)
    }

    // The stateful route over the real view model: the Gherkin "Accepting writes the nutrition goal".

    private val plans = FakeCoachPlanRepository()
    private val coach = FakeCoachRepository(Result.success(coachValues(provisional = false)))

    private fun showRoute() {
        plans.current = Result.success(coachPlan())
        val model = PlanProposalViewModel(plans, coach, MutableClock(), SavedStateHandle())
        rule.setContent {
            AppTheme {
                PlanProposalRoot(onBack = { backs++ }, viewModel = model, connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun acceptingTheStoredPlan_writesItAndLeavesTheScreen() {
        showRoute()

        tap("plan_proposal_accept")

        assertEquals(listOf("plan-1"), plans.acceptCalls.map { it.first })
        assertEquals(1, backs)
    }

    @Test
    fun discardingTheStoredPlan_leavesTheScreen() {
        showRoute()

        tap("plan_proposal_discard")

        assertEquals(listOf("plan-1"), plans.discardCalls)
        assertEquals(1, backs)
    }

    @Test
    fun withoutAStoredPlan_openingTheScreenAsksTheCoachOnce_andShowsTheProposal() {
        val model = PlanProposalViewModel(plans, coach, MutableClock(), SavedStateHandle())
        rule.setContent {
            AppTheme {
                PlanProposalRoot(onBack = {}, viewModel = model, connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitForIdle()

        assertEquals(1, plans.requests.size)
        rule.onNodeWithText("2.000 kcal al día").assertExists()
        assertTrue(plans.acceptCalls.isEmpty())
    }
}
