package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [SummaryCard]: a tappable card whose lines carry the key facts. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class SummaryCardTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsTitleAndLines_andClicks() {
        var clicks = 0
        rule.setContent {
            AppTheme { SummaryCard("Tu plan", listOf("Definición · 2.100 kcal", "Cardio 90 min"), { clicks++ }, "box") }
        }

        rule.onNodeWithText("Tu plan").assertIsDisplayed()
        rule.onNodeWithText("Definición · 2.100 kcal").assertIsDisplayed()
        rule.onNodeWithTag("box").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun isAtLeast48dp_withNoLines() {
        rule.setContent { AppTheme { SummaryCard("Tu bloque", emptyList(), {}, "box") } }

        rule.onNodeWithTag("box").assertHeightIsAtLeast(48.dp)
    }
}
