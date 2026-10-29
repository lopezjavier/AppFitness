package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [ExerciseStatusRow] (R-51): tappable only on request, independent trailing action, supporting line. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ExerciseStatusRowTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rowWithoutOnClick_isNotAButton_andHasNoClickAction() {
        rule.setContent { AppTheme { ExerciseStatusRow("Otro · Pausa larga", marker = {}, testTag = "row") } }

        val config = rule.onNodeWithTag("row").fetchSemanticsNode().config
        assertFalse(config.contains(SemanticsActions.OnClick))
        assertFalse(config.contains(SemanticsProperties.Role))
    }

    @Test
    fun clickableRow_isAButtonOf48dp_andInvokesTheCallback() {
        var clicks = 0
        rule.setContent {
            AppTheme {
                ExerciseStatusRow("Sugerencia", marker = {
                }, testTag = "row", onClick = { clicks++ }, onClickLabel = "Ver por qué")
            }
        }

        val node = rule.onNodeWithTag("row")
        node.assertHeightIsAtLeast(48.dp).assertHasClickAction().performClick()

        assertEquals(Role.Button, node.fetchSemanticsNode().config[SemanticsProperties.Role])
        assertEquals(1, clicks)
    }

    @Test
    fun trailingAction_isClickableOnItsOwn_andDoesNotTriggerTheRow() {
        var trailing = 0
        rule.setContent {
            AppTheme {
                ExerciseStatusRow(
                    text = "Tensión o fatiga",
                    marker = {},
                    testTag = "row",
                    trailing = {
                        GhostButton("Ya no me molesta", onClick = { trailing++ }, modifier = Modifier.testTag("act"))
                    },
                )
            }
        }

        rule.onNodeWithTag("act").performClick()

        assertEquals(1, trailing)
        assertFalse(rule.onNodeWithTag("row").fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
    }

    @Test
    fun supportingLine_isShownWithItsTag() {
        rule.setContent {
            AppTheme {
                ExerciseStatusRow("Nivel", marker = {
                }, trailing = {}, supporting = "Sin conexión", supportingTestTag = "hint")
            }
        }

        rule.onNodeWithTag("hint").assertIsDisplayed().assertTextContains("Sin conexión")
    }

    @Test
    fun contentDescription_isExposedOnTheRow() {
        rule.setContent {
            AppTheme {
                ExerciseStatusRow("Tensión", marker = {}, testTag = "row", contentDescription = "Molestia: Tensión")
            }
        }

        val cds = rule.onNodeWithTag("row").fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertTrue(cds.contains("Molestia: Tensión"))
    }
}
