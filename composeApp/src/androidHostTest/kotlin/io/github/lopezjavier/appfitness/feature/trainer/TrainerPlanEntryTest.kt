package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CalorieChange
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-69: the plan entry and the calorie-change card on the Trainer tab. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class TrainerPlanEntryTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainerEvent>()
    private var opened = 0

    private fun content(plan: PlanSummary, change: CalorieChange? = null) =
        TrainerUiState.Content(coachValues(provisional = false), plan = plan, calorieChange = change)

    private fun show(state: TrainerUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme {
                TrainerScreen(state, flowOf(online), { events += it }, onOpenPlan = { opened++ })
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun withoutAPlan_crearPlanOpensTheProposal() {
        show(content(PlanSummary.None))

        rule.onNodeWithText("Tu plan").assertExists()
        tap("trainer_create_plan")

        assertEquals(1, opened)
    }

    @Test
    fun offline_crearPlanIsDisabledWithTheHint() {
        show(content(PlanSummary.None), online = false)

        rule.onNodeWithTag("trainer_create_plan").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede crear el plan").assertExists()
    }

    @Test
    fun withAPendingProposal_aSummaryCardOpensIt() {
        show(content(PlanSummary.Proposed(coachPlan())))

        rule.onNodeWithTag("trainer_create_plan").assertDoesNotExist()
        rule.onNodeWithText("Plan pendiente").assertExists()
        rule.onNodeWithText("Revísalo y acepta o descarta.").assertExists()
        tap("trainer_plan_card")

        assertEquals(1, opened)
    }

    @Test
    fun withAnAcceptedPlan_theSummaryShowsCaloriesRateAndOutcomes() {
        show(content(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted))))

        rule.onNodeWithText("Plan activo").assertExists()
        rule.onNodeWithText("2.000 kcal al día · −0,4 kg/sem").assertExists()
        rule.onNodeWithText("1 objetivo del plan").assertExists()
        rule.onNodeWithText("Editado por ti").assertDoesNotExist()
    }

    @Test
    fun anAcceptedPlanEditedByTheUser_isMarked() {
        show(content(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted, editedByUser = true))))

        rule.onNodeWithText("Editado por ti").assertExists()
    }

    @Test
    fun calorieChangeCard_showsTheChangeItsReasonAndTheDataUsed() {
        show(content(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)), calorieChange()))

        rule.onNodeWithTag("trainer_calorie_card").assertExists()
        rule.onNodeWithText("Ajuste de calorías").assertExists()
        rule.onNodeWithText("De 2.000 a 2.100 kcal al día").assertExists()
        rule.onNodeWithText("Tu peso baja más rápido de lo previsto.").assertExists()
        rule.onNodeWithText("Según tu tendencia: −0,1 kg/sem (margen ±0,1)").assertExists()
    }

    @Test
    fun calorieChangeCard_isHiddenWithoutAPendingChange() {
        show(content(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted))))

        rule.onNodeWithTag("trainer_calorie_card").assertDoesNotExist()
    }

    @Test
    fun aceptar_onTheCalorieCard_sendsTheDecision() {
        show(content(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)), calorieChange()))

        rule.onNodeWithTag("trainer_calorie_card_accept").assertIsEnabled()
        tap("trainer_calorie_card_accept")

        assertEquals(listOf<TrainerEvent>(TrainerEvent.DecideCalorieChange(accept = true)), events)
    }

    @Test
    fun descartar_onTheCalorieCard_sendsTheDiscard() {
        show(content(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)), calorieChange()))

        tap("trainer_calorie_card_discard")

        assertEquals(listOf<TrainerEvent>(TrainerEvent.DecideCalorieChange(accept = false)), events)
    }

    @Test
    fun aceptar_offline_isDisabledWithTheHint() {
        show(content(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)), calorieChange()), online = false)

        rule.onNodeWithTag("trainer_calorie_card_accept").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede aceptar el cambio").assertExists()
    }

    @Test
    fun acceptingTheCalorieChange_throughTheRoute_sendsItAndRemovesTheCard() {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        plans.pending = Result.success(calorieChange())
        val model = TrainerViewModel(
            FakeCoachRepository(),
            MutableClock(),
            SavedStateHandle(),
            plans = plans,
        )
        rule.setContent {
            AppTheme { TrainerRoute(viewModel = model, connectivity = FakeConnectivityObserver()) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_calorie_card").assertExists()

        tap("trainer_calorie_card_accept")

        assertEquals(listOf("change-1" to true), plans.decisions)
        rule.onNodeWithTag("trainer_calorie_card").assertDoesNotExist()
        assertTrue(plans.pendingReads >= 1)
    }
}
