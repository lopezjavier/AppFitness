package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppNavigationType
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-04 tablet layout: the side rail carries the tabs and the FAB from 600 dp wide. */
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
        rule.onNodeWithTag("fab").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w599dp-h960dp-mdpi")
    fun app_at599dp_showsBottomBar_noRail() {
        rule.setContent { App() }
        rule.onNodeWithTag("nav_bar").assertIsDisplayed()
        rule.onNodeWithTag("nav_rail").assertDoesNotExist()
        rule.onNodeWithTag("fab").assertIsDisplayed()
    }

    @Test
    fun rail_fabSitsAboveTheTabs() {
        setRailContent()
        val fab = rule.onNodeWithTag("fab").getUnclippedBoundsInRoot()
        val rail = rule.onNodeWithTag("nav_rail").getUnclippedBoundsInRoot()
        val tab = rule.onNodeWithTag("nav_tab_diary").getUnclippedBoundsInRoot()
        assertTrue("fab bottom ${fab.bottom} <= tab top ${tab.top}", fab.bottom <= tab.top)
        assertTrue("fab inside rail", fab.left >= rail.left && fab.right <= rail.right)
    }

    @Test
    fun rail_fabAtDoubleFontScale_staysInsideRailAboveTabs() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AppTheme { AppScaffold(navigationType = AppNavigationType.Rail) }
            }
        }
        val fab = rule.onNodeWithTag("fab").getUnclippedBoundsInRoot()
        val rail = rule.onNodeWithTag("nav_rail").getUnclippedBoundsInRoot()
        val tab = rule.onNodeWithTag("nav_tab_diary").getUnclippedBoundsInRoot()
        assertTrue("fab right ${fab.right} <= rail right ${rail.right}", fab.right <= rail.right)
        assertTrue("fab bottom ${fab.bottom} <= tab top ${tab.top}", fab.bottom <= tab.top)
    }

    @Test
    fun rail_fabOpensContextualSheet() {
        setRailContent()
        rule.onNodeWithTag("nav_tab_progress").performClick()
        rule.onNodeWithTag("fab").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("wip_sheet_title").assertTextEquals("Registrar peso")
    }

    @Test
    fun rail_profileHidesRailAndFab_backRestoresThem() {
        setRailContent()
        rule.onNodeWithTag("topbar_avatar").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("nav_rail").assertDoesNotExist()
        rule.onNodeWithTag("fab").assertDoesNotExist()
        rule.onNodeWithTag("topbar_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("nav_rail").assertIsDisplayed()
        rule.onNodeWithTag("nav_tab_diary").assertIsSelected()
    }
}
