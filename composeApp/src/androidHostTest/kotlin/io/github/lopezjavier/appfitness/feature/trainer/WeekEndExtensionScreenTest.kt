package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-134: the pencil of the active week's last day, its picker and the states around it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One test per scenario.
class WeekEndExtensionScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainerEvent>()

    // Week 1 is Oct 19-25; one later week, so the block ends on Nov 1. The week can end from Oct 25 to Oct 29.
    private val pause = BlockPause(
        paused = false,
        pausedWeeks = 0,
        blockEnd = LocalDate(2026, 11, 1),
        minEnd = LocalDate(2026, 11, 1),
        maxEnd = LocalDate(2026, 11, 5),
        extendable = true,
    )

    private fun trainer(
        weeks: List<PlanWeek> = listOf(planWeek()),
        pause: BlockPause? = this.pause,
        pickerOpen: Boolean = false,
        busy: Boolean = false,
    ) = TrainerUiState.Content(
        plan = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted)),
        block = BlockSummary.Saved(coachBlock(saved = true)),
        weeks = weeks,
        pause = pause,
        extendWeekPickerOpen = pickerOpen,
        extendingWeek = busy,
    )

    private fun show(state: TrainerUiState, online: Boolean = true) {
        rule.setContent { AppTheme { TrainerWithBoxes(state, flowOf(online), { events += it }) } }
        rule.waitForIdle()
        rule.openTrainerBlock()
    }

    private fun day(number: Int) = rule.onNode(hasText("October $number, 2026", substring = true))

    private fun inWeek(week: Int, tag: String) = hasTestTag(tag) and hasAnyAncestor(hasTestTag("trainer_week_$week"))

    @Test
    fun theActiveWeek_showsThePencilAndHelpOnItsDateRow() {
        show(trainer())

        rule.onNode(hasText("19 al 25 oct")).assertExists()
        rule.onNodeWithTag("trainer_week_extend_edit").assertIsEnabled()
        rule.onNodeWithTag("trainer_week_extend_help").assertExists()
    }

    @Test
    fun pastAndUpcomingWeeks_haveNeitherPencilNorHelp() {
        val weeks = listOf(
            planWeek(week = 1, phase = WeekPhase.Past),
            planWeek(week = 2, phase = WeekPhase.Current),
            planWeek(week = 3, phase = WeekPhase.Upcoming),
        )
        val state = trainer(
            weeks,
            pause.copy(
                blockEnd = LocalDate(2026, 11, 8),
                minEnd = LocalDate(2026, 11, 8),
                maxEnd = LocalDate(2026, 11, 12),
            ),
        )
        show(state)

        rule.onAllNodesWithTag("trainer_week_extend_edit").assertCountEquals(1)
        rule.onAllNodesWithTag("trainer_week_extend_help").assertCountEquals(1)
        rule.onNode(inWeek(2, "trainer_week_extend_edit")).assertExists()
        rule.onNode(inWeek(2, "trainer_week_extend_help")).assertExists()
        rule.onNode(inWeek(1, "trainer_week_extend_edit")).assertDoesNotExist()
        rule.onNode(inWeek(3, "trainer_week_extend_edit")).assertDoesNotExist()
    }

    @Test
    fun withoutAnActiveBlockEnd_noPencil() {
        show(trainer(pause = BlockPause(paused = false, pausedWeeks = 0, blockEnd = null)))

        rule.onNodeWithTag("trainer_week_extend_edit").assertDoesNotExist()
        rule.onNodeWithTag("trainer_week_extend_help").assertDoesNotExist()
    }

    @Test
    fun whenNothingCanBeExtended_noPencil() {
        show(trainer(pause = pause.copy(extendable = false)))

        rule.onNodeWithTag("trainer_week_extend_edit").assertDoesNotExist()
        rule.onNodeWithTag("trainer_week_extend_help").assertDoesNotExist()
    }

    @Test
    fun withoutAnActiveWeek_noPencil() {
        show(trainer(weeks = listOf(planWeek(phase = WeekPhase.Upcoming))))

        rule.onNodeWithTag("trainer_week_extend_edit").assertDoesNotExist()
    }

    @Test
    fun tappingThePencil_asksToOpenThePicker() {
        show(trainer())

        rule.onNodeWithTag("trainer_week_extend_edit").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.OpenExtendWeekPicker), events)
    }

    @Test
    fun theOpenPicker_disablesDaysOutsideTheWeekRange() {
        show(trainer(pickerOpen = true))

        rule.onNodeWithTag("trainer_week_extend_picker").assertExists()
        day(24).assertIsNotEnabled()
        day(25).assertIsEnabled()
        day(29).assertIsEnabled()
        day(30).assertIsNotEnabled()
    }

    @Test
    fun confirmingAPickedDay_sendsExtendWeekWithThatDay() {
        show(trainer(pickerOpen = true))

        day(27).performClick()
        rule.onNodeWithTag("trainer_week_extend_picker_confirm").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.ExtendWeek(LocalDate(2026, 10, 27))), events)
    }

    @Test
    fun confirmingWithoutChange_sendsTheWeeksCurrentLastDay() {
        show(trainer(pickerOpen = true))

        rule.onNodeWithTag("trainer_week_extend_picker_confirm").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.ExtendWeek(LocalDate(2026, 10, 25))), events)
    }

    @Test
    fun cancellingThePicker_sendsDismissOnly() {
        show(trainer(pickerOpen = true))

        rule.onNodeWithTag("trainer_week_extend_picker_dismiss").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.DismissExtendWeekPicker), events)
    }

    @Test
    fun afterAPick_theWeekShowsItsNewDateRange() {
        val state = mutableStateOf(trainer(pickerOpen = true))
        rule.setContent {
            AppTheme {
                TrainerWithBoxes(
                    state.value,
                    flowOf(true),
                    { event ->
                        if (event is TrainerEvent.ExtendWeek) {
                            val moved = state.value.weeks.map { it.copy(endDate = event.day) }
                            state.value = state.value.copy(weeks = moved, extendWeekPickerOpen = false)
                        }
                    },
                )
            }
        }
        rule.waitForIdle()
        rule.openTrainerBlock()

        day(27).performClick()
        rule.onNodeWithTag("trainer_week_extend_picker_confirm").performClick()
        rule.waitForIdle()

        rule.onNode(hasText("19 al 27 oct")).assertExists()
        rule.onNodeWithTag("trainer_week_extend_picker").assertDoesNotExist()
    }

    @Test
    fun offline_thePencilIsDisabledWithTheHintInsideTheCard() {
        show(trainer(), online = false)

        rule.onNodeWithTag("trainer_week_extend_edit").assertIsNotEnabled()
        rule.onNode(
            hasText("Sin conexión: no se puede cambiar el último día") and hasAnyAncestor(hasTestTag("trainer_week_1")),
        ).assertExists()
    }

    @Test
    fun whileChanging_thePencilIsDisabled() {
        show(trainer(busy = true))

        rule.onNodeWithTag("trainer_week_extend_edit").assertIsNotEnabled()
    }

    @Test
    fun withoutAPausedWeek_theBlockEndLineIsNotShown() {
        show(trainer())

        rule.onNodeWithTag("trainer_block_end").assertDoesNotExist()
    }

    @Test
    fun afterAPausedWeek_theBlockEndLineShowsWithoutPencilOrHelpNextToIt() {
        show(trainer(pause = pause.copy(pausedWeeks = 1)))

        rule.onNodeWithTag("trainer_block_end").assertExists()
        rule.onAllNodesWithTag("trainer_week_extend_edit").assertCountEquals(1)
        rule.onNode(inWeek(1, "trainer_week_extend_edit")).assertExists()
    }

    @Test
    fun recreation_keepsThePickedDayOfTheOpenPicker() {
        val tester = StateRestorationTester(rule)
        val state = trainer(pickerOpen = true)
        tester.setContent { AppTheme { TrainerWithBoxes(state, flowOf(true), { events += it }) } }
        rule.openTrainerBlock()
        day(28).performClick()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_week_extend_picker_confirm").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.ExtendWeek(LocalDate(2026, 10, 28))), events)
    }

    @Test
    fun recreation_keepsThePencilOnTheActiveWeek() {
        val tester = StateRestorationTester(rule)
        val state = trainer()
        tester.setContent { AppTheme { TrainerWithBoxes(state, flowOf(true), { events += it }) } }
        rule.openTrainerBlock()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNode(inWeek(1, "trainer_week_extend_edit")).assertExists()
    }
}
