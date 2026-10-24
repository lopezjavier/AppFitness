package io.github.lopezjavier.appfitness.feature.workout

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
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The view and the shown month survive recreation of the calendar (ADR-40). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class WorkoutCalendarRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeWorkoutSessionRepository().apply {
        completed += completedSession("a", 0)
    }

    private fun viewModel(state: SavedStateHandle) =
        WorkoutCalendarViewModel(repository, MutableClock(), state, TimeZone.UTC)

    @Composable
    private fun Host(model: WorkoutCalendarViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { WorkoutCalendarScreen(state, model::onEvent, zone = TimeZone.UTC) }
    }

    @Test
    fun monthAndView_surviveRecreationFromTheSameSavedState() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel(savedState)) }
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_prev").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("calendar_view_years").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("calendar_view_years").assertIsSelected()
        rule.onNodeWithTag("calendar_view_month").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_title").assertTextEquals("septiembre 2026")
    }
}
