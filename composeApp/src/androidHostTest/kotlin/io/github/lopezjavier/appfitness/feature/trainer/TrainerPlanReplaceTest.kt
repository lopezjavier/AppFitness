package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-78: "Crear plan nuevo" opens "Antes de empezar" with the plan it replaces (the notice lives there, no sheet). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TrainerPlanReplaceTest {
    @get:Rule
    val rule = createComposeRule()

    private val starts = mutableListOf<Triple<BeforeStartMode, String?, String?>>()

    private val accepted get() = PlanSummary.Accepted(coachPlan(id = "plan-7", status = PlanStatus.Accepted))
    private val pending get() = PlanSummary.Proposed(coachPlan(id = "plan-8"))

    private fun content(plan: PlanSummary) = TrainerUiState.Content(plan = plan)

    private fun show(state: TrainerUiState, online: Boolean = true, details: Boolean = true) {
        rule.setContent {
            AppTheme {
                TrainerWithDetails(
                    state,
                    flowOf(online),
                    {},
                    actions = TrainerActions(onOpenBeforeStart = { m, p, r -> starts += Triple(m, p, r) }),
                )
            }
        }
        rule.waitForIdle()
        if (details) rule.openTrainerDetails()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun withAnAcceptedPlan_crearPlanNuevoReplacesCrearPlan() {
        show(content(accepted))

        rule.onNodeWithTag("trainer_create_plan").assertDoesNotExist()
        rule.onNodeWithTag("trainer_create_plan_new").assertExists()
        rule.onNodeWithText("Crear plan nuevo").assertExists()
    }

    @Test
    fun withASavedBlock_crearPlanNuevoIsGone() {
        show(TrainerUiState.Content(plan = accepted, block = BlockSummary.Saved(coachBlock(saved = true))))

        rule.onNodeWithTag("trainer_create_plan_new").assertDoesNotExist()
        rule.onNodeWithText("Crear plan nuevo").assertDoesNotExist()
    }

    @Test
    fun withAPendingPlan_crearPlanNuevoIsShown() {
        show(content(pending))

        rule.onNodeWithTag("trainer_create_plan").assertDoesNotExist()
        rule.onNodeWithTag("trainer_create_plan_new").assertExists()
    }

    @Test
    fun withoutAPlan_thereIsNoCrearPlanNuevo() {
        show(content(PlanSummary.None), details = false)

        rule.onNodeWithTag("trainer_details_row").assertDoesNotExist()
        rule.onNodeWithTag("trainer_create_plan_new").assertDoesNotExist()
    }

    @Test
    fun offline_crearPlanNuevoIsDisabledWithTheHint() {
        show(content(accepted), online = false)

        rule.onNodeWithTag("trainer_create_plan_new").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede crear el plan").assertExists()
    }

    @Test
    fun tappingIt_opensAntesDeEmpezarReplacingTheAcceptedPlan_withoutASheet() {
        show(content(accepted))

        tap("trainer_create_plan_new")

        assertEquals(listOf(Triple(BeforeStartMode.Plan, null, "plan-7")), starts)
        rule.onNodeWithText("¿Crear un plan nuevo?").assertDoesNotExist()
    }

    @Test
    fun tappingIt_onAPendingPlan_replacesThePendingPlanId() {
        show(content(pending))

        tap("trainer_create_plan_new")

        assertEquals(listOf(Triple(BeforeStartMode.Plan, null, "plan-8")), starts)
    }
}
