package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.usecase.BuildDiaryDay
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Yesterday = LocalDate(2026, 10, 18)

/** US-19 "Changing the day" with the real ViewModel, and the day and collapsed sections surviving recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class DiaryRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val diary = FakeFoodDiaryRepository().apply {
        server[Yesterday] = listOf(
            FoodLogEntry("y", Yesterday, "lunch", "f", "Lentejas", 300.0, null, "g", 420.0, 20.0, 60.0, 5.0),
        )
    }

    private fun viewModel(state: SavedStateHandle) = DiaryViewModel(
        FakeMealSlotRepository(),
        diary,
        FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))),
        BuildDiaryDay(),
        MutableClock(),
        FakeConnectivityObserver(),
        state,
    )

    @Composable
    private fun Host(model: DiaryViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { DiaryScreen(state, model::onEvent) }
    }

    @Test
    fun changingTheDay_showsYesterdayWithItsEntriesAndTodayIsReachableAgain() {
        val model = viewModel(SavedStateHandle())
        rule.setContent { Host(model) }
        rule.waitForIdle()
        rule.onNodeWithTag("diary_date_label").assertTextEquals("Hoy")
        rule.onNodeWithTag("diary_date_next").assertIsNotEnabled()

        rule.onNodeWithTag("diary_date_prev").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("diary_date_label").assertTextEquals("Ayer")
        rule.onNodeWithTag("diary_entry_y").assertExists()
        rule.onNodeWithTag("diary_date_next").assertIsEnabled().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("diary_date_label").assertTextEquals("Hoy")
        rule.onNodeWithTag("diary_entry_y").assertDoesNotExist()
    }

    @Test
    fun dayAndCollapsedSection_surviveRecreationFromTheSameSavedState() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel(savedState)) }
        rule.waitForIdle()
        rule.onNodeWithTag("diary_date_prev").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("diary_section_lunch_toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("diary_entry_y").assertDoesNotExist()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("diary_date_label").assertTextEquals("Ayer")
        rule.onNodeWithTag("diary_section_lunch").assertExists()
        rule.onNodeWithTag("diary_entry_y").assertDoesNotExist()
        rule.onNodeWithTag("diary_section_lunch_toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("diary_entry_y").assertExists()
    }

    @Test
    fun dayRouteArgument_opensThatDay_andTheDayShownAfterNavigatingWinsOnRestoration() {
        val savedState = SavedStateHandle(mapOf("diary_date" to Yesterday.toString()))
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel(savedState)) }
        rule.waitForIdle()
        rule.onNodeWithTag("diary_date_label").assertTextEquals("Ayer")

        rule.onNodeWithTag("diary_date_next").performClick()
        rule.waitForIdle()
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("diary_date_label").assertTextEquals("Hoy")
    }

    @Test
    fun dayPicker_pickingAPastDayShowsItAndItsEntries() {
        val model = viewModel(SavedStateHandle())
        rule.setContent { Host(model) }
        rule.waitForIdle()

        rule.onNodeWithTag("diary_day_picker_open").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_day_2026-10-18").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("diary_day_picker_sheet").assertDoesNotExist()
        rule.onNodeWithTag("diary_date_label").assertTextEquals("Ayer")
        rule.onNodeWithTag("diary_entry_y").assertExists()
    }

    @Test
    fun dayPicker_openSheetAndShownMonth_surviveRecreation() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel(savedState)) }
        rule.waitForIdle()
        rule.onNodeWithTag("diary_day_picker_open").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_prev").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_day_2026-09-15").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("diary_day_picker_sheet").assertExists()
        rule.onNodeWithTag("month_calendar_day_2026-09-15").assertExists()
    }

    @Test
    fun goToDay_survivesViewModelRecreationFromTheSameSavedState() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel(savedState)) }
        rule.waitForIdle()
        rule.onNodeWithTag("diary_day_picker_open").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_day_2026-10-18").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("diary_date_label").assertTextEquals("Ayer")
    }
}
