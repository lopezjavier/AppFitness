package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseReason
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-82: the accepted plan's phase on the "Tu plan" card. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TrainerPlanPhaseTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(phase: Phase, reason: PhaseReason = PhaseReason.Intention) {
        val plan = coachPlan(status = PlanStatus.Accepted, phase = phase, phaseReason = reason)
        rule.setContent {
            AppTheme {
                TrainerScreen(
                    TrainerUiState.Content(plan = PlanSummary.Accepted(plan)),
                    flowOf(true),
                    {},
                    actions = TrainerActions(),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun acceptedPlan_showsOnePhaseLineWithLabelAndShortReason() {
        show(Phase.Cut)

        rule.onNodeWithTag("trainer_plan_phase").assertExists()
        rule.onNodeWithText("Definición · Tu objetivo es perder grasa.").assertExists()
    }

    @Test
    fun returningPlan_showsRecuperar() {
        show(Phase.Regain, PhaseReason.ReturningBreak)

        rule.onNodeWithText("Recuperar", substring = true).assertExists()
    }

    @Test
    fun phaseHelp_opensTheExplanation() {
        show(Phase.Maintenance)

        rule.onNodeWithTag("trainer_plan_phase_help").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Comes lo que gastas para mantener tu peso y seguir entrenando.").assertExists()
    }
}
