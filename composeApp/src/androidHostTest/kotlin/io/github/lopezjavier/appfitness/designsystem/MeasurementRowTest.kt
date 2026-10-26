package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class MeasurementRowTest {
    @get:Rule
    val rule = createComposeRule()

    private var clicks = 0

    private fun show(
        onClick: (() -> Unit)? = { clicks++ },
        leading: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    ) {
        clicks = 0
        rule.setContent {
            AppTheme {
                MeasurementRow(
                    value = "84,5 cm",
                    supporting = "12 oct",
                    onClick = onClick,
                    clickLabel = "Editar medida",
                    modifier = Modifier.testTag("row"),
                    leading = leading,
                )
            }
        }
    }

    @Test
    fun showsTheValueAndTheDate_asOneMergedNode() {
        show()

        rule.onNodeWithTag("row").assertTextContains("84,5 cm", substring = true)
        rule.onNodeWithTag("row").assertTextContains("12 oct", substring = true)
        assertEquals(1, rule.onAllNodesWithTag("row").fetchSemanticsNodes().size)
    }

    @Test
    fun isAtLeast48dpHigh() {
        show()

        rule.onNodeWithTag("row").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun tappingTheRowCallsOnClick_withTheSpokenClickLabel() {
        show()

        rule.onNodeWithTag("row").assertHasClickAction()
        val label = rule.onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label
        assertEquals("Editar medida", label)
        rule.onNodeWithTag("row").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun withoutOnClick_theRowIsNotClickable() {
        show(onClick = null)

        assertFalse(rule.onNodeWithTag("row").fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
    }

    @Test
    fun showsTheLeadingIcon_whenGiven() {
        show(leading = { Text("ICON", Modifier.testTag("leading")) })

        rule.onNodeWithTag("leading", useUnmergedTree = true).assertIsDisplayed()
    }
}
