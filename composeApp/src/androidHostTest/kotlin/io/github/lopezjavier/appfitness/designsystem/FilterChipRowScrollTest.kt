package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class FilterChipRowScrollTest {
    @get:Rule
    val rule = createComposeRule()

    private val options = List(8) { AppOption("Opcion $it", "chip_$it") }

    @Composable
    private fun Row(selected: Int, scrollToSelected: Boolean, onSelect: (Int) -> Unit = {}) {
        AppTheme {
            FilterChipRow(
                options = options,
                selectedIndex = selected,
                onSelect = onSelect,
                modifier = Modifier,
                scrollToSelected = scrollToSelected,
            )
        }
    }

    @Test
    fun scrollToSelected_bringsTheLastChipIntoView() {
        rule.setContent { Row(selected = 7, scrollToSelected = true) }
        rule.waitForIdle()

        rule.onNodeWithTag("chip_7").assertIsDisplayed()
    }

    @Test
    fun withoutScrollToSelected_theLastChipStaysOffScreen() {
        rule.setContent { Row(selected = 7, scrollToSelected = false) }
        rule.waitForIdle()

        assertFailsWith<AssertionError> { rule.onNodeWithTag("chip_7").assertIsDisplayed() }
    }

    @Test
    fun scrollToSelected_followsASelectionChange() {
        var selected by mutableIntStateOf(0)
        rule.setContent { Row(selected, scrollToSelected = true) }
        rule.waitForIdle()

        selected = 7
        rule.waitForIdle()

        rule.onNodeWithTag("chip_7").assertIsDisplayed()
    }

    @Test
    fun scrollToSelected_stillSelectsOnTap() {
        var tapped = -1
        rule.setContent { Row(selected = 0, scrollToSelected = true) { tapped = it } }
        rule.waitForIdle()

        rule.onNodeWithTag("chip_1").performClick()

        kotlin.test.assertEquals(1, tapped)
    }
}
