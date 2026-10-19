package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [AppTextField]: accessible name, touch target, length limit and error text. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppTextFieldSemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    private var text by mutableStateOf("")

    private fun show(initial: String = "", maxLength: Int = 10, errorText: String? = null, enabled: Boolean = true) {
        text = initial
        rule.setContent {
            AppTheme {
                AppTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "Nombre",
                    maxLength = maxLength,
                    errorText = errorText,
                    enabled = enabled,
                    testTagPrefix = "field",
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun label_isTheAccessibleName() {
        show()
        rule.onNodeWithTag("field").assertContentDescriptionEquals("Nombre")
    }

    @Test
    fun field_meetsTheTouchTarget() {
        show()
        rule.onNodeWithTag("field").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun typedText_reachesTheCaller() {
        show()
        rule.onNodeWithTag("field").performTextInput("Ana")
        rule.waitForIdle()
        assertEquals("Ana", text)
    }

    @Test
    fun text_isCutAtMaxLength() {
        show(maxLength = 5)
        rule.onNodeWithTag("field").performTextInput("Alexander")
        rule.waitForIdle()
        assertEquals("Alexa", text)
    }

    @Test
    fun errorText_isShownBelowTheField() {
        show(errorText = "Escribe tu nombre")
        rule.onNodeWithTag("field_error").assertTextEquals("Escribe tu nombre")
    }

    @Test
    fun noError_showsNoErrorText() {
        show()
        rule.onNodeWithTag("field_error").assertDoesNotExist()
    }

    @Test
    fun disabledField_isNotEnabled() {
        show(enabled = false)
        rule.onNodeWithTag("field").assertIsNotEnabled()
    }
}
