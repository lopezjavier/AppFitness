package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-134: the pencil of the block's last day, its picker and the states around it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One test per scenario.
class BlockEndExtensionScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainerEvent>()
    private val end = LocalDate(2026, 10, 24)
    private val extendable = BlockPause(
        paused = false,
        pausedWeeks = 0,
        blockEnd = end,
        minEnd = LocalDate(2026, 10, 20),
        maxEnd = LocalDate(2026, 10, 30),
        extendable = true,
    )

    private fun trainer(pause: BlockPause?, pickerOpen: Boolean = false, busy: Boolean = false) =
        TrainerUiState.Content(
            plan = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)),
            block = BlockSummary.Saved(coachBlock(saved = true)),
            weeks = listOf(planWeek()),
            pause = pause,
            extendPickerOpen = pickerOpen,
            extendingBlock = busy,
        )

    private fun show(state: TrainerUiState, online: Boolean = true) {
        rule.setContent { AppTheme { TrainerWithBoxes(state, flowOf(online), { events += it }) } }
        rule.waitForIdle()
        rule.openTrainerBlock()
    }

    private fun day(number: Int) = rule.onNode(hasText("October $number, 2026", substring = true))

    @Test
    fun aRunningBlock_showsTheEndDayWithThePencilAndHelp() {
        show(trainer(extendable))

        rule.onNodeWithTag("trainer_block_end").assertTextEquals("El bloque termina el 24 de octubre")
        rule.onNodeWithTag("trainer_block_end_edit").assertIsEnabled()
        rule.onNodeWithTag("trainer_block_end_help").assertExists()
    }

    @Test
    fun afterTheLastDay_theEndStaysWithoutPencil() {
        show(trainer(extendable.copy(extendable = false)))

        rule.onNodeWithTag("trainer_block_end").assertExists()
        rule.onNodeWithTag("trainer_block_end_edit").assertDoesNotExist()
        rule.onNodeWithTag("trainer_block_end_help").assertDoesNotExist()
    }

    @Test
    fun withoutABlockEnd_noLineAndNoPencil() {
        show(trainer(BlockPause(paused = false, pausedWeeks = 0, blockEnd = null)))

        rule.onNodeWithTag("trainer_block_end").assertDoesNotExist()
        rule.onNodeWithTag("trainer_block_end_edit").assertDoesNotExist()
    }

    @Test
    fun tappingThePencil_asksToOpenThePicker() {
        show(trainer(extendable))

        rule.onNodeWithTag("trainer_block_end_edit").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.OpenExtendPicker), events)
    }

    @Test
    fun theOpenPicker_disablesDaysOutsideTheRange() {
        show(trainer(extendable, pickerOpen = true))

        rule.onNodeWithTag("trainer_block_end_picker").assertExists()
        day(19).assertIsNotEnabled()
        day(20).assertIsEnabled()
        day(30).assertIsEnabled()
        day(31).assertIsNotEnabled()
    }

    @Test
    fun confirmingAPickedDay_sendsExtendBlock() {
        show(trainer(extendable, pickerOpen = true))

        day(27).performClick()
        rule.onNodeWithTag("trainer_block_end_picker_confirm").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.ExtendBlock(LocalDate(2026, 10, 27))), events)
    }

    @Test
    fun confirmingWithoutChange_sendsTheCurrentEnd() {
        show(trainer(extendable, pickerOpen = true))

        rule.onNodeWithTag("trainer_block_end_picker_confirm").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.ExtendBlock(end)), events)
    }

    @Test
    fun cancellingThePicker_sendsDismissOnly() {
        show(trainer(extendable, pickerOpen = true))

        rule.onNodeWithTag("trainer_block_end_picker_dismiss").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.DismissExtendPicker), events)
    }

    @Test
    fun offline_thePencilIsDisabledWithTheHint() {
        show(trainer(extendable), online = false)

        rule.onNodeWithTag("trainer_block_end_edit").assertIsNotEnabled()
        rule.onNode(hasText("Sin conexión: no se puede cambiar el último día")).assertExists()
    }

    @Test
    fun whileChanging_thePencilIsDisabled() {
        show(trainer(extendable, busy = true))

        rule.onNodeWithTag("trainer_block_end_edit").assertIsNotEnabled()
    }

    @Test
    fun recreation_keepsThePickedDayOfTheOpenPicker() {
        val tester = StateRestorationTester(rule)
        val state = trainer(extendable, pickerOpen = true)
        tester.setContent { AppTheme { TrainerWithBoxes(state, flowOf(true), { events += it }) } }
        rule.openTrainerBlock()
        day(28).performClick()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_block_end_picker_confirm").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.ExtendBlock(LocalDate(2026, 10, 28))), events)
    }
}
