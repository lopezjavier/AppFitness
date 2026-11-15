package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

private val NBSP = 160.toChar().toString()

/**
 * The "Tu bloque" card of the Trainer root after the removal of the "Tu bloque" screen (T-283, R-106): the week
 * selector, the chosen week's detail, the extend-week picker hosted by the root and the states of the whole root
 * (skeleton and failed read, R-108).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TrainerRootBlockCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainerEvent>()

    private val accepted = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted))
    private val threeWeeks = listOf(
        planWeek(week = 1, phase = WeekPhase.Past),
        planWeek(week = 2, phase = WeekPhase.Current),
        planWeek(week = 3, phase = WeekPhase.Upcoming),
    )

    private fun state(weeks: List<PlanWeek>, pause: BlockPause? = null) = TrainerUiState.Content(
        plan = accepted,
        block = BlockSummary.Saved(coachBlock(saved = true)),
        weeks = weeks,
        pause = pause,
    )

    private fun show(state: TrainerUiState, online: Boolean = true) {
        rule.setContent { AppTheme { TrainerScreen(state, flowOf(online), { events += it }) } }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        rule.waitForIdle()
    }

    // Week selector (R-106)

    @Test
    fun weekSelector_startsOnTheCurrentWeek() {
        show(state(threeWeeks))

        rule.onNodeWithTag("trainer_box_block_week_2", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("trainer_box_block_week_1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun choosingAnUpcomingWeek_showsItsPlannedDetail() {
        show(state(threeWeeks))

        tap("trainer_box_block_weeks_item_3")

        rule.onNodeWithTag("trainer_box_block_week_3", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("trainer_box_block_planned", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("trainer_box_block_week_2", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun choosingAPastWeek_showsItsDoneOfPlannedHeadline() {
        show(state(threeWeeks))

        tap("trainer_box_block_weeks_item_1")

        rule.onNodeWithTag("trainer_box_block_headline", useUnmergedTree = true)
            .assertTextEquals("3${NBSP}de${NBSP}4")
    }

    @Test
    fun theChosenWeek_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { TrainerScreen(state(threeWeeks), flowOf(true), {}) } }
        tap("trainer_box_block_weeks_item_3")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_box_block_week_3", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aBlockLongerThanSixWeeks_titlesTheWeekWithItsTotal() {
        val weeks = (1..8).map {
            planWeek(week = it, phase = if (it == 3) WeekPhase.Current else WeekPhase.Upcoming)
        }
        show(state(weeks))

        rule.onNodeWithTag("trainer_box_block_week_title", useUnmergedTree = true)
            .assertTextEquals("Semana${NBSP}3${NBSP}de${NBSP}8")
    }

    // The extend-week picker is hosted by the root (R-103: opened from the block menu)

    private val extendable = BlockPause(
        paused = false,
        pausedWeeks = 0,
        blockEnd = LocalDate(2026, 11, 8),
        extensionDays = 0,
        minEnd = LocalDate(2026, 11, 6),
        maxEnd = LocalDate(2026, 11, 12),
        extendable = true,
    )

    @Test
    fun blockMenu_extendWeek_sendsTheOpenPickerEvent() {
        show(state(threeWeeks, extendable))

        tap("trainer_block_menu")
        tap("trainer_block_menu_extend_week")

        assertEquals(listOf<TrainerEvent>(TrainerEvent.OpenExtendWeekPicker), events)
    }

    @Test
    fun theOpenPicker_isHostedByTheRoot_confirmingSendsExtendWeekWithTheWeeksLastDay_andCancelDismisses() {
        show(state(threeWeeks, extendable).copy(extendWeekPickerOpen = true))

        rule.onNodeWithTag("trainer_week_extend_picker_accept").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_week_extend_picker_discard").performClick()
        rule.waitForIdle()

        assertEquals(
            listOf(TrainerEvent.ExtendWeek(threeWeeks[1].endDate), TrainerEvent.DismissExtendWeekPicker),
            events,
        )
    }

    @Test
    fun blockMenu_offline_extendWeekIsDisabled() {
        show(state(threeWeeks, extendable), online = false)

        tap("trainer_block_menu")

        rule.onNodeWithTag("trainer_block_menu_extend_week").assertIsNotEnabled()
    }

    // States of the root (R-108)

    @Test
    fun loading_showsTheSkeleton_neverTheNoPlanState() {
        show(TrainerUiState.Loading)

        rule.onNodeWithTag("trainer_loading", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("trainer_empty", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("trainer_create_plan", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun error_showsTheRetry_neverTheNoPlanState_andRetrySendsRefresh() {
        show(TrainerUiState.Error)

        rule.onNodeWithTag("trainer_empty", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("trainer_create_plan", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("trainer_error_action").performClick()

        assertEquals(listOf<TrainerEvent>(TrainerEvent.Refresh), events)
    }
}
