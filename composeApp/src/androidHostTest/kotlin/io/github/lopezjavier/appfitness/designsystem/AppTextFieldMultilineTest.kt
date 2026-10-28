package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [AppTextField] v4: a multi-line box insets its text from the top and bottom border; single-line is unchanged. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppTextFieldMultilineTest {
    @get:Rule
    val rule = createComposeRule()

    private val inset: Dp = AppTextFieldDefaults.MultiLineVerticalPadding

    /** Shows the field next to a bare [BasicTextField] of the same style and lines: the text-only reference height. */
    private fun show(singleLine: Boolean, minLines: Int, value: String = "") {
        rule.setContent {
            AppTheme {
                Column {
                    AppTextField(
                        value = value,
                        onValueChange = {},
                        label = "Notas",
                        testTagPrefix = "field",
                        singleLine = singleLine,
                        minLines = minLines,
                    )
                    BasicTextField(
                        value = value,
                        onValueChange = {},
                        modifier = Modifier.testTag("bare"),
                        textStyle = AppTheme.typography.bodyLarge,
                        singleLine = singleLine,
                        minLines = minLines,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun heightOf(tag: String): Dp = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().height

    @Test
    fun multiLine_heightIsTheTextPlusTheTopAndBottomInset() {
        show(singleLine = false, minLines = 3)

        rule.onNodeWithTag("field").assertHeightIsEqualTo(heightOf("bare") + inset * 2)
    }

    @Test
    fun multiLine_withTextKeepsTheSameInset() {
        show(singleLine = false, minLines = 3, value = "rodilla izquierda")

        rule.onNodeWithTag("field").assertHeightIsEqualTo(heightOf("bare") + inset * 2)
    }

    @Test
    fun multiLine_honoursTheMinimumLines() {
        show(singleLine = false, minLines = 4)

        rule.onNodeWithTag("field").assertHeightIsEqualTo(heightOf("bare") + inset * 2)
        assertTrue(heightOf("field") > AppTextFieldDefaults.MinHeight * 2)
    }

    @Test
    fun singleLine_keepsTheMinimumHeight() {
        show(singleLine = true, minLines = 1)

        rule.onNodeWithTag("field").assertHeightIsEqualTo(AppTextFieldDefaults.MinHeight)
    }
}
