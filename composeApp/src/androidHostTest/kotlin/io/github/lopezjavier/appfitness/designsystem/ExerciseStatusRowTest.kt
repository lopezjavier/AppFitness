package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
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
                        ExerciseStatusTextAction("Ya no me molesta", onClick = {
                            trailing++
                        }, modifier = Modifier.testTag("act"))
                    },
                )
            }
        }

        rule.onNodeWithTag("act").performClick()

        assertEquals(1, trailing)
        assertFalse(rule.onNodeWithTag("row").fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
    }

    @Test
    fun textAction_is48dpTall_andClickedOnce() {
        var clicks = 0
        rule.setContent {
            AppTheme {
                ExerciseStatusTextAction("Ya no me molesta", onClick = { clicks++ }, modifier = Modifier.testTag("act"))
            }
        }

        rule.onNodeWithTag("act").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun textAction_disabled_isNotClickable() {
        var clicks = 0
        rule.setContent {
            AppTheme {
                ExerciseStatusTextAction(
                    "Ya no me molesta",
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("act"),
                    enabled = false,
                )
            }
        }

        rule.onNodeWithTag("act").assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun longLevelText_wrapsAtWordBoundaries_ratherThanBreakingTheAction() {
        rule.setContent {
            AppTheme {
                ExerciseStatusRow(
                    text = "Dolor agudo que limita el movimiento",
                    marker = {},
                    testTag = "row",
                    textTestTag = "level",
                    trailing = {
                        ExerciseStatusTextAction("Ya no me molesta", onClick = {}, modifier = Modifier.testTag("act"))
                    },
                )
            }
        }

        val action = rule.onNodeWithTag("act").getUnclippedBoundsInRoot()
        val level = rule.onNodeWithTag("level").getUnclippedBoundsInRoot()
        // The action keeps a single line (about one touch target tall); the level text takes at most 3 lines.
        assertTrue("action wrapped: ${action.height}", action.height < 64.dp)
        assertTrue("level too tall: ${level.height}", level.height <= 72.dp)
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
