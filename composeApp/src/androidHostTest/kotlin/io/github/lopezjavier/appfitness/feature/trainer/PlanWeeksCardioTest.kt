package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-73 "Cardio minutes of the week" and "Steps of the week" in the Trainer week cards. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class PlanWeeksCardioTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(vararg weeks: PlanWeek) {
        val state = TrainerUiState.Content(
            plan = PlanSummary.Accepted(
                coachPlan(status = io.github.lopezjavier.appfitness.domain.model.PlanStatus.Accepted),
            ),
            block = BlockSummary.Saved(coachBlock(saved = true)),
            weeks = weeks.toList(),
        )
        rule.setContent { AppTheme { TrainerWithBoxes(state, flowOf(true), {}) } }
        rule.waitForIdle()
        rule.openTrainerBlock()
    }

    @Test
    fun cardioOfTheWeek_readsDoneOfTarget_andTheBarSpeaksTheSameText() {
        show(planWeek(cardioMinDone = 100, cardioMinTarget = 150))

        rule.onNodeWithTag("plan_week_cardio_value_1").assertTextEquals("Cardio de la semana: 100 de 150 min")
        val description = rule.onNodeWithTag("plan_week_cardio_bar_1").fetchSemanticsNode()
            .config[SemanticsProperties.StateDescription]
        assertEquals("Cardio de la semana: 100 de 150 min", description)
    }

    @Test
    fun stepsOfTheWeek_useTheThousandsSeparator() {
        show(planWeek(stepsAvg = 7800, stepsTarget = 7000))

        rule.onNodeWithTag("plan_week_steps_value_1").assertTextEquals("Pasos: media 7.800 de 7.000")
    }

    @Test
    fun noStepsData_readsSinDatosDePasos() {
        show(planWeek(stepsAvg = null, stepsTarget = 7000))

        rule.onNodeWithTag("plan_week_steps_value_1").assertTextEquals("Sin datos de pasos")
    }

    @Test
    fun aPlanWithoutTargets_showsNeitherLine() {
        show(planWeek())

        rule.onNodeWithTag("plan_week_cardio_value_1").assertDoesNotExist()
        rule.onNodeWithTag("plan_week_steps_value_1").assertDoesNotExist()
    }

    @Test
    fun theNextWeek_startsAtZero_andNothingCarriesOver() {
        show(
            planWeek(week = 1, phase = WeekPhase.Past, cardioMinDone = 100, cardioMinTarget = 150),
            planWeek(
                week = 2,
                phase = WeekPhase.Upcoming,
                cardioMinDone = 0,
                cardioMinTarget = 150,
                stepsTarget = 7000,
            ),
        )

        rule.onNodeWithTag("plan_week_cardio_value_2").assertTextEquals("Cardio de la semana: 0 de 150 min")
        rule.onNodeWithTag("plan_week_steps_value_2").assertDoesNotExist()
    }
}
