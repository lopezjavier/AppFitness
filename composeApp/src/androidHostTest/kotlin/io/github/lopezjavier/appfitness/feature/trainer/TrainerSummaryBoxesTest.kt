package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.feature.routines.RecordingMessages
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-120: the Trainer root's summary boxes and the "Tu plan" / "Tu bloque" screens they open, with nothing lost. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class TrainerSummaryBoxesTest {
    @get:Rule
    val rule = createComposeRule()

    private val accepted = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted))
    private val savedBlock = BlockSummary.Saved(coachBlock(saved = true))
    private val names = mapOf("bench" to "Press de banca")

    private fun show(state: TrainerUiState, actions: TrainerActions = TrainerActions(), online: Boolean = true) {
        rule.setContent {
            AppTheme { TrainerWithBoxes(state, flowOf(online), {}, actions = actions, exerciseNames = names) }
        }
        rule.waitForIdle()
    }

    private fun row(id: String, kind: CoachChangeKind, status: CoachChangeStatus) =
        coachChange(id = id, kind = kind, status = status).toRowUi()

    private fun savedState(weeks: List<PlanWeek>, pause: BlockPause? = null) =
        TrainerUiState.Content(plan = accepted, block = savedBlock, weeks = weeks, pause = pause)

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    // The root shows summary boxes

    @Test
    fun theRoot_hasNoDetallesEntry() {
        show(savedState(listOf(planWeek(week = 1))))

        rule.onNodeWithText("Detalles").assertDoesNotExist()
    }

    @Test
    fun withoutAPlan_theRootOffersCrearPlan_andNoBoxes() {
        show(TrainerUiState.Content())

        rule.onNodeWithTag("trainer_create_plan").assertExists()
        rule.onNodeWithTag("trainer_box_plan").assertDoesNotExist()
        rule.onNodeWithTag("trainer_box_block").assertDoesNotExist()
    }

    @Test
    fun withAProposedPlan_theRootKeepsThePendingCard_andCrearPlanNuevo() {
        show(TrainerUiState.Content(plan = PlanSummary.Proposed(coachPlan())))

        rule.onNodeWithTag("trainer_plan_card").assertExists()
        rule.onNodeWithTag("trainer_create_plan_new").assertExists()
        rule.onNodeWithTag("trainer_box_plan").assertDoesNotExist()
    }

    @Test
    fun anAcceptedPlan_theBoxCarriesTheKeyFigures_andHidesThePlanBody() {
        show(TrainerUiState.Content(plan = accepted))

        rule.onNodeWithTag("trainer_box_plan").assertExists()
        rule.onNodeWithTag("trainer_create_block").assertExists()
        rule.onNodeWithTag("trainer_plan_phase").assertDoesNotExist()
        rule.onNodeWithTag("trainer_plan_card").assertDoesNotExist()
    }

    @Test
    fun aSavedBlock_theBoxReadsTheCurrentWeekAndItsAdherence() {
        show(savedState(listOf(planWeek(week = 1, phase = WeekPhase.Past), planWeek(week = 2), planWeek(week = 3))))

        rule.onNodeWithTag("trainer_box_block").assertExists()
        rule.onNodeWithText("Semana 2 de 3").assertExists()
        rule.onNodeWithText("3 de 4 entrenos · 75 %").assertExists()
        rule.onNodeWithTag("trainer_week_1").assertDoesNotExist()
        rule.onNodeWithTag("trainer_remove_block").assertDoesNotExist()
        rule.onNodeWithTag("trainer_create_block").assertDoesNotExist()
    }

    @Test
    fun aPausedBlock_theBoxReadsSemanaEnPausa() {
        val pause = BlockPause(paused = true, pausedWeeks = 1, blockEnd = LocalDate(2026, 11, 9))
        show(savedState(listOf(planWeek(week = 1)), pause))

        rule.onNodeWithText("Semana en pausa").assertExists()
    }

    @Test
    fun aBlockNotStartedYet_theBoxReadsAunSinEmpezar() {
        show(savedState(listOf(planWeek(week = 1, phase = WeekPhase.Upcoming))))

        rule.onNodeWithText("Aún sin empezar").assertExists()
    }

    @Test
    fun aFinishedBlock_theBoxReadsBloqueTerminado_andCrearBloqueIsHidden() {
        show(savedState(listOf(planWeek(week = 1, phase = WeekPhase.Past))).copy(blockReviewDue = true))

        rule.onNodeWithText("Bloque terminado").assertExists()
        rule.onNodeWithTag("trainer_create_block").assertDoesNotExist()
    }

    @Test
    fun theWeekSummaryBox_callsOnOpenWeekSummary() {
        var opened = 0
        show(
            TrainerUiState.Content(plan = accepted, hasWeekSummary = true),
            TrainerActions(onOpenWeekSummary = { opened++ }),
        )

        tap("trainer_box_week_summary")

        assertEquals(1, opened)
    }

    @Test
    fun theRoot_keepsOnlyThePendingFlags_andTheAppliedChangesLiveOnTuBloque() {
        val state = TrainerUiState.Content(
            plan = accepted,
            coachChanges = listOf(
                row("flag", CoachChangeKind.NoRangeFlag, CoachChangeStatus.Pending),
                row("done", CoachChangeKind.LoadRaise, CoachChangeStatus.Applied),
            ),
        )
        show(state)

        rule.onNodeWithTag("trainer_coach_change_row_flag").assertExists()
        rule.onNodeWithTag("trainer_coach_change_row_done").assertDoesNotExist()
        rule.openTrainerBlock()
        rule.onNodeWithTag("trainer_coach_change_row_done").assertExists()
        rule.onNodeWithTag("trainer_coach_change_row_flag").assertDoesNotExist()
    }

    // Each box opens its information

    @Test
    fun tuPlan_opensFromItsBox_andBackReturnsToTheRoot() {
        show(TrainerUiState.Content(plan = accepted))

        rule.openTrainerPlan()
        rule.onNodeWithTag("trainer_plan_screen").assertExists()
        rule.onNodeWithTag("trainer_screen").assertDoesNotExist()
        tap("trainer_plan_back")

        rule.onNodeWithTag("trainer_screen").assertExists()
        rule.onNodeWithTag("trainer_plan_screen").assertDoesNotExist()
    }

    @Test
    fun tuBloque_opensFromItsBox_andBackReturnsToTheRoot() {
        show(savedState(listOf(planWeek(week = 1))))

        rule.openTrainerBlock()
        rule.onNodeWithTag("trainer_block_screen").assertExists()
        tap("trainer_block_back")

        rule.onNodeWithTag("trainer_screen").assertExists()
        rule.onNodeWithTag("trainer_block_screen").assertDoesNotExist()
    }

    @Test
    fun tuPlan_holdsThePlanFiguresPhaseAndCardio() {
        show(TrainerUiState.Content(plan = accepted))
        rule.openTrainerPlan()

        rule.onNodeWithTag("trainer_plan_card").assertExists()
        rule.onNodeWithTag("trainer_plan_phase").assertExists()
        rule.onNodeWithTag("trainer_create_plan_new").assertExists()
    }

    @Test
    fun tuPlan_withASavedBlock_offersBloqueNuevo() {
        show(savedState(listOf(planWeek(week = 1))))
        rule.openTrainerPlan()

        rule.onNodeWithTag("trainer_new_block").assertExists()
    }

    @Test
    fun tuBloque_holdsTheSavedBlockWithItsWeeksAndRemoveAction() {
        show(savedState(listOf(planWeek(week = 1), planWeek(week = 2, phase = WeekPhase.Upcoming))))
        rule.openTrainerBlock()

        rule.onNodeWithTag("trainer_block_card").assertExists()
        rule.onNodeWithTag("trainer_remove_block").assertExists()
        rule.onNodeWithTag("trainer_week_1").assertExists()
    }

    @Test
    fun aSavedBlockWithoutAPlan_stillReachesEliminarBloqueThroughItsBox() {
        show(TrainerUiState.Content(block = savedBlock))

        rule.openTrainerBlock()

        rule.onNodeWithTag("trainer_remove_block").assertExists()
    }

    @Test
    fun appliedChangesWithoutABlock_stillReachTheirListThroughTuBloque() {
        show(
            TrainerUiState.Content(
                coachChanges = listOf(row("done", CoachChangeKind.LoadRaise, CoachChangeStatus.Applied)),
            ),
        )

        rule.openTrainerBlock()

        rule.onNodeWithTag("trainer_coach_change_row_done").assertExists()
    }

    @Test
    fun theBoxes_callTheirActions() {
        var plan = 0
        var block = 0
        rule.setContent {
            AppTheme {
                TrainerScreen(
                    savedState(listOf(planWeek(week = 1))),
                    flowOf(true),
                    {},
                    actions = TrainerActions(onOpenPlan = { plan++ }, onOpenBlockScreen = { block++ }),
                )
            }
        }

        tap("trainer_box_plan")
        tap("trainer_box_block")

        assertEquals(listOf(1, 1), listOf(plan, block))
    }

    // States of the pushed screens

    @Test
    fun whileLoading_eachScreenShowsItsSkeleton() {
        rule.setContent {
            AppTheme { TrainerBoxScreen(TrainerBox.Plan, TrainerUiState.Loading, flowOf(true), {}, {}) }
        }

        rule.onNodeWithTag("trainer_plan_loading").assertExists()
    }

    @Test
    fun whenNothingIsLeft_theScreenShowsTheEmptyState() {
        rule.setContent {
            AppTheme { TrainerBoxScreen(TrainerBox.Block, TrainerUiState.Content(), flowOf(true), {}, {}) }
        }

        rule.onNodeWithText("Todavía no hay nada que mostrar.").assertExists()
        rule.onNodeWithTag("trainer_block_empty").assertExists()
    }

    @Test
    fun theScreenTitles_equalTheBoxTitles() {
        show(savedState(listOf(planWeek(week = 1))))

        rule.openTrainerPlan()
        rule.onNodeWithTag("trainer_plan_title").assertTextContains("Tu plan")
        tap("trainer_plan_back")
        rule.openTrainerBlock()
        rule.onNodeWithTag("trainer_block_title").assertTextContains("Tu bloque")
    }

    // Effects and refresh across the two screens

    @Test
    fun aFailedRemoval_showsItsMessageFromTuBloque() {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(id = "plan-7", status = PlanStatus.Accepted))
        val blocks = FakeCoachBlockRepository()
        blocks.current = Result.success(coachBlock(saved = true))
        blocks.removeResult = Result.failure(IllegalStateException("boom"))
        val model = TrainerViewModel(FakeCoachRepository(), MutableClock(), plans, blocks, FakeCoachWeeksRepository())
        val messages = RecordingMessages()
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides messages) {
                    TrainerRoutesWithBoxes(model, FakeConnectivityObserver())
                }
            }
        }
        rule.waitForIdle()
        rule.openTrainerBlock()

        tap("trainer_remove_block")
        tap("trainer_remove_block_dialog_confirm")

        assertEquals(listOf("No se pudo eliminar el bloque. Inténtalo de nuevo."), messages.shown)
    }

    @Test
    fun removingTheBlockOnTuBloque_andGoingBack_showsNoBlockBoxOnTheRoot() {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(id = "plan-7", status = PlanStatus.Accepted))
        val blocks = FakeCoachBlockRepository()
        blocks.current = Result.success(coachBlock(saved = true))
        val model = TrainerViewModel(FakeCoachRepository(), MutableClock(), plans, blocks, FakeCoachWeeksRepository())
        rule.setContent { AppTheme { TrainerRoutesWithBoxes(model, FakeConnectivityObserver()) } }
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_box_block").assertExists()
        rule.openTrainerBlock()

        tap("trainer_remove_block")
        tap("trainer_remove_block_dialog_confirm")
        tap("trainer_block_back")

        rule.onNodeWithTag("trainer_box_block").assertDoesNotExist()
        rule.onNodeWithTag("trainer_create_block").assertExists()
    }
}
