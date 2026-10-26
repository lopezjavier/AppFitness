package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [AppTextField] v2: trailing slot, keyboard action, autofocus and error semantics. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppTextFieldV2Test {
    @get:Rule
    val rule = createComposeRule()

    private fun show(errorText: String? = null) {
        rule.setContent {
            AppTheme {
                AppTextField(value = "", onValueChange = {
                }, label = "Nombre", testTagPrefix = "field", errorText = errorText)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun errorText_isAnnouncedAsAnError() {
        show(errorText = "Escribe tu nombre")
        rule.onNodeWithTag("field_error").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }

    @Test
    fun trailingSlot_isRenderedAndClickable() {
        var clicks = 0
        rule.setContent {
            AppTheme {
                AppTextField(
                    value = "",
                    onValueChange = {},
                    label = "Nombre",
                    testTagPrefix = "field",
                    trailing = { Box(Modifier.size(48.dp).clickable { clicks++ }.testTag("field_trailing")) },
                )
            }
        }
        rule.onNodeWithTag("field_trailing").assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun imeAction_callsOnImeActionWithTheConfiguredAction() {
        var calls = 0
        rule.setContent {
            AppTheme {
                AppTextField(
                    value = "",
                    onValueChange = {},
                    label = "Buscar",
                    testTagPrefix = "field",
                    imeAction = ImeAction.Search,
                    onImeAction = { calls++ },
                )
            }
        }
        rule.onNodeWithTag("field").assert(
            SemanticsMatcher("ime action is Search") {
                it.config.getOrNull(SemanticsProperties.ImeAction) == ImeAction.Search
            },
        )
        rule.onNodeWithTag("field").performImeAction()
        assertEquals(1, calls)
    }

    @Test
    fun doneWithoutOnImeAction_stillCallsOnDone() {
        var done = 0
        rule.setContent {
            AppTheme {
                AppTextField(value = "", onValueChange = {}, label = "N", testTagPrefix = "field", onDone = { done++ })
            }
        }
        rule.onNodeWithTag("field").performImeAction()
        assertEquals(1, done)
    }

    @Test
    fun nextWithoutOnImeAction_movesFocusToTheNextField() {
        rule.setContent {
            AppTheme {
                Column {
                    AppTextField("", {
                    }, "Primero", testTagPrefix = "first", imeAction = ImeAction.Next, autofocus = true)
                    AppTextField("", {}, "Segundo", testTagPrefix = "second")
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("first").assertIsFocused()
        rule.onNodeWithTag("first").performImeAction()
        rule.waitForIdle()
        rule.onNodeWithTag("second").assertIsFocused()
    }

    @Test
    fun autofocus_focusesTheFieldOnOpen() {
        rule.setContent {
            AppTheme {
                AppTextField(value = "", onValueChange = {}, label = "N", testTagPrefix = "field", autofocus = true)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("field").assertIsFocused()
    }

    @Test
    fun withoutAutofocus_theFieldIsNotFocused() {
        show()
        rule.onNodeWithTag("field").assertIsNotFocused()
    }
}
