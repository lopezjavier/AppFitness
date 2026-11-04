package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseReason
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-72 / ADR-98: protein as the headline macro, the fat minimum, carbs as the rest and the underweight note. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class PlanProposalMacrosTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(plan: CoachPlan) {
        rule.setContent {
            AppTheme {
                PlanProposalScreen(
                    state = PlanProposalUiState.Proposal(plan),
                    isOnline = flowOf(true),
                    onEvent = {},
                    onBack = {},
                    onOpenBeforeStart = {},
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun protein_isTheHeadlineMacroInGrams() {
        show(coachPlan())

        rule.onNodeWithTag("plan_proposal_macro_protein").assertExists()
        rule.onNodeWithText("Proteína: 150 g").assertExists()
    }

    @Test
    fun fat_showsItsMinimumFromTwentyPercentOfTheCalories() {
        show(coachPlan())

        // 20 % of 2000 kcal is 400 kcal, 44.4 g, rounded up to 45 g.
        rule.onNodeWithText("Grasa: 60 g (mínimo 45 g)").assertExists()
    }

    @Test
    fun carbs_areTheRest() {
        show(coachPlan())

        rule.onNodeWithText("Carbohidratos: el resto, 200 g").assertExists()
    }

    @Test
    fun underweightPlan_showsMaintenanceWithTheNote() {
        show(coachPlan(phase = Phase.Maintenance, phaseReason = PhaseReason.Underweight))

        rule.onNodeWithText("Mantenimiento").assertExists()
        rule.onNodeWithText("Tu peso está por debajo del rango saludable, así que mantienes.").assertExists()
        rule.onNodeWithTag("plan_proposal_underweight_note").assertExists()
        rule.onNodeWithText(
            "Tu peso está por debajo del rango saludable para tu altura: el plan no busca perder peso.",
        ).assertExists()
    }

    @Test
    fun regularPlan_hasNoUnderweightNote() {
        show(coachPlan())

        rule.onNodeWithTag("plan_proposal_underweight_note").assertDoesNotExist()
    }
}
