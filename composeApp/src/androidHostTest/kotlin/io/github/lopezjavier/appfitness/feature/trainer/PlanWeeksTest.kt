package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DeloadProposal
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-73 and US-75: the week list and the deload card on the Trainer tab. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class PlanWeeksTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainerEvent>()

    private fun content(weeks: List<PlanWeek> = emptyList(), deload: DeloadProposal? = null) = TrainerUiState.Content(
        coachValues(provisional = false),
        plan = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)),
        block = BlockSummary.Saved(coachBlock(saved = true)),
        weeks = weeks,
        deload = deload,
    )

    private fun show(state: TrainerUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme { TrainerScreen(state, flowOf(online), { events += it }) }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun weekOne_listsItsWorkoutAndShowsItDoneAfterCompletion() {
        show(content(listOf(planWeek(planned = 1, completed = 1, adherencePct = 100))))

        rule.onNodeWithTag("trainer_weeks_section").assertExists()
        rule.onNodeWithText("Semana 1").assertExists()
        rule.onNodeWithText("Push").assertExists()
        rule.onNodeWithTag("trainer_week_1_session_0_done").assertExists()
        rule.onNodeWithTag("trainer_week_1_session_0_pending").assertDoesNotExist()
    }

    @Test
    fun threeOfFour_rendersTheCountAnd75Percent() {
        show(content(listOf(planWeek(planned = 4, completed = 3, adherencePct = 75))))

        rule.onNodeWithText("3 de 4 entrenos · 75 %").assertExists()
        rule.onNodeWithTag("trainer_week_1_current").assertExists()
    }

    @Test
    fun aMissedWorkout_readsNoHecho_andTheNextWeekStaysPlanned() {
        val missed = planWeek(week = 1, phase = WeekPhase.Past, planned = 1, completed = 0, adherencePct = 0)
        val next = planWeek(week = 2, phase = WeekPhase.Upcoming, planned = 4, completed = 0, adherencePct = 0)
            .copy(sessions = listOf(PlanWeekSession(0, "Push", "routine-0", done = false)))
        show(content(listOf(missed, next)))

        rule.onNodeWithTag("trainer_week_1_session_0_pending").assertExists()
        rule.onNodeWithText("No hecho").assertExists()
        rule.onNodeWithText("4 entrenos planificados").assertExists()
        rule.onNodeWithText("Planificado").assertExists()
        rule.onNodeWithTag("trainer_week_2_current").assertDoesNotExist()
    }

    @Test
    fun aDeloadWeek_showsTheDescargaChip() {
        show(content(listOf(planWeek(week = 4, phase = WeekPhase.Upcoming, deload = true))))

        rule.onNodeWithTag("trainer_week_4_deload").assertExists()
        rule.onNodeWithText("Descarga").assertExists()
    }

    @Test
    fun withoutWeeks_theSectionIsAbsent() {
        show(content())

        rule.onNodeWithTag("trainer_weeks_section").assertDoesNotExist()
    }

    @Test
    fun deloadCard_showsTheSuggestionItsReasonAndTheData() {
        show(content(deload = deloadProposal(week = 4)))

        rule.onNodeWithTag("trainer_deload_card").assertExists()
        rule.onNodeWithText("Semana de descarga").assertExists()
        rule.onNodeWithText("Semana 4: mismo peso, 40 % menos de series").assertExists()
        rule.onNodeWithText(
            "Ninguno de tus ejercicios principales ha mejorado su 1RM estimado en tres semanas.",
        ).assertExists()
    }

    @Test
    fun deloadCard_isAbsentWithoutASuggestion() {
        show(content())

        rule.onNodeWithTag("trainer_deload_card").assertDoesNotExist()
    }

    @Test
    fun aceptarAndDescartar_sendTheirDecision() {
        show(content(deload = deloadProposal()))

        rule.onNodeWithTag("trainer_deload_card_accept").assertIsEnabled()
        tap("trainer_deload_card_accept")
        tap("trainer_deload_card_discard")

        assertEquals(
            listOf<TrainerEvent>(TrainerEvent.DecideDeload(true), TrainerEvent.DecideDeload(false)),
            events,
        )
    }

    @Test
    fun offline_aceptarIsDisabledWithTheHint() {
        show(content(deload = deloadProposal()), online = false)

        rule.onNodeWithTag("trainer_deload_card_accept").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede aceptar la descarga").assertExists()
    }

    private fun route(weeks: FakeCoachWeeksRepository): TrainerViewModel {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        val blocks = FakeCoachBlockRepository()
        blocks.current = Result.success(coachBlock(saved = true))
        return TrainerViewModel(
            FakeCoachRepository(),
            MutableClock(),
            SavedStateHandle(),
            plans = plans,
            blocks = blocks,
            coachWeeks = weeks,
        )
    }

    @Test
    fun accepting_removesTheCard_andMarksTheNextWeek() {
        val weeks = FakeCoachWeeksRepository()
        weeks.weeksResult = Result.success(listOf(planWeek(week = 3), planWeek(week = 4, phase = WeekPhase.Upcoming)))
        weeks.pending = Result.success(deloadProposal(week = 4))
        val model = route(weeks)
        rule.setContent {
            AppTheme { TrainerRoute(viewModel = model, connectivity = FakeConnectivityObserver()) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_deload_card").assertExists()
        rule.onNodeWithTag("trainer_week_4_deload").assertDoesNotExist()

        weeks.weeksResult = Result.success(
            listOf(planWeek(week = 3), planWeek(week = 4, phase = WeekPhase.Upcoming, deload = true)),
        )
        tap("trainer_deload_card_accept")

        assertEquals(listOf("deload-1" to true), weeks.decisions)
        rule.onNodeWithTag("trainer_deload_card").assertDoesNotExist()
        rule.onNodeWithTag("trainer_week_4_deload").assertExists()
    }

    @Test
    fun discarding_removesTheCard_andLeavesTheWeeksUnchanged() {
        val weeks = FakeCoachWeeksRepository()
        weeks.weeksResult = Result.success(listOf(planWeek(week = 3), planWeek(week = 4, phase = WeekPhase.Upcoming)))
        weeks.pending = Result.success(deloadProposal(week = 4))
        val model = route(weeks)
        rule.setContent {
            AppTheme { TrainerRoute(viewModel = model, connectivity = FakeConnectivityObserver()) }
        }
        rule.waitForIdle()

        tap("trainer_deload_card_discard")

        assertEquals(listOf("deload-1" to false), weeks.decisions)
        rule.onNodeWithTag("trainer_deload_card").assertDoesNotExist()
        rule.onNodeWithTag("trainer_week_4_deload").assertDoesNotExist()
    }
}
