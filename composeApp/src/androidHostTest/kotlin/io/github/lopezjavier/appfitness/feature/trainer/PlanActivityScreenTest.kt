package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanActivity
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.StepsBasis
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-79: the cardio and steps targets in the proposal and in "Tu plan", one test per scenario. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class PlanActivityScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val proposalEvents = mutableListOf<PlanProposalEvent>()
    private val trainerEvents = mutableListOf<TrainerEvent>()

    private fun proposal(activity: PlanActivity? = defaultActivity, edit: CardioEdit? = null) =
        PlanProposalUiState.Proposal(coachPlan(activity = activity), cardioEdit = edit)

    private fun showProposal(state: PlanProposalUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme {
                PlanProposalScreen(state, flowOf(online), { proposalEvents += it }, {}, {})
            }
        }
        rule.waitForIdle()
    }

    private fun showTrainer(state: TrainerUiState.Content) {
        rule.setContent {
            AppTheme { TrainerWithDetails(state, flowOf(true), { trainerEvents += it }) }
        }
        rule.waitForIdle()
        rule.openTrainerDetails()
    }

    private fun accepted(activity: PlanActivity?) =
        PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted, activity = activity))

    @Test
    fun targetsNextToTheCalories_showBothRowsWithTheirReasons() {
        showProposal(proposal())

        rule.onNodeWithText("2.000 kcal al día").assertExists()
        rule.onNodeWithText("Cardio · 150 min por semana · ritmo suave").assertExists()
        rule.onNodeWithText("Pasos · 7.000 al día").assertExists()
        rule.onNodeWithTag("plan_proposal_activity_cardio_help").performClick()
        rule.onNodeWithText(
            "Ritmo suave: respiras más fuerte, pero puedes hablar con frases completas.",
            substring = true,
        ).assertExists()
    }

    @Test
    fun cardioHelp_endsWithTheMedicalLine_andFatLossAddsItsLine() {
        val fat = activityCardioHelp("fat_loss")
        val strength = activityCardioHelp("strength")
        val recomposition = activityCardioHelp("recomposition")

        assertEquals(3, fat.size)
        assertEquals(3, strength.size)
        assertEquals(2, recomposition.size)
        assertEquals(fat.last(), strength.last())
        assertEquals(fat.last(), recomposition.last())
        assertEquals(false, fat[1] == strength[1])
    }

    @Test
    fun cardioHelp_hasNoMedicalLine() {
        showProposal(proposal())

        rule.onNodeWithTag("plan_proposal_activity_cardio_help").performClick()

        rule.onNodeWithText("consulta antes con tu médico", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("plan_proposal_activity_cardio_help_sheet_body").assertTextEquals(
            "La OMS recomienda al menos 150 minutos a la semana de actividad moderada para tu salud. " +
                "Ritmo suave: respiras más fuerte, pero puedes hablar con frases completas.",
            "En los estudios, cada 30 minutos semanales de cardio suman de media medio kilo menos; " +
                "la mayor parte de la pérdida viene de tu dieta.",
            "No sumamos calorías por el cardio: los relojes fallan al calcularlas y el cuerpo compensa una parte. " +
                "Tu plan se ajusta con tu peso real.",
        )
    }

    @Test
    fun theCardioRow_opensTheMinutesSheet() {
        showProposal(proposal())

        rule.onNodeWithTag("plan_proposal_activity_cardio_row").performClick()

        assertEquals(listOf<PlanProposalEvent>(PlanProposalEvent.OpenCardioEdit), proposalEvents)
    }

    @Test
    fun changingTheCardio_to90_showsTheNote_enablesSave_andKeepsTheCalories() {
        showProposal(proposal(edit = CardioEdit(draft = 90, saved = 150)))

        rule.onNodeWithTag("cardio_sheet_stepper_value").assertTextEquals("90 min")
        rule.onNodeWithTag("cardio_sheet_note").assertExists()
        rule.onNodeWithText("Está por debajo de lo que recomienda la OMS (150 min). Puedes dejarlo así.").assertExists()
        rule.onNodeWithTag("cardio_sheet_commit").assertIsEnabled()
        rule.onNodeWithText("2.000 kcal al día").assertExists()
    }

    @Test
    fun theStepper_sendsFiveMinuteSteps_andSaveIsDisabledUntilTheDraftChanges() {
        showProposal(proposal(edit = CardioEdit(draft = 150, saved = 150)))

        rule.onNodeWithTag("cardio_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("cardio_sheet_note").assertDoesNotExist()
        rule.onNodeWithTag("cardio_sheet_stepper_decrement").performClick()

        assertEquals(listOf<PlanProposalEvent>(PlanProposalEvent.CardioMinutesChanged(145)), proposalEvents)
    }

    @Test
    fun closingTheSheet_withX_dismissesWithoutSaving() {
        showProposal(proposal(edit = CardioEdit(draft = 90, saved = 150)))

        rule.onNodeWithTag("cardio_sheet_close").performClick()
        rule.waitForIdle()

        assertEquals(true, PlanProposalEvent.DismissCardioEdit in proposalEvents)
        assertEquals(false, PlanProposalEvent.SaveCardio in proposalEvents)
    }

    @Test
    fun offline_saveIsDisabledWithTheHint() {
        showProposal(proposal(edit = CardioEdit(draft = 90, saved = 150)), online = false)

        rule.onNodeWithTag("cardio_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede guardar el cardio").assertExists()
    }

    @Test
    fun aSavedCardioBelow150_showsTheNoteInTheRow() {
        showProposal(proposal(activity = defaultActivity.copy(cardioMinWeek = 90, cardioEdited = true)))

        rule.onNodeWithText("Cardio · 90 min por semana · ritmo suave").assertExists()
        rule.onNodeWithTag("plan_proposal_activity_below_note").assertExists()
    }

    @Test
    fun stepsRow_isNotEditable_andExplainsItsOrigin() {
        showProposal(proposal(activity = defaultActivity.copy(stepsDay = 11_000, stepsBasis = StepsBasis.Average)))

        val row = rule.onNodeWithTag("plan_proposal_activity_steps_row")
        row.assertExists()
        rule.onNodeWithText("Pasos · 11.000 al día").assertExists()
        rule.onNodeWithText("Sale de tu media de los últimos 28 días y no se puede editar.").assertExists()
        row.assertDoesNotHaveClick()
        row.performClick()
        assertEquals(emptyList<PlanProposalEvent>(), proposalEvents)
    }

    @Test
    fun stepsFloorWithoutAWatch_saysSo() {
        showProposal(proposal())

        rule.onNodeWithText("Sin datos suficientes del reloj, partimos de 7.000.").assertExists()
    }

    @Test
    fun aPlanWithoutActivity_showsNoActivityBlockAndNoError() {
        showProposal(proposal(activity = null))

        rule.onNodeWithTag("plan_proposal_activity_section").assertDoesNotExist()
        rule.onNodeWithTag("plan_proposal_accept").assertExists()
    }

    @Test
    fun acceptedPlan_showsBothTargetsInTuPlan() {
        showTrainer(TrainerUiState.Content(plan = accepted(defaultActivity.copy(cardioMinWeek = 120))))

        rule.onNodeWithTag("trainer_plan_activity", useUnmergedTree = true)
            .assertTextEquals("Cardio · 120 min por semana · Pasos · 7.000 al día")
    }

    @Test
    fun acceptedPlan_editFromTuPlan_opensTheSheetAtTheSavedValue() {
        showTrainer(
            TrainerUiState.Content(
                plan = accepted(defaultActivity.copy(cardioMinWeek = 120)),
                cardioEdit = CardioEdit(draft = 120, saved = 120),
            ),
        )

        rule.onNodeWithTag("cardio_sheet_stepper_value").assertTextEquals("120 min")
        rule.onNodeWithTag("cardio_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun tuPlanCardioRow_sendsTheOpenEvent() {
        showTrainer(TrainerUiState.Content(plan = accepted(defaultActivity)))

        rule.onNodeWithTag("trainer_plan_cardio_row").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.OpenCardioEdit), trainerEvents)
    }

    @Test
    fun acceptedPlanWithoutActivity_showsNoLineAndNoError() {
        showTrainer(TrainerUiState.Content(plan = accepted(null)))

        rule.onNodeWithTag("trainer_plan_activity", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("trainer_plan_card").assertExists()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotHaveClick() =
        this.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
}
