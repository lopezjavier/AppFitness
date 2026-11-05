package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-99: the calorie proposal card with "Aplicar" / "Ahora no" and its six copy variants. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario or copy variant.
class TrainerCalorieProposalScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainerEvent>()

    private fun proposal(from: Int, to: Int, rate: Double?) =
        calorieProposal().copy(fromKcal = from, toKcal = to, rateKgWeek = rate)

    private fun state(proposal: CalorieProposal?, deciding: Boolean = false) = TrainerUiState.Content(
        plan = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)),
        calorieProposal = proposal,
        decidingChange = deciding,
    )

    private fun show(state: TrainerUiState, online: Boolean = true) {
        rule.setContent { AppTheme { TrainerScreen(state, flowOf(online), { events += it }) } }
        rule.waitForIdle()
    }

    private fun assertCopy(proposal: CalorieProposal, text: String) {
        show(state(proposal))
        rule.onNodeWithText(text).assertExists()
    }

    @Test
    fun cutting_andCaloriesDown_saysWeightFellLessThanPlanned() = assertCopy(
        proposal(2000, 1900, -0.5),
        "Te proponemos bajar 100 kcal: en las últimas 2 semanas tu peso ha bajado menos de lo previsto.",
    )

    @Test
    fun cutting_andCaloriesUp_saysWeightFellMoreThanPlanned() = assertCopy(
        proposal(2000, 2100, -0.5),
        "Te proponemos subir 100 kcal: en las últimas 2 semanas tu peso ha bajado más de lo previsto.",
    )

    @Test
    fun gaining_andCaloriesDown_saysWeightRoseMoreThanPlanned() = assertCopy(
        proposal(2800, 2700, 0.25),
        "Te proponemos bajar 100 kcal: en las últimas 2 semanas tu peso ha subido más de lo previsto.",
    )

    @Test
    fun gaining_andCaloriesUp_saysWeightRoseLessThanPlanned() = assertCopy(
        proposal(2800, 2900, 0.25),
        "Te proponemos subir 100 kcal: en las últimas 2 semanas tu peso ha subido menos de lo previsto.",
    )

    @Test
    fun holding_andCaloriesDown_saysWeightRoseAndGoalIsToHold() = assertCopy(
        proposal(2500, 2400, 0.0),
        "Te proponemos bajar 100 kcal: en las últimas 2 semanas tu peso ha subido y tu objetivo es mantenerlo.",
    )

    @Test
    fun holding_andCaloriesUp_saysWeightFellAndGoalIsToHold() = assertCopy(
        proposal(2500, 2600, 0.0),
        "Te proponemos subir 100 kcal: en las últimas 2 semanas tu peso ha bajado y tu objetivo es mantenerlo.",
    )

    @Test
    fun theCard_offersAplicarAndAhoraNo_neverAceptarOrDescartar() {
        show(state(proposal(2000, 1900, -0.5)))

        rule.onNodeWithText("Aplicar").assertExists()
        rule.onNodeWithText("Ahora no").assertExists()
        rule.onNodeWithText("Aceptar").assertDoesNotExist()
        rule.onNodeWithText("Descartar").assertDoesNotExist()
    }

    @Test
    fun withoutAProposal_noCardIsShown() {
        show(state(null))

        rule.onNodeWithTag("trainer_calorie_card").assertDoesNotExist()
    }

    @Test
    fun aplicarAndAhoraNo_emitTheirEvents() {
        show(state(proposal(2000, 1900, -0.5)))

        rule.onNodeWithTag("trainer_calorie_card_accept").assertIsEnabled().performClick()
        rule.onNodeWithTag("trainer_calorie_card_discard").performClick()

        assertEquals(
            listOf<TrainerEvent>(TrainerEvent.ApplyCalorieProposal, TrainerEvent.DeclineCalorieProposal),
            events,
        )
    }

    @Test
    fun offline_disablesAplicar_andShowsTheHintForBothActions() {
        show(state(proposal(2000, 1900, -0.5)), online = false)

        rule.onNodeWithTag("trainer_calorie_card_accept").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede aplicar el cambio o guardar tu decisión").assertExists()
    }

    @Test
    fun whileDeciding_aplicarIsNotTappable() {
        show(state(proposal(2000, 1900, -0.5), deciding = true))

        rule.onNodeWithTag("trainer_calorie_card_accept").assertIsNotEnabled()
    }

    @Test
    fun afterRecreation_theCardAndItsActionsRemain() {
        val tester = StateRestorationTester(rule)
        val content = state(proposal(2000, 1900, -0.5))
        tester.setContent { AppTheme { TrainerScreen(content, flowOf(true), { events += it }) } }
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_calorie_card").assertExists()
        rule.onNodeWithTag("trainer_calorie_card_accept").assertIsEnabled().performClick()
        assertEquals(listOf<TrainerEvent>(TrainerEvent.ApplyCalorieProposal), events)
    }
}
