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
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
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
}
