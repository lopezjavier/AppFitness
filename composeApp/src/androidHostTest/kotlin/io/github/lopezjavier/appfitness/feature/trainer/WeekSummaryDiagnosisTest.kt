package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.NutritionDiagnosis
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val NBSP = 160.toChar().toString()

/** US-113 / US-114: the "why is my weight not moving" card of the weekly summary, one test per Gherkin scenario. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class WeekSummaryDiagnosisTest {
    @get:Rule
    val rule = createComposeRule()

    private fun state(
        diagnosis: NutritionDiagnosis?,
        withProposal: Boolean = false,
        decisionStatus: ProposalStatus = ProposalStatus.Pending,
    ) = WeekSummaryUiState.Content(
        weekSummary(
            nutrition = weekNutrition(
                diagnosis = diagnosis,
                decisions = if (withProposal) listOf(weekDecision(decisionStatus)) else emptyList(),
            ),
        ),
        emptyMap(),
    )

    private fun show(state: WeekSummaryUiState) {
        rule.setContent { AppTheme { WeekSummaryScreen(state, flowOf(true), {}, {}) } }
        rule.waitForIdle()
    }

    private fun assertLines(cause: String, action: String) {
        rule.onNodeWithTag("week_summary_section_diagnosis").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("week_summary_diagnosis_cause").assertTextEquals(cause)
        rule.onNodeWithTag("week_summary_diagnosis_action").assertTextEquals(action)
    }

    @Test
    fun loggingCause_isTheOnlyCauseShown() {
        show(state(NutritionDiagnosis.LoggingFood(3, LocalDate(2027, 1, 4))))

        assertLines(
            "Faltan registros: 3 de 7 días de comida completos en la semana del 4 ene. " +
                "Sin ellos no podemos saber por qué tu peso no va como estaba previsto.",
            "Registra lo que comes al menos 5${NBSP}días por semana.",
        )
        rule.onAllNodesWithTag("week_summary_diagnosis_cause").assertCountEquals(1)
    }

    @Test
    fun weighInCause_namesTheWeighIns() {
        show(state(NutritionDiagnosis.LoggingWeighIn(2, LocalDate(2027, 1, 4))))

        assertLines("Faltan pesajes: 2 en la semana del 4 ene.", "Pésate al menos 3${NBSP}días por semana.")
    }

    @Test
    fun caloriesCause_withWeekendVariant_addsTheWeekendLine() {
        show(state(NutritionDiagnosis.Calories(2350, 2000, 350, above = true, weekend = true)))

        rule.onNodeWithTag("week_summary_diagnosis_cause").performScrollTo()
            .assertTextContains(
                "Registraste de media 2.350 kcal al día, 350 kcal más que tu objetivo.",
                substring = true,
            )
            .assertTextContainsIgnoringNbsp("La mayor parte llega el fin de semana.")
        rule.onNodeWithTag("week_summary_diagnosis_action")
            .assertTextEqualsIgnoringNbsp("Intenta que el fin de semana se parezca al resto de la semana.")
    }

    @Test
    fun plateau_showsTrendRangeAndReassurance() {
        show(state(NutritionDiagnosis.Plateau(-0.02, -0.12, 0.09)))

        rule.onNodeWithTag("week_summary_diagnosis_cause").performScrollTo()
            .assertTextContainsIgnoringNbsp("Meseta: tu peso apenas ha cambiado en 2 semanas")
            .assertTextContainsIgnoringNbsp("−0,02 kg por semana, entre −0,12 y 0,09")
            .assertTextContainsIgnoringNbsp("No es un fracaso")
    }

    @Test
    fun loggingGap_isNotCalledAPlateau() {
        show(state(NutritionDiagnosis.LoggingFood(4, LocalDate(2027, 1, 4))))

        rule.onNodeWithTag("week_summary_diagnosis_cause").performScrollTo()
            .assertTextContains("Faltan registros", substring = true)
        rule.onAllNodesWithTag("week_summary_diagnosis_cause").assertCountEquals(1)
    }

    @Test
    fun noCause_keepsThePlanWhenThereIsNoProposal() {
        show(state(NutritionDiagnosis.NoCause))

        assertLines("En tus datos no vemos una causa clara.", "Sigue con el plan: lo revisamos la semana que viene.")
    }

    @Test
    fun noCause_pointsToTheProposalWhenThereIsOne() {
        show(state(NutritionDiagnosis.NoCause, withProposal = true))

        rule.onNodeWithTag("week_summary_diagnosis_action").performScrollTo()
            .assertTextEquals("Revisa la propuesta de calorías de esta semana.")
    }

    @Test
    fun plateau_withADeclinedDecision_keepsThePlanInsteadOfPointingToAProposal() {
        show(
            state(
                NutritionDiagnosis.Plateau(-0.02, -0.12, 0.09),
                withProposal = true,
                decisionStatus = ProposalStatus.Declined,
            ),
        )

        rule.onNodeWithTag("week_summary_diagnosis_action").performScrollTo()
            .assertTextEquals("Sigue con el plan: lo revisamos la semana que viene.")
    }

    @Test
    fun noDiagnosis_drawsNoCard() {
        show(state(null))

        rule.onAllNodesWithTag("week_summary_section_diagnosis").assertCountEquals(0)
    }

    @Test
    fun help_opensItsSentence() {
        show(state(NutritionDiagnosis.NoCause))

        rule.onNodeWithTag("week_summary_diagnosis_help").performScrollTo().performClick()

        rule.onNodeWithTag("week_summary_diagnosis_help_sheet_body")
            .assertTextContains("dos semanas seguidas", substring = true)
    }

    @Test
    fun recreation_keepsTheCard() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            AppTheme { WeekSummaryScreen(state(NutritionDiagnosis.NoCause), flowOf(true), {}, {}) }
        }
        rule.onNodeWithTag("week_summary_section_diagnosis").performScrollTo().assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("week_summary_section_diagnosis").assertIsDisplayed()
        rule.onNodeWithTag("week_summary_diagnosis_cause")
            .assertTextEquals("En tus datos no vemos una causa clara.")
    }
}
