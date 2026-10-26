package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DiaryDaySummary
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val September15 = LocalDate(2026, 9, 15)

/** The viewed month, the selected day and the panel survive recreation of the history (ADR-40). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class DiaryHistoryRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val diary = FakeFoodDiaryRepository().apply {
        summaryDays += DiaryDaySummary(September15, MealTotals(1840.0, 142.0, 210.0, 48.0))
    }

    private fun viewModel(state: SavedStateHandle) = DiaryHistoryViewModel(
        diary,
        FakeNutritionGoalsRepository(NutritionGoals(GoalStrategy.Maintenance, 0, 2400, MacroSplit(30, 45, 25))),
        MutableClock(),
        state,
    )

    @Composable
    private fun Host(model: DiaryHistoryViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { DiaryHistoryScreen(state, model::onEvent, onBack = {}) }
    }

    @Test
    fun monthDayAndView_surviveRecreationFromTheSameSavedState() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel(savedState)) }
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_prev").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_day_$September15").performClick()
        rule.onNodeWithTag("diary_history_view_week").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("month_calendar_title").assertTextEquals("septiembre 2026")
        rule.onNodeWithTag("month_calendar_day_$September15").assertIsSelected()
        rule.onNodeWithTag("diary_history_view_week").assertIsSelected()
    }
}
