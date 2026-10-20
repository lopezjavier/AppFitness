package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T-050: the shell keeps the tab and the screen across a transient root route and across recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class StateRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun mainLoadingMain_keepsTheSelectedTab() {
        var route by mutableStateOf<RootRoute>(RootRoute.Main)
        rule.setContent { App(rootRoute = route) }
        rule.onNodeWithTag("nav_tab_profile").performClick()
        rule.waitForIdle()

        route = RootRoute.Loading
        rule.waitForIdle()
        route = RootRoute.Main
        rule.waitForIdle()

        rule.onNodeWithTag("nav_tab_profile").assertIsSelected()
    }

    @Test
    fun recreation_keepsTheSelectedTabAndScreen() {
        val tester = StateRestorationTester(rule)
        tester.setContent { App(rootRoute = RootRoute.Main) }
        rule.onNodeWithTag("nav_tab_profile").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("profile_stub_open_preferences").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("preferences_screen").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("preferences_screen").assertExists()
    }
}
