package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppNavigationType
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-04 tablet layout: tab content next to the rail, and switching layouts keeps state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h1280dp-mdpi")
class ShellRailContentTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setRailContent() = rule.setContent {
        AppTheme { AppScaffold(navigationType = AppNavigationType.Rail) }
    }

    @Test
    fun rail_tabContentNotCoveredAndNoBottomGap() {
        setRailContent()
        val rail = rule.onNodeWithTag("nav_rail").getUnclippedBoundsInRoot()
        val screen = rule.onNodeWithTag("diary_screen").getUnclippedBoundsInRoot()
        assertTrue("content left ${screen.left} >= rail right ${rail.right}", screen.left >= rail.right)
        rule.onNodeWithTag("diary_screen").performTouchInput { repeat(3) { swipeUp() } }
        rule.waitForIdle()
        val footer = rule.onNodeWithTag("diary_wip_footer").getUnclippedBoundsInRoot()
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        // Robolectric has no system bars, so the footer should sit within one content padding of the bottom.
        assertTrue("footer bottom ${footer.bottom} vs root ${root.bottom}", root.bottom - footer.bottom <= 32.dp)
    }

    @Test
    fun switchingBarToRail_keepsActiveTabAndScroll() {
        var type by mutableStateOf(AppNavigationType.Bar)
        rule.setContent { AppTheme { AppScaffold(navigationType = type) } }
        rule.onNodeWithTag("nav_tab_diary").performClick()
        rule.waitForIdle()
        // Scrolled past the first item (the message): a reset to the top would bring it back.
        rule.onNodeWithTag("diary_screen").performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, SCROLL_BY_PX)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("diary_wip_message").assertDoesNotExist()
        type = AppNavigationType.Rail
        rule.waitForIdle()
        rule.onNodeWithTag("nav_tab_diary").assertIsSelected()
        rule.onNodeWithTag("diary_wip_message").assertDoesNotExist()
        type = AppNavigationType.Bar
        rule.waitForIdle()
        rule.onNodeWithTag("nav_tab_diary").assertIsSelected()
        rule.onNodeWithTag("diary_wip_message").assertDoesNotExist()
    }

    @Test
    fun rail_itemsKeepSemantics() {
        setRailContent()
        listOf("diary" to "Diario", "workout" to "Entreno", "profile" to "Perfil").forEach { (tab, label) ->
            val node = rule.onNodeWithTag("nav_tab_$tab")
            node.assertContentDescriptionEquals(label)
            val bounds = node.getUnclippedBoundsInRoot()
            val width = bounds.right - bounds.left
            val height = bounds.bottom - bounds.top
            assertTrue("$tab width $width", width >= 48.dp)
            assertTrue("$tab height $height", height >= 48.dp)
        }
        rule.onNodeWithTag("nav_tab_diary").assertIsSelected()
        rule.onNodeWithTag("nav_tab_workout").assertIsNotSelected()
    }
}

private const val SCROLL_BY_PX = 600f
