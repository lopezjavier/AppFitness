package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DerivedSource
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SHEET_MESSAGE = "Sustituirá tu plan actual. Hasta que aceptes el nuevo, sigue valiendo el de ahora."

/** US-78 / US-68: "Crear plan nuevo" with its notice sheet, and the source chip of the total expenditure. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class TrainerPlanReplaceTest {
    @get:Rule
    val rule = createComposeRule()

    private val opened = mutableListOf<String?>()

    private val accepted get() = PlanSummary.Accepted(coachPlan(id = "plan-7", status = PlanStatus.Accepted))
    private val pending get() = PlanSummary.Proposed(coachPlan(id = "plan-8"))

    private fun content(plan: PlanSummary) = TrainerUiState.Content(coachValues(provisional = false), plan = plan)

    private fun show(state: TrainerUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme { TrainerScreen(state, flowOf(online), {}, onOpenPlan = { opened += it }) }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun contentWith(source: DerivedSource, kcal: Double? = 2400.0, reask: Boolean = false) =
        coachValues(provisional = false).let {
            TrainerUiState.Content(
                it.copy(totalExpenditure = it.totalExpenditure.copy(kcal = kcal, source = source, reask = reask)),
            )
        }

    @Test
    fun withAnAcceptedPlan_crearPlanNuevoReplacesCrearPlan() {
        show(content(accepted))

        rule.onNodeWithTag("trainer_create_plan").assertDoesNotExist()
        rule.onNodeWithTag("trainer_replace_plan").assertExists()
        rule.onNodeWithText("Crear plan nuevo").assertExists()
    }

    @Test
    fun withAPendingPlan_crearPlanNuevoIsShown() {
        show(content(pending))

        rule.onNodeWithTag("trainer_create_plan").assertDoesNotExist()
        rule.onNodeWithTag("trainer_replace_plan").assertExists()
    }

    @Test
    fun withoutAPlan_thereIsNoCrearPlanNuevo() {
        show(content(PlanSummary.None))

        rule.onNodeWithTag("trainer_replace_plan").assertDoesNotExist()
    }

    @Test
    fun offline_crearPlanNuevoIsDisabledWithTheHint() {
        show(content(accepted), online = false)

        rule.onNodeWithTag("trainer_replace_plan").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede crear el plan").assertExists()
    }

    @Test
    fun tappingIt_opensTheNoticeSheetWithoutNavigating() {
        show(content(accepted))

        tap("trainer_replace_plan")

        rule.onNodeWithTag("trainer_replace_sheet").assertExists()
        rule.onNodeWithText("¿Crear un plan nuevo?").assertExists()
        rule.onNodeWithText(SHEET_MESSAGE).assertExists()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun closingTheSheet_cancelsWithoutNavigating() {
        show(content(accepted))
        tap("trainer_replace_plan")

        tap("trainer_replace_sheet_close")

        rule.onNodeWithTag("trainer_replace_sheet").assertDoesNotExist()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun crearPlan_inTheSheet_opensTheProposalReplacingTheCurrentPlan() {
        show(content(accepted))
        tap("trainer_replace_plan")

        tap("trainer_replace_sheet_commit")

        assertEquals(listOf<String?>("plan-7"), opened)
        rule.onNodeWithTag("trainer_replace_sheet").assertDoesNotExist()
    }

    @Test
    fun crearPlan_onAPendingPlan_replacesThePendingPlanId() {
        show(content(pending))
        tap("trainer_replace_plan")

        tap("trainer_replace_sheet_commit")

        assertEquals(listOf<String?>("plan-8"), opened)
    }

    @Test
    fun theOpenSheet_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme { TrainerScreen(content(accepted), flowOf(true), {}, onOpenPlan = { opened += it }) }
        }
        rule.waitForIdle()
        tap("trainer_replace_plan")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_replace_sheet").assertExists()
        rule.onNodeWithText(SHEET_MESSAGE).assertExists()
    }

    @Test
    fun adaptiveExpenditure_showsTheAdaptativoChip() {
        show(contentWith(DerivedSource.Adaptive))

        rule.onNodeWithTag("trainer_source_total_expenditure").assertExists()
        rule.onNodeWithText("Adaptativo").assertExists()
        rule.onNodeWithText("Según lo que registras y tu peso", substring = true).assertExists()
    }

    @Test
    fun formulaExpenditure_showsTheEstimadoChip() {
        show(contentWith(DerivedSource.Formula))

        rule.onNodeWithText("Estimado").assertExists()
        rule.onNodeWithText("Según una fórmula con tu perfil", substring = true).assertExists()
    }

    @Test
    fun reaskedOrUnknownExpenditure_hasNoChip() {
        show(contentWith(DerivedSource.Formula, reask = true))
        rule.onNodeWithTag("trainer_source_total_expenditure").assertDoesNotExist()
    }

    @Test
    fun unknownKcal_hasNoChip() {
        show(contentWith(DerivedSource.Adaptive, kcal = null))
        rule.onNodeWithTag("trainer_source_total_expenditure").assertDoesNotExist()
    }
}
