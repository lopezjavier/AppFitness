package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-106: "Semana en pausa" on the Trainer weeks section and on the weekly summary, also after recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class BlockPauseScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun trainer(pause: BlockPause?) = TrainerUiState.Content(
        plan = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)),
        block = BlockSummary.Saved(coachBlock(saved = true)),
        weeks = listOf(planWeek()),
        pause = pause,
    )

    private fun showTrainer(state: TrainerUiState) {
        rule.setContent { AppTheme { TrainerScreen(state, flowOf(true), {}) } }
        rule.waitForIdle()
    }

    private val end = LocalDate(2026, 10, 24)

    @Test
    fun pausedBlock_showsEnPausaOnTheCurrentWeekAndTheMovedEndDate() {
        showTrainer(trainer(BlockPause(paused = true, pausedWeeks = 1, blockEnd = end)))

        rule.onNodeWithText("En pausa", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(
            "trainer_box_block_end",
            useUnmergedTree = true,
        ).assertTextEquals("El bloque termina el 24 oct.")
    }

    @Test
    fun afterResume_theEndDateStaysAndTheChipReadsActual() {
        showTrainer(trainer(BlockPause(paused = false, pausedWeeks = 1, blockEnd = end)))

        rule.onNodeWithText("Actual", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("trainer_box_block_end", useUnmergedTree = true).assertExists()
    }

    @Test
    fun withoutAnyPausedWeek_theEndLineIsNotShown() {
        showTrainer(trainer(BlockPause(paused = false, pausedWeeks = 0, blockEnd = end)))

        rule.onNodeWithText("Actual", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("trainer_box_block_end", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun unknownPause_hidesTheEndLineButKeepsTheWeeks() {
        showTrainer(trainer(null))

        rule.onNodeWithTag("trainer_box_block_end", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("trainer_box_block_weeks").assertExists()
    }

    @Test
    fun pausedSummary_readsOnlySemanaEnPausa() {
        val state = WeekSummaryUiState.Content(
            WeekSummary.pausedWeek("plan-1", LocalDate(2026, 11, 3), LocalDate(2026, 11, 9)),
            emptyMap(),
        )
        rule.setContent { AppTheme { WeekSummaryScreen(state, flowOf(true), {}, {}) } }
        rule.waitForIdle()

        rule.onNodeWithTag("week_summary_paused").assertTextContains("Semana en pausa", substring = true)
        rule.onNodeWithTag("week_summary_subtitle").assertTextEquals("3 al 9 nov")
        rule.onNodeWithTag("week_summary_coach_card").assertDoesNotExist()
        rule.onNodeWithTag("week_summary_section_strength").assertDoesNotExist()
        rule.onNodeWithTag("week_summary_section_proposal").assertDoesNotExist()
    }

    @Test
    fun recreation_keepsThePausedChipAndEndLine() {
        val tester = StateRestorationTester(rule)
        val state = trainer(BlockPause(paused = true, pausedWeeks = 1, blockEnd = end))
        tester.setContent { AppTheme { TrainerScreen(state, flowOf(true), {}) } }

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithText("En pausa", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("trainer_box_block_end", useUnmergedTree = true).assertExists()
    }

    @Test
    fun recreation_keepsThePausedSummary() {
        val tester = StateRestorationTester(rule)
        val state = WeekSummaryUiState.Content(
            WeekSummary.pausedWeek("plan-1", LocalDate(2026, 11, 3), LocalDate(2026, 11, 9)),
            emptyMap(),
        )
        tester.setContent { AppTheme { WeekSummaryScreen(state, flowOf(true), {}, {}) } }

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("week_summary_paused").assertExists()
    }
}
