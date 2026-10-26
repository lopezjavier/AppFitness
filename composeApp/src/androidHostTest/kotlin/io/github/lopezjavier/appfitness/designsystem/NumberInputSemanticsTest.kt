package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [NumberInput] v2: select-all on focus, sentence-case label, inline range error and unit. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class NumberInputSemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    private var text by mutableStateOf("")

    private fun show(
        initial: String = "87,5",
        errorText: String? = null,
        unit: String? = null,
        showLabel: Boolean = true,
    ) {
        text = initial
        rule.setContent {
            AppTheme {
                NumberInput(
                    value = text,
                    onValueChange = { text = it },
                    label = "Peso (kg)",
                    modifier = Modifier.testTag("weight"),
                    maxDecimals = 1,
                    errorText = errorText,
                    errorTestTag = "weight_error",
                    unit = unit,
                    showLabel = showLabel,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun focus() {
        rule.onNodeWithTag("weight").performSemanticsAction(SemanticsActions.RequestFocus)
        rule.waitForIdle()
    }

    @Test
    fun focusing_selectsTheWholeValue_soTypingReplacesIt() {
        show("87,5")
        focus()

        rule.onNodeWithTag("weight").performTextInput("9")

        assertEquals("9", text)
    }

    @Test
    fun afterFocus_movingTheCursorKeepsTheRestOfTheValue() {
        show("87,5")
        focus()

        rule.onNodeWithTag("weight").performTextInputSelection(TextRange(1))
        rule.onNodeWithTag("weight").performTextInput("9")

        assertEquals("897,5", text)
    }

    @Test
    fun label_isShownInSentenceCase() {
        show()

        rule.onNodeWithText("Peso (kg)", useUnmergedTree = true).assertExists()
    }

    @Test
    fun field_keepsTheTouchTarget() {
        show()

        rule.onNodeWithTag("weight").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun rangeError_isShownInlineAndAnnouncedAsAnError() {
        show(errorText = "Entre 30 y 300 kg")

        rule.onNodeWithTag("weight_error", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("weight_error", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }

    @Test
    fun unit_isShownInsideTheField() {
        show(unit = "kg")

        rule.onNodeWithText("kg", useUnmergedTree = true).assertExists()
    }
}
