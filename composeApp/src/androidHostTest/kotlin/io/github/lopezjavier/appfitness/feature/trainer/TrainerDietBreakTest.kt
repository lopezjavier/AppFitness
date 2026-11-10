package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DietBreakWeek
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-115: "Descanso de dieta" in "Tu plan": the week-4 line and chip, and the live switch of an accepted cut. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TrainerDietBreakTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainerEvent>()
    private val week4 = DietBreakWeek(targetKcal = 2400, proteinG = 150, carbsG = 300, fatG = 70)

    private fun show(
        phase: Phase = Phase.Cut,
        dietBreak: DietBreakWeek? = null,
        edited: Boolean = false,
        saving: Boolean = false,
        online: Boolean = true,
    ) {
        val plan = coachPlan(
            status = PlanStatus.Accepted,
            phase = phase,
            editedByUser = edited,
            dietBreakWeek = dietBreak,
        )
        val state = TrainerUiState.Content(
            plan = PlanSummary.Accepted(plan),
            weeks = listOf(1, 2, 3, 4).map { planWeek(week = it) },
            dietBreakSaving = saving,
        )
        rule.setContent { AppTheme { TrainerWithDetails(state, flowOf(online), { events += it }) } }
        rule.waitForIdle()
        rule.openTrainerDetails()
    }

    @Test
    fun withTheBreakOn_planShowsTheWeek4LineAndOnlyWeek4ShowsTheChip() {
        show(dietBreak = week4)

        rule.onNodeWithText("Semana 4: descanso de dieta, 2.400 kcal al día").assertExists()
        rule.onNodeWithTag("trainer_plan_diet_break_switch").assertIsOn()
        rule.onNodeWithTag("trainer_block_week_diet_break").assertExists()
    }

    @Test
    fun withTheBreakOff_thereIsNoLineAndNoChip() {
        show()

        rule.onNodeWithTag("trainer_plan_diet_break").assertDoesNotExist()
        rule.onNodeWithTag("trainer_block_week_diet_break").assertDoesNotExist()
        rule.onNodeWithTag("trainer_plan_diet_break_switch").assertIsOff()
    }

    @Test
    fun tappingTheSwitch_sendsTheToggleAndTheHelpExplainsIt() {
        show()

        rule.onNodeWithTag("trainer_plan_diet_break_switch").performClick()
        rule.onNodeWithTag("trainer_plan_diet_break_help").performClick()
        rule.waitForIdle()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.ToggleDietBreak(true)), events)
        rule.onNodeWithText(
            "Opcional: la semana 4 del bloque comes lo que gastas; en los estudios se pasa menos hambre, pero no " +
                "se pierde grasa más rápido.",
        ).assertExists()
    }

    @Test
    fun whileSaving_theSwitchIsInert() {
        show(saving = true)

        rule.onNodeWithTag("trainer_plan_diet_break_switch").assertIsNotEnabled()
    }

    @Test
    fun aPlanEditedByTheUser_hasNoSwitch() {
        show(edited = true)

        rule.onNodeWithTag("trainer_plan_diet_break_switch").assertDoesNotExist()
    }

    @Test
    fun aPlanThatIsNotACut_hasNoSwitch() {
        show(phase = Phase.Maintenance)

        rule.onNodeWithTag("trainer_plan_diet_break_switch").assertDoesNotExist()
    }

    @Test
    fun offline_theSwitchIsDisabledWithTheStandardHint() {
        show(online = false)

        rule.onNodeWithTag("trainer_plan_diet_break_switch").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede cambiar el descanso de dieta").assertExists()
    }

    @Test
    fun online_theSwitchIsEnabled() {
        show()

        rule.onNodeWithTag("trainer_plan_diet_break_switch").assertIsEnabled()
    }
}
