package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.GoalChecklist
import io.github.lopezjavier.appfitness.domain.usecase.GoalTargets
import io.github.lopezjavier.appfitness.domain.usecase.NutritionGoalsChecklist
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-72: the N1-N4 rows under "Tu plan", only after the user edited the goals. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TrainerGoalsChecklistTest {
    @get:Rule
    val rule = createComposeRule()

    private fun checklist(kcal: Int, proteinG: Int, fatG: Int): GoalChecklist = NutritionGoalsChecklist(
        targets = GoalTargets(kcal, proteinG, fatG),
        weightKg = 80.0,
        sex = Sex.Male,
        phase = Phase.Maintenance,
        expenditureKcal = 2000.0,
    )

    private fun show(edited: Boolean, checklist: GoalChecklist?) {
        val plan = coachPlan(status = PlanStatus.Accepted, editedByUser = edited, phase = Phase.Maintenance)
        rule.setContent {
            AppTheme {
                TrainerScreen(
                    TrainerUiState.Content(plan = PlanSummary.Accepted(plan), goalsChecklist = checklist),
                    flowOf(true),
                    {},
                    actions = TrainerActions(),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun goalsNotEdited_showNoRows() {
        show(edited = false, checklist = checklist(1400, 100, 30))

        rule.onNodeWithTag("trainer_plan_goals_check").assertDoesNotExist()
    }

    @Test
    fun editedGoalsBelowTheMinimums_showFourFailingRowsWithTheirFigures() {
        show(edited = true, checklist = checklist(1400, 100, 30))

        rule.onNodeWithText("Tus objetivos").assertExists()
        rule.onNodeWithText("Tus calorías (1.400) están por debajo del mínimo de 1.500 kcal.").assertExists()
        rule.onNodeWithText("Proteína 100 g: menos de 1,6 g por kg (128 g).").assertExists()
        rule.onNodeWithText("Grasa 30 g: menos del 20 % de tus calorías (32 g).").assertExists()
        rule.onNodeWithTag("trainer_plan_goals_check_n2_rate").assertExists()
        rule.onNodeWithTag("trainer_plan_goals_check_all_ok").assertDoesNotExist()
    }

    @Test
    fun failingRow_exposesNoCumpleAsItsState() {
        show(edited = true, checklist = checklist(2000, 100, 60))

        val state = rule.onNodeWithTag("trainer_plan_goals_check_n3_protein", useUnmergedTree = false)
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals("No cumple", state)
    }

    @Test
    fun passingRowsNextToAFailingOne_keepTheirShortLabelAndCumple() {
        show(edited = true, checklist = checklist(2000, 100, 60))

        rule.onNodeWithText("Calorías por encima del mínimo").assertExists()
        rule.onNodeWithText("Grasa suficiente").assertExists()
        val state = rule.onNodeWithTag("trainer_plan_goals_check_n1_floor")
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals("Cumple", state)
    }

    @Test
    fun allPassing_showOnlyTheConfirmationLine() {
        show(edited = true, checklist = checklist(2000, 150, 60))

        rule.onNodeWithTag("trainer_plan_goals_check_all_ok").assertExists()
        rule.onNodeWithText("Tus objetivos cumplen lo recomendado.").assertExists()
        rule.onNodeWithTag("trainer_plan_goals_check_n1_floor").assertDoesNotExist()
    }

    @Test
    fun gramsRenderAsWholeNumbers() {
        show(edited = true, checklist = checklist(2000, 100, 60))

        rule.onNodeWithText("Proteína 100 g: menos de 1,6 g por kg (128 g).").assertExists()
    }
}
