package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
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

    private val openedBlocks = mutableListOf<String>()
    private val starts = mutableListOf<Triple<BeforeStartMode, String?, String?>>()

    private fun content(plan: PlanSummary, change: CalorieProposal? = null, block: BlockSummary = BlockSummary.None) =
        TrainerUiState.Content(plan = plan, calorieProposal = change, block = block)

    private fun show(state: TrainerUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme {
                TrainerScreen(
                    state,
                    flowOf(online),
                    { events += it },
                    actions = TrainerActions(
                        onOpenProposal = { opened++ },
                        onOpenBlock = { openedBlocks += it },
                        onOpenBeforeStart = { mode, planId, replacing -> starts += Triple(mode, planId, replacing) },
                    ),
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun withoutAPlan_crearPlanOpensAntesDeEmpezarInPlanMode() {
        show(content(PlanSummary.None))

        rule.onNodeWithText("Tu plan").assertExists()
        tap("trainer_create_plan")

        assertEquals(listOf(Triple(BeforeStartMode.Plan, null, null)), starts)
        assertEquals(0, opened)
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
    fun calorieProposalCard_showsTheChangeItsReasonAndTheDataUsed() {
        show(content(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)), calorieProposal()))

        rule.onNodeWithTag("trainer_calorie_card").assertExists()
        rule.onNodeWithText("Ajuste de calorías").assertExists()
        rule.onNodeWithText("De 2.000 a 2.100 kcal al día").assertExists()
        rule.onNodeWithText(
            "Te proponemos subir 100 kcal: en las últimas 2 semanas tu peso ha bajado más de lo previsto.",
        ).assertExists()
        rule.onNodeWithText("Según tu tendencia: −0,1 kg/sem (margen ±0,1)").assertExists()
    }

    @Test
    fun calorieProposalCard_isHiddenWithoutAPendingChange() {
        show(content(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted))))

        rule.onNodeWithTag("trainer_calorie_card").assertDoesNotExist()
    }

    @Test
    fun aplicar_onTheCalorieCard_sendsTheDecision() {
        show(content(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)), calorieProposal()))

        rule.onNodeWithTag("trainer_calorie_card_accept").assertIsEnabled()
        tap("trainer_calorie_card_accept")

        assertEquals(listOf<TrainerEvent>(TrainerEvent.ApplyCalorieProposal), events)
    }

    @Test
    fun ahoraNo_onTheCalorieCard_sendsTheDiscard() {
        show(content(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)), calorieProposal()))

        tap("trainer_calorie_card_discard")

        assertEquals(listOf<TrainerEvent>(TrainerEvent.DeclineCalorieProposal), events)
    }

    @Test
    fun aplicar_offline_isDisabledWithTheHint() {
        show(content(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)), calorieProposal()), online = false)

        rule.onNodeWithTag("trainer_calorie_card_accept").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede aplicar el cambio o guardar tu decisión").assertExists()
    }

    @Test
    fun acceptingTheCalorieChange_throughTheRoute_sendsItAndRemovesTheCard() {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        plans.pending = Result.success(calorieProposal())
        val model = TrainerViewModel(
            FakeCoachRepository(),
            MutableClock(),
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

    @Test
    fun withoutAPlan_thereIsNoBlockEntry() {
        show(content(PlanSummary.None))

        rule.onNodeWithTag("trainer_create_block").assertDoesNotExist()
        rule.onNodeWithTag("trainer_block_card").assertDoesNotExist()
    }

    @Test
    fun anOpenPlanWithoutABlock_crearBloqueOpensAntesDeEmpezarInBlockModeForThatPlan() {
        show(content(PlanSummary.Accepted(coachPlan(id = "plan-9", status = PlanStatus.Accepted))))

        tap("trainer_create_block")

        assertEquals(listOf(Triple(BeforeStartMode.Block, "plan-9", null)), starts)
        assertTrue(openedBlocks.isEmpty())
    }

    @Test
    fun offline_crearBloqueIsDisabledWithTheHint() {
        show(content(PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted))), online = false)

        rule.onNodeWithTag("trainer_create_block").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede crear el bloque").assertExists()
    }

    @Test
    fun aProposedBlock_showsTheCardThatReopensThePreview() {
        val plan = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted))
        show(content(plan, block = BlockSummary.Proposed(coachBlock())))

        rule.onNodeWithText("Bloque propuesto").assertExists()
        rule.onNodeWithTag("trainer_create_block").assertDoesNotExist()
        tap("trainer_block_card")

        assertEquals(listOf("plan-1"), openedBlocks)
    }

    @Test
    fun aSavedBlock_readsBloqueGuardado_withoutOpeningAnything() {
        val plan = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted))
        show(content(plan, block = BlockSummary.Saved(coachBlock(saved = true))))

        rule.onNodeWithText("Bloque guardado").assertExists()
        tap("trainer_block_card")

        assertTrue(openedBlocks.isEmpty())
    }
}
