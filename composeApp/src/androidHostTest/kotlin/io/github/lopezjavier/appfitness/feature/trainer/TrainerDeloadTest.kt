package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.CoachChangeException
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFailure
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.feature.routines.RecordingMessages
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-100 (UI side): the deload row, "Cancelar descarga" and "Hacer descarga la próxima semana" on the Trainer tab. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class TrainerDeloadTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainerEvent>()
    private val messages = RecordingMessages()
    private val changes = FakeCoachChangesRepository()
    private val weeks = FakeCoachWeeksRepository()

    private val runningBlock = listOf(
        planWeek(week = 1, phase = WeekPhase.Past),
        planWeek(week = 2, phase = WeekPhase.Current),
        planWeek(week = 3, phase = WeekPhase.Upcoming),
    )

    private fun row(kind: CoachChangeKind, status: CoachChangeStatus = CoachChangeStatus.Applied) =
        deloadChange(kind = kind, status = status).toRowUi()

    private fun content(rows: List<CoachChangeRowUi> = emptyList(), weeks: List<PlanWeek> = runningBlock) =
        TrainerUiState.Content(
            plan = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)),
            block = BlockSummary.Saved(coachBlock(saved = true)),
            weeks = weeks,
            coachChanges = rows,
        )

    private fun show(state: TrainerUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme { TrainerWithBoxes(state, flowOf(online), { events += it }) }
        }
        rule.waitForIdle()
        rule.openTrainerBlock()
    }

    private fun model(vararg rows: CoachChange): TrainerViewModel {
        changes.changes = Result.success(rows.toList())
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(id = "plan-1", status = PlanStatus.Accepted))
        val blocks = FakeCoachBlockRepository()
        blocks.current = Result.success(coachBlock(saved = true))
        weeks.weeksResult = Result.success(runningBlock)
        return TrainerViewModel(
            FakeCoachRepository(),
            MutableClock(),
            plans,
            blocks,
            weeks,
            androidx.lifecycle.SavedStateHandle(),
            coachChanges = changes,
        )
    }

    private fun showRoute(model: TrainerViewModel) {
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppMessages provides messages) {
                    TrainerRoutesWithBoxes(model, FakeConnectivityObserver())
                }
            }
        }
        rule.waitForIdle()
        rule.openTrainerBlock()
    }

    @Test
    fun triggeredDeload_showsTheRowWithItsReasonAndCancelAction() {
        show(content(listOf(row(CoachChangeKind.DeloadTriggered))))

        rule.onNodeWithText("Semana de descarga").assertExists()
        rule.onNodeWithText(
            "Semana de descarga: mismos pesos, menos series. Llevas 3 semanas sin mejorar en los básicos.",
        ).assertExists()
        rule.onNodeWithTag("trainer_deload_cancel_deload-1").assertExists()
    }

    @Test
    fun scheduledDeload_readsTheSevenWeeksReason() {
        show(content(listOf(row(CoachChangeKind.DeloadScheduled))))

        rule.onNodeWithText("Llevas 7 semanas entrenando sin descarga.").assertExists()
    }

    @Test
    fun userDeload_readsTheRequestedReason() {
        show(content(listOf(row(CoachChangeKind.DeloadUser))))

        rule.onNodeWithText("Semana de descarga que pediste: mismos pesos, menos series.").assertExists()
    }

    @Test
    fun deloadRow_hasNoAcceptOrDiscardCard() {
        show(content(listOf(row(CoachChangeKind.DeloadTriggered))))

        rule.onNodeWithText("Aceptar").assertDoesNotExist()
        rule.onNodeWithText("Descartar").assertDoesNotExist()
    }

    @Test
    fun cancelTap_sendsTheCancelEvent() {
        show(content(listOf(row(CoachChangeKind.DeloadTriggered))))

        rule.onNodeWithTag("trainer_deload_cancel_deload-1").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.CancelDeload("deload-1")), events)
    }

    @Test
    fun cancelledDeload_readsCanceladaWithoutActionOrWeightLine() {
        show(content(listOf(row(CoachChangeKind.DeloadTriggered, CoachChangeStatus.Undone))))

        rule.onNodeWithText("Cancelada").assertExists()
        rule.onNodeWithTag("trainer_deload_cancel_deload-1").assertDoesNotExist()
    }

    @Test
    fun cancellingThroughTheRoute_cancelsOnTheServer_andTheRowTurnsCancelada() {
        val model = model(deloadChange())
        weeks.onSuccess = { changes.changes = Result.success(listOf(deloadChange(status = CoachChangeStatus.Undone))) }
        showRoute(model)

        rule.onNodeWithTag("trainer_deload_cancel_deload-1").performClick()
        rule.waitForIdle()

        assertEquals(listOf("deload-1"), weeks.cancellations)
        rule.onNodeWithText("Cancelada").assertExists()
        assertEquals(emptyList<String>(), messages.shown)
    }

    @Test
    fun failedCancel_showsAMessage_andKeepsTheRow() {
        weeks.cancelResult = Result.failure(AppException(AppError.Unknown))
        showRoute(model(deloadChange()))

        rule.onNodeWithTag("trainer_deload_cancel_deload-1").performClick()
        rule.waitForIdle()

        assertEquals(listOf("No se pudo cancelar la descarga. Inténtalo de nuevo."), messages.shown)
        rule.onNodeWithTag("trainer_deload_cancel_deload-1").assertExists()
    }

    @Test
    fun offline_disablesCancelAndShowsTheHint() {
        show(content(listOf(row(CoachChangeKind.DeloadTriggered))), online = false)

        rule.onNodeWithTag("trainer_deload_cancel_deload-1").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede cancelar la descarga", substring = true).assertExists()
    }

    @Test
    fun requestAction_isShownUnderTheWeeks_andSendsTheEvent() {
        show(content())

        rule.onNodeWithTag("trainer_deload_request").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.RequestDeload), events)
    }

    @Test
    fun requestAction_isHiddenWhenADeloadIsPlannedInTheBlock() {
        show(content(weeks = runningBlock.map { if (it.week == 3) it.copy(deload = true) else it }))

        rule.onNodeWithTag("trainer_deload_request").assertDoesNotExist()
    }

    @Test
    fun requestingThroughTheRoute_appliesAtOnce_andTheActionDisappears() {
        val model = model()
        weeks.onSuccess = {
            weeks.weeksResult = Result.success(runningBlock.map { if (it.week == 3) it.copy(deload = true) else it })
            changes.changes = Result.success(listOf(deloadChange(kind = CoachChangeKind.DeloadUser, week = 3)))
        }
        showRoute(model)

        rule.onNodeWithTag("trainer_deload_request").performClick()
        rule.waitForIdle()

        assertEquals(1, weeks.requests)
        rule.onNodeWithTag("trainer_week_3_deload").assertExists()
        rule.onNodeWithTag("trainer_deload_request").assertDoesNotExist()
        rule.onNodeWithText("Semana de descarga que pediste: mismos pesos, menos series.").assertExists()
    }

    @Test
    fun requestRefusedBecauseOneExists_showsAMessage() {
        weeks.requestResult = Result.failure(CoachChangeException(CoachChangeFailure.DeloadNotAvailable))
        showRoute(model())

        rule.onNodeWithTag("trainer_deload_request").performClick()
        rule.waitForIdle()

        assertEquals(listOf("Ya hay una descarga en este bloque."), messages.shown)
    }

    @Test
    fun failedRequest_showsAMessage() {
        weeks.requestResult = Result.failure(AppException(AppError.Unknown))
        showRoute(model())

        rule.onNodeWithTag("trainer_deload_request").performClick()
        rule.waitForIdle()

        assertEquals(listOf("No se pudo pedir la descarga. Inténtalo de nuevo."), messages.shown)
    }

    @Test
    fun offline_disablesTheRequestAndShowsTheHint() {
        show(content(), online = false)

        rule.onNodeWithTag("trainer_deload_request").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede pedir la descarga", substring = true).assertExists()
    }

    @Test
    fun recreation_keepsTheDeloadRowAndTheRequestStateOfTheBlock() {
        val model = model(deloadChange())
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme { TrainerRoutesWithBoxes(model, FakeConnectivityObserver()) }
        }
        rule.waitForIdle()
        rule.openTrainerBlock()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_deload_cancel_deload-1").assertExists()
        rule.onNodeWithTag("trainer_deload_request").assertExists()
    }
}
