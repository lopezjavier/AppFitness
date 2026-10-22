package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppDropdownFieldTest {
    @get:Rule
    val rule = createComposeRule()

    private val options = listOf(AppDropdownOption("waist", "Cintura"), AppDropdownOption("hip", "Cadera"))
    private var selected by mutableStateOf("waist")

    private fun show() {
        selected = "waist"
        rule.setContent {
            AppTheme {
                AppDropdownField(
                    label = "Zona",
                    options = options,
                    selectedKey = selected,
                    onSelect = { selected = it },
                    testTagPrefix = "dd",
                )
            }
        }
    }

    @Test
    fun showsTheSelectedLabel_andHidesTheOptionsUntilOpened() {
        show()

        rule.onNodeWithTag("dd_field").assertTextContains("Cintura", substring = true)
        rule.onNodeWithTag("dd_option_hip").assertDoesNotExist()
    }

    @Test
    fun opening_listsEveryOption_andChoosingOneSelectsItAndCloses() {
        show()

        rule.onNodeWithTag("dd_field").performClick()
        rule.onNodeWithTag("dd_option_waist").assertExists()
        rule.onNodeWithTag("dd_option_hip").performClick()
        rule.waitForIdle()

        assertEquals("hip", selected)
        rule.onNodeWithTag("dd_field").assertTextContains("Cadera", substring = true)
        rule.onNodeWithTag("dd_option_waist").assertDoesNotExist()
    }

    @Test
    fun theSelectedOptionIsExposedAsSelected_andTargetsAreAtLeast48dp() {
        show()

        rule.onNodeWithTag("dd_field").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("dd_field").performClick()

        rule.onNodeWithTag("dd_option_hip").assertHeightIsAtLeast(48.dp)
        val selectedFlag = { tag: String ->
            rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
        }
        assertEquals(true, selectedFlag("dd_option_waist"))
        assertEquals(false, selectedFlag("dd_option_hip"))
    }
}
