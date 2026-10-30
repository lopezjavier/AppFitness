package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.App
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.RootRoute
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CoachUsage
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the Trainer tab, and the open edit sheet (with the stored level highlighted) survive recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TrainerRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun recreation_keepsTheTrainerTabSelected() {
        val tester = StateRestorationTester(rule)
        tester.setContent { App(rootRoute = RootRoute.Main) }
        rule.onNodeWithTag("nav_tab_trainer").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_trainer").assertIsSelected()
        rule.onNodeWithTag("trainer_screen").assertExists()
    }

    @Test
    fun recreatedViewModel_reopensTheSheet_withoutAFooter() {
        val saved = SavedStateHandle()
        val repository = FakeCoachRepository()
        val first = TrainerViewModel(repository, MutableClock(), saved)
        var current by mutableStateOf(first)
        val connectivity = FakeConnectivityObserver()
        rule.setContent { AppTheme { TrainerRoute(viewModel = current, connectivity = connectivity) } }
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_edit_activity_button").performClick()
        rule.waitForIdle()

        // A new view model over the same saved state, as after process death: the values are fetched again.
        current = TrainerViewModel(repository, MutableClock(), saved)
        rule.waitForIdle()

        rule.onNodeWithTag("activity_level_sheet_activity_light").assertIsSelected()
        rule.onNodeWithTag("activity_level_sheet_commit").assertDoesNotExist()
        assertTrue(repository.confirmCalls.isEmpty())
    }

    @Test
    fun recreatedViewModel_afterATap_startsWithTheSheetClosed() {
        val saved = SavedStateHandle()
        val repository = FakeCoachRepository()
        var current by mutableStateOf(TrainerViewModel(repository, MutableClock(), saved))
        val connectivity = FakeConnectivityObserver()
        rule.setContent { AppTheme { TrainerRoute(viewModel = current, connectivity = connectivity) } }
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_edit_activity_button").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("activity_level_sheet_activity_high").performClick()
        rule.waitForIdle()

        current = TrainerViewModel(repository, MutableClock(), saved)
        rule.waitForIdle()

        rule.onNodeWithTag("activity_level_sheet_activity_high").assertDoesNotExist()
    }

    private fun StateRestorationTester.setTrainerApp() {
        val usage = CoachUsage(LocalDate(2026, 11, 1), 0, 0, 0.42)
        val connectivity = FakeConnectivityObserver()
        setContent {
            App(
                rootRoute = RootRoute.Main,
                trainer = { modifier, open, _, _ ->
                    TrainerRoute(
                        modifier,
                        open,
                        viewModel = TrainerViewModel(FakeCoachRepository(), MutableClock(), SavedStateHandle()),
                        connectivity = connectivity,
                    )
                },
                coachUsage = { modifier, onBack ->
                    CoachUsageScreen(CoachUsageUiState.Content(usage), flowOf(true), {}, onBack, modifier)
                },
            )
        }
    }

    @Test
    fun recreation_keepsTheOverflowSheetOpen() {
        val tester = StateRestorationTester(rule)
        tester.setTrainerApp()
        rule.onNodeWithTag("nav_tab_trainer").performClick()
        rule.onNodeWithTag("trainer_overflow").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_action_coach_usage").assertExists()
    }

    @Test
    fun recreation_keepsTheUsageScreenOpen() {
        val tester = StateRestorationTester(rule)
        tester.setTrainerApp()
        rule.onNodeWithTag("nav_tab_trainer").performClick()
        rule.onNodeWithTag("trainer_overflow").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_action_coach_usage").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("coach_usage_figure").assertExists()
    }

    @Test
    fun recreatedViewModel_keepsThePlanSectionAndTheCalorieCard() {
        val plans = FakeCoachPlanRepository()
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        plans.pending = Result.success(calorieChange())
        val saved = SavedStateHandle()
        val repository = FakeCoachRepository()
        var current by mutableStateOf(TrainerViewModel(repository, MutableClock(), saved, plans = plans))
        val connectivity = FakeConnectivityObserver()
        rule.setContent { AppTheme { TrainerRoute(viewModel = current, connectivity = connectivity) } }
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_calorie_card").assertExists()

        // A new view model over the same saved state, as after process death: the plan is read again.
        current = TrainerViewModel(repository, MutableClock(), saved, plans = plans)
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_plan_card").assertExists()
        rule.onNodeWithTag("trainer_calorie_card").assertExists()
    }
}
