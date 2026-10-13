package io.github.lopezjavier.appfitness.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-04: the FAB is contextual per tab and hidden on full-screen destinations. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ShellFabTest {
    @get:Rule
    val rule = createComposeRule()

    private fun openSheetAndAssertTitle(title: String) {
        rule.onNodeWithTag("fab").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("wip_sheet_title").assertTextEquals(title)
    }

    @Test
    fun diary_fabOpensAddFoodSheet() {
        rule.setContent { AppTheme { AppScaffold() } }
        openSheetAndAssertTitle("Añadir alimento")
    }

    @Test
    fun workout_fabOpensStartWorkoutSheet() {
        rule.setContent { AppTheme { AppScaffold() } }
        rule.onNodeWithTag("nav_tab_workout").performClick()
        openSheetAndAssertTitle("Empezar entreno")
    }

    @Test
    fun progress_fabOpensLogWeightSheet() {
        rule.setContent { AppTheme { AppScaffold() } }
        rule.onNodeWithTag("nav_tab_progress").performClick()
        openSheetAndAssertTitle("Registrar peso")
    }

    @Test
    fun profile_hidesFab() {
        rule.setContent { AppTheme { AppScaffold() } }
        rule.onNodeWithTag("fab").assertIsDisplayed()
        rule.onNodeWithTag("topbar_avatar").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("fab").assertDoesNotExist()
    }
}
