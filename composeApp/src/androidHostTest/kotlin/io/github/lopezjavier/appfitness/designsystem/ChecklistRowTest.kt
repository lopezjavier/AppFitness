package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** T-208: the checklist row (checkbox + title + wrapping reason), one Checkbox node with a readable state. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class ChecklistRowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun stateIs(expected: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)

    @Test
    fun theWholeRow_isOneCheckboxNode_withTheStateAsText() {
        rule.setContent {
            AppTheme {
                ChecklistRow(checked = true, onCheckedChange = {}, title = "Quitar Remo", testTag = "row")
            }
        }

        rule.onNodeWithTag("row")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(stateIs("Marcado"))
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun tappingTheTitle_togglesTheRow() {
        var checked by mutableStateOf(true)
        rule.setContent {
            AppTheme {
                ChecklistRow(checked, { checked = it }, "Quitar Remo", "row", supporting = "Motivo")
            }
        }

        rule.onNodeWithText("Quitar Remo").performClick()
        rule.waitForIdle()

        assertEquals(false, checked)
        rule.onNodeWithTag("row").assert(stateIs("Sin marcar"))
    }

    @Test
    fun theReason_isShownInFullEvenWhenLong() {
        val reason = "Motivo largo ".repeat(12).trim()
        rule.setContent {
            AppTheme { ChecklistRow(true, {}, "Titulo", "row", supporting = reason) }
        }

        rule.onNodeWithText(reason, useUnmergedTree = true).assertExists()
    }

    @Test
    fun disabled_isInert() {
        var calls = 0
        rule.setContent {
            AppTheme { ChecklistRow(true, { calls++ }, "Titulo", "row", enabled = false) }
        }

        rule.onNodeWithTag("row").assertIsNotEnabled().performClick()

        assertEquals(0, calls)
    }
}
