package io.github.lopezjavier.appfitness.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppNavigationType
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-04 tablet layout: the side rail carries the tabs from 600 dp wide. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h1280dp-mdpi")
class ShellRailTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setRailContent() = rule.setContent {
        AppTheme { AppScaffold(navigationType = AppNavigationType.Rail) }
    }

    @Test
    @Config(qualifiers = "w600dp-h960dp-mdpi")
    fun app_at600dp_showsRail_noBottomBar() {
        rule.setContent { App() }
        rule.onNodeWithTag("nav_rail").assertIsDisplayed()
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w599dp-h960dp-mdpi")
    fun app_at599dp_showsBottomBar_noRail() {
        rule.setContent { App() }
        rule.onNodeWithTag("nav_bar").assertIsDisplayed()
        rule.onNodeWithTag("nav_rail").assertDoesNotExist()
    }

    @Test
    fun rail_hasThreeItems_andProfileKeepsTheRail() {
        setRailContent()
        listOf("nav_tab_diary", "nav_tab_workout", "nav_tab_trainer", "nav_tab_profile").forEach {
            rule.onNodeWithTag(it).assertIsDisplayed()
        }
        rule.onNodeWithTag("nav_tab_profile").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("nav_rail").assertIsDisplayed()
        rule.onNodeWithTag("nav_tab_profile").assertIsSelected()
        rule.onNodeWithTag("profile_screen").assertIsDisplayed()
    }
}
