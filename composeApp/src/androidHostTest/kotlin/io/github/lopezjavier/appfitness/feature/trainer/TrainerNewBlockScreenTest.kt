package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T-240 (US-110, US-111): "Bloque nuevo", "Mi entrenamiento" and "Llevas n semanas en esta fase" on the Trainer. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TrainerNewBlockScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainerEvent>()

    private val accepted get() = PlanSummary.Accepted(coachPlan(id = "plan-7", status = PlanStatus.Accepted))

    private fun savedBlock(weeks: PhaseWeeks? = null) = TrainerUiState.Content(
        plan = accepted,
        block = BlockSummary.Saved(coachBlock(saved = true)),
        weeksInPhase = weeks,
    )

    private fun show(state: TrainerUiState, online: Boolean = true) {
        rule.setContent {
            AppTheme { TrainerScreen(state, flowOf(online), { events += it }, actions = TrainerActions()) }
        }
        rule.waitForIdle()
    }

    @Test
    fun duringASavedBlock_bloqueNuevoAndMiEntrenamientoReplaceCrearPlanNuevo() {
        show(savedBlock())

        rule.onNodeWithTag("trainer_new_block").assertExists()
        rule.onNodeWithText("Bloque nuevo").assertExists()
        rule.onNodeWithTag("trainer_my_training").assertExists()
        rule.onNodeWithText("Mi entrenamiento").assertExists()
        rule.onNodeWithTag("trainer_create_plan_new").assertDoesNotExist()
    }

    @Test
    fun withoutASavedBlock_thereIsNoBloqueNuevo() {
        show(TrainerUiState.Content(plan = accepted))

        rule.onNodeWithTag("trainer_new_block").assertDoesNotExist()
        rule.onNodeWithTag("trainer_my_training").assertDoesNotExist()
    }

    @Test
    fun tappingTheEntries_sendsTheirEvents() {
        show(savedBlock())

        rule.onNodeWithTag("trainer_new_block").performClick()
        rule.onNodeWithTag("trainer_my_training").performClick()

        assertEquals(listOf(TrainerEvent.OpenNewBlock, TrainerEvent.OpenMyTraining), events)
    }

    @Test
    fun offline_bothEntriesAreDisabled_withTheHint() {
        show(savedBlock(), online = false)

        rule.onNodeWithTag("trainer_new_block").assertIsNotEnabled()
        rule.onNodeWithTag("trainer_my_training").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede crear un bloque nuevo").assertExists()
    }

    @Test
    fun weeksInPhase_plural() {
        show(savedBlock(PhaseWeeks(Phase.Cut, 5)))

        rule.onNodeWithTag("trainer_plan_weeks_in_phase").assertTextEquals("Llevas 5 semanas en esta fase")
    }

    @Test
    fun weeksInPhase_singular() {
        show(savedBlock(PhaseWeeks(Phase.Cut, 1)))

        rule.onNodeWithTag("trainer_plan_weeks_in_phase").assertTextEquals("Llevas 1 semana en esta fase")
    }

    @Test
    fun weeksInPhase_hiddenWhenUnknown() {
        show(savedBlock(null))

        rule.onNodeWithTag("trainer_plan_weeks_in_phase").assertDoesNotExist()
    }
}
