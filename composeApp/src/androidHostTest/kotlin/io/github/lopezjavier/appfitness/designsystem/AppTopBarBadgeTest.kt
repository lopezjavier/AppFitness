package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_notifications
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** T-135: the count badge of a top bar action (the bell of the tab roots). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class AppTopBarBadgeTest {
    @get:Rule
    val rule = createComposeRule()

    private fun bar(count: Int, onClick: () -> Unit = {}) = rule.setContent {
        AppTheme {
            AppTopBar(
                title = "Perfil",
                actions = listOf(
                    AppTopBarAction(
                        Res.drawable.ic_notifications,
                        "Centro de avisos",
                        onClick,
                        "bell",
                        badgeCount = count,
                    ),
                ),
            )
        }
    }

    @Test
    fun countAboveZero_drawsTheBadge() {
        bar(2)

        rule.onNodeWithTag(ICON_BUTTON_BADGE_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun zero_drawsNoBadge() {
        bar(0)

        rule.onAllNodesWithTag(ICON_BUTTON_BADGE_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun badgedAction_stillClicks() {
        var clicks = 0
        bar(3) { clicks++ }

        rule.onNodeWithTag("bell").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun badgeLabel_showsTheNumberUpToNineThenOverflow() {
        assertEquals("1", badgeLabel(1, "9+"))
        assertEquals("9", badgeLabel(9, "9+"))
        assertEquals("9+", badgeLabel(10, "9+"))
    }
}
