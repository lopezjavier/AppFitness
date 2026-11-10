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

/** US-120: the compact Trainer root and "Detalles", which keeps the plan and the block with nothing lost. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class TrainerDetailsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val accepted = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted))
    private val savedBlock = BlockSummary.Saved(coachBlock(saved = true))
    private val names = mapOf("bench" to "Press de banca")

    private fun show(state: TrainerUiState, actions: TrainerActions = TrainerActions(), online: Boolean = true) {
        rule.setContent {
            AppTheme { TrainerWithDetails(state, flowOf(online), {}, actions = actions, exerciseNames = names) }
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

    // The root shows the essentials

    @Test
    fun withoutAPlan_theRootOffersCrearPlan_andNoDetallesRow() {
        show(TrainerUiState.Content())

        rule.onNodeWithTag("trainer_create_plan").assertExists()
        rule.onNodeWithTag("trainer_details_row").assertDoesNotExist()
        rule.onNodeWithTag("trainer_block_status").assertDoesNotExist()
    }

    @Test
    fun withAProposedPlan_theRootKeepsThePendingCard_andOffersDetalles() {
        show(TrainerUiState.Content(plan = PlanSummary.Proposed(coachPlan())))

        rule.onNodeWithTag("trainer_plan_card").assertExists()
        rule.onNodeWithTag("trainer_create_plan_new").assertDoesNotExist()
        rule.onNodeWithText("Detalles").assertExists()
    }

    @Test
    fun anAcceptedPlanWithoutABlock_theRootOffersCrearBloque_andHidesThePlanBody() {
        show(TrainerUiState.Content(plan = accepted))

        rule.onNodeWithTag("trainer_create_block").assertExists()
        rule.onNodeWithTag("trainer_plan_phase").assertDoesNotExist()
        rule.onNodeWithTag("trainer_plan_card").assertDoesNotExist()
        rule.onNodeWithTag("trainer_details_row").assertExists()
    }

    @Test
    fun aSavedBlock_theRootShowsOnlyTheStatus_andNotTheWeeksNorTheRemoveAction() {
        show(savedState(listOf(planWeek(week = 1), planWeek(week = 2, phase = WeekPhase.Upcoming))))

        rule.onNodeWithTag("trainer_block_status").assertExists()
        rule.onNodeWithTag("trainer_week_1").assertDoesNotExist()
        rule.onNodeWithTag("trainer_plan_phase").assertDoesNotExist()
        rule.onNodeWithTag("trainer_remove_block").assertDoesNotExist()
        rule.onNodeWithTag("trainer_create_block").assertDoesNotExist()
    }

    @Test
    fun theStatusCard_readsTheCurrentWeekAndItsAdherence() {
        show(savedState(listOf(planWeek(week = 1, phase = WeekPhase.Past), planWeek(week = 2), planWeek(week = 3))))

        rule.onNodeWithText("Semana 2 de 3").assertExists()
        rule.onNodeWithText("3 de 4 entrenos · 75 %").assertExists()
    }

    @Test
    fun aPausedBlock_theStatusReadsSemanaEnPausa() {
        val pause = BlockPause(paused = true, pausedWeeks = 1, blockEnd = LocalDate(2026, 11, 9))
        show(savedState(listOf(planWeek(week = 1)), pause))

        rule.onNodeWithText("Semana en pausa").assertExists()
    }

    @Test
    fun aBlockNotStartedYet_theStatusReadsAunSinEmpezar() {
        show(savedState(listOf(planWeek(week = 1, phase = WeekPhase.Upcoming))))

        rule.onNodeWithText("Aún sin empezar").assertExists()
    }

    @Test
    fun aFinishedBlock_theStatusReadsBloqueTerminado_andCrearBloqueIsHidden() {
        show(savedState(listOf(planWeek(week = 1, phase = WeekPhase.Past))).copy(blockReviewDue = true))

        rule.onNodeWithText("Bloque terminado").assertExists()
        rule.onNodeWithTag("trainer_create_block").assertDoesNotExist()
    }

    @Test
    fun theRoot_keepsOnlyThePendingFlags_andTheAppliedChangesLiveInDetalles() {
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
        rule.openTrainerDetails()
        rule.onNodeWithTag("trainer_coach_change_row_done").assertExists()
        rule.onNodeWithTag("trainer_coach_change_row_flag").assertDoesNotExist()
    }

    // Detalles holds everything else

    @Test
    fun detalles_opensFromTheRow_andBackReturnsToTheRoot() {
        show(TrainerUiState.Content(plan = accepted))

        rule.openTrainerDetails()
        rule.onNodeWithTag("trainer_details_screen").assertExists()
        rule.onNodeWithTag("trainer_screen").assertDoesNotExist()
        tap("trainer_details_back")

        rule.onNodeWithTag("trainer_screen").assertExists()
        rule.onNodeWithTag("trainer_details_screen").assertDoesNotExist()
    }

    @Test
    fun detalles_holdsThePlanFiguresPhaseAndCardio() {
        show(TrainerUiState.Content(plan = accepted))
        rule.openTrainerDetails()

        rule.onNodeWithTag("trainer_plan_card").assertExists()
        rule.onNodeWithTag("trainer_plan_phase").assertExists()
        rule.onNodeWithTag("trainer_create_plan_new").assertExists()
    }

    @Test
    fun detalles_holdsTheSavedBlockWithItsWeeksAndRemoveAction() {
        show(savedState(listOf(planWeek(week = 1), planWeek(week = 2, phase = WeekPhase.Upcoming))))
        rule.openTrainerDetails()

        rule.onNodeWithTag("trainer_block_card").assertExists()
        rule.onNodeWithTag("trainer_remove_block").assertExists()
        rule.onNodeWithTag("trainer_week_1").assertExists()
        rule.onNodeWithTag("trainer_new_block").assertExists()
    }

    @Test
    fun aSavedBlockWithoutAPlan_stillReachesEliminarBloqueThroughDetalles() {
        show(TrainerUiState.Content(block = savedBlock))

        rule.onNodeWithTag("trainer_details_row").assertExists()
        rule.openTrainerDetails()

        rule.onNodeWithTag("trainer_remove_block").assertExists()
    }

    @Test
    fun appliedChangesWithoutAPlan_stillReachTheirListThroughDetalles() {
        show(
            TrainerUiState.Content(
                coachChanges = listOf(row("done", CoachChangeKind.LoadRaise, CoachChangeStatus.Applied)),
            ),
        )

        rule.onNodeWithTag("trainer_details_row").assertExists()
        rule.openTrainerDetails()

        rule.onNodeWithTag("trainer_coach_change_row_done").assertExists()
    }

    @Test
    fun theDetallesRow_callsOnOpenDetails() {
        var opened = 0
        rule.setContent {
            AppTheme {
                TrainerScreen(
                    TrainerUiState.Content(plan = accepted),
                    flowOf(true),
                    {},
                    actions = TrainerActions(onOpenDetails = { opened++ }),
                )
            }
        }

        tap("trainer_details_row")

        assertEquals(1, opened)
    }

    // States of Detalles

    @Test
    fun whileLoading_detallesShowsItsSkeleton() {
        rule.setContent { AppTheme { TrainerDetailsScreen(TrainerUiState.Loading, flowOf(true), {}, {}) } }

        rule.onNodeWithTag("trainer_details_loading").assertExists()
    }

    @Test
    fun whenNothingIsLeft_detallesShowsTheEmptyState() {
        rule.setContent { AppTheme { TrainerDetailsScreen(TrainerUiState.Content(), flowOf(true), {}, {}) } }

        rule.onNodeWithText("Todavía no hay nada que mostrar.").assertExists()
        rule.onNodeWithTag("trainer_details_empty").assertExists()
    }

    @Test
    fun detallesTitle_equalsTheRowLabel() {
        show(TrainerUiState.Content(plan = accepted))
        rule.openTrainerDetails()

        rule.onNodeWithTag("trainer_details_title").assertTextContains("Detalles")
    }

    // Effects and refresh across the two screens

    @Test
    fun aFailedRemoval_showsItsMessageFromDetalles() {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(id = "plan-7", status = PlanStatus.Accepted))
        val blocks = FakeCoachBlockRepository()
        blocks.current = Result.success(coachBlock(saved = true))
        blocks.removeResult = Result.failure(IllegalStateException("boom"))
        val model = TrainerViewModel(
            FakeCoachRepository(),
            MutableClock(),
            plans,
            blocks,
            FakeCoachWeeksRepository(),
        )
        val messages = RecordingMessages()
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides messages) {
                    TrainerRoutesWithDetails(model, FakeConnectivityObserver())
                }
            }
        }
        rule.waitForIdle()
        rule.openTrainerDetails()

        tap("trainer_remove_block")
        tap("trainer_remove_block_dialog_confirm")

        assertEquals(listOf("No se pudo eliminar el bloque. Inténtalo de nuevo."), messages.shown)
    }

    @Test
    fun removingTheBlockOnDetalles_andGoingBack_showsNoBlockOnTheRoot() {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(id = "plan-7", status = PlanStatus.Accepted))
        val blocks = FakeCoachBlockRepository()
        blocks.current = Result.success(coachBlock(saved = true))
        val model = TrainerViewModel(FakeCoachRepository(), MutableClock(), plans, blocks, FakeCoachWeeksRepository())
        rule.setContent { AppTheme { TrainerRoutesWithDetails(model, FakeConnectivityObserver()) } }
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_block_status").assertExists()
        rule.openTrainerDetails()

        tap("trainer_remove_block")
        tap("trainer_remove_block_dialog_confirm")
        tap("trainer_details_back")

        rule.onNodeWithTag("trainer_block_status").assertDoesNotExist()
        rule.onNodeWithTag("trainer_create_block").assertExists()
    }
}
