package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-73: the week list on the Trainer tab (T-230 UI adds the deload request action tests). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class PlanWeeksTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainerEvent>()

    private fun content(weeks: List<PlanWeek> = emptyList()) = TrainerUiState.Content(
        plan = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)),
        block = BlockSummary.Saved(coachBlock(saved = true)),
        weeks = weeks,
    )

    private fun show(state: TrainerUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme { TrainerWithDetails(state, flowOf(online), { events += it }) }
        }
        rule.waitForIdle()
        rule.openTrainerDetails()
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
}
