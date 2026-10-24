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
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
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

    private fun viewModel(state: SavedStateHandle) = WorkoutCalendarViewModel(
        repository,
        FakeTrainingPreferencesRepository(),
        MutableClock(),
        FakeWeightRepository(),
        FakeExerciseRepository(listOf(exerciseRow("e1", "bench"))),
        state,
        TimeZone.UTC,
    )

    @Composable
    private fun Host(model: WorkoutCalendarViewModel) {
        val state by model.uiState.collectAsState()
        AppTheme { WorkoutCalendarScreen(state, model::onEvent) }
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

    @Test
    fun selectedDayAndItsCard_surviveRecreationFromTheSameSavedState() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(viewModel(savedState)) }
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_day_2026-10-19").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("calendar_day_card_a").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("month_calendar_day_2026-10-19").assertIsSelected()
        rule.onNodeWithTag("calendar_day_card_a").assertExists()
    }
}
