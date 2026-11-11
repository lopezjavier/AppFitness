package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        rule.setContent { AppTheme { TrainerWithBoxes(state, flowOf(true), {}) } }
        rule.waitForIdle()
        rule.openTrainerBlock()
    }

    private val end = LocalDate(2026, 10, 24)

    @Test
    fun pausedBlock_showsTheChipAndTheMovedEndDate() {
        showTrainer(trainer(BlockPause(paused = true, pausedWeeks = 1, blockEnd = end)))

        rule.onNodeWithTag("trainer_block_paused").assertTextEquals("Semana en pausa")
        rule.onNodeWithTag("trainer_block_end").assertTextEquals("El bloque termina el 24 de octubre")
    }

    @Test
    fun afterResume_theEndDateStaysAndTheChipGoes() {
        showTrainer(trainer(BlockPause(paused = false, pausedWeeks = 1, blockEnd = end)))

        rule.onNodeWithTag("trainer_block_paused").assertDoesNotExist()
        rule.onNodeWithTag("trainer_block_end").assertExists()
    }

    @Test
    fun withoutAnyPausedWeek_neitherTheChipNorTheEndLineShow() {
        showTrainer(trainer(BlockPause(paused = false, pausedWeeks = 0, blockEnd = end)))

        rule.onNodeWithTag("trainer_block_paused").assertDoesNotExist()
        rule.onNodeWithTag("trainer_block_end").assertDoesNotExist()
    }

    @Test
    fun unknownPause_hidesBothLinesButKeepsTheWeeks() {
        showTrainer(trainer(null))

        rule.onNodeWithTag("trainer_block_paused").assertDoesNotExist()
        rule.onNodeWithTag("trainer_weeks_section").assertExists()
    }

    @Test
    fun pausedSummary_readsOnlySemanaEnPausa() {
        val state = WeekSummaryUiState.Content(
            WeekSummary.pausedWeek("plan-1", LocalDate(2026, 11, 3), LocalDate(2026, 11, 9)),
            emptyMap(),
        )
        rule.setContent { AppTheme { WeekSummaryScreen(state, flowOf(true), {}, {}) } }
        rule.waitForIdle()

        rule.onNodeWithTag("week_summary_paused").assertTextEquals("Semana en pausa")
        rule.onNodeWithTag("week_summary_subtitle").assertTextEquals("3 al 9 nov")
        rule.onNodeWithTag("week_summary_section_sessions").assertDoesNotExist()
        rule.onNodeWithTag("week_summary_section_nutrition").assertDoesNotExist()
        rule.onNodeWithTag("week_summary_section_proposal").assertDoesNotExist()
    }

    @Test
    fun recreation_keepsThePausedChipAndEndLine() {
        val tester = StateRestorationTester(rule)
        val state = trainer(BlockPause(paused = true, pausedWeeks = 1, blockEnd = end))
        tester.setContent { AppTheme { TrainerWithBoxes(state, flowOf(true), {}) } }
        rule.openTrainerBlock()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_block_paused").assertExists()
        rule.onNodeWithTag("trainer_block_end").assertExists()
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
