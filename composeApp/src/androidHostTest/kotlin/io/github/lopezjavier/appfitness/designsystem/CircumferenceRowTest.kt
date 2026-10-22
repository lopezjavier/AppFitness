package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
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
class CircumferenceRowTest {
    @get:Rule
    val rule = createComposeRule()

    private var clicks = 0

    private fun show() {
        clicks = 0
        rule.setContent {
            AppTheme {
                CircumferenceRow(
                    value = "84,5 cm",
                    whenText = "12 oct · 08:30",
                    clickLabel = "Editar medida",
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("row"),
                )
            }
        }
    }

    @Test
    fun showsTheValueAndTheDate() {
        show()

        rule.onNodeWithTag("row").assertTextContains("84,5 cm", substring = true)
        rule.onNodeWithTag("row").assertTextContains("12 oct · 08:30", substring = true)
    }

    @Test
    fun tappingTheRowEdits() {
        show()

        rule.onNodeWithTag("row").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun theRowHasAnAccessibleActionLabelAndATouchTargetOf48dp() {
        show()

        rule.onNodeWithTag("row").assertHeightIsAtLeast(48.dp)
        val label = rule.onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label
        assertEquals("Editar medida", label)
    }
}
