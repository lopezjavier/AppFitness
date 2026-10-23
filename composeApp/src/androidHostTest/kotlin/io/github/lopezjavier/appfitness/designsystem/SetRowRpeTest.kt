package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The optional RPE cell of a [SetRow] and its header column. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class SetRowRpeTest {
    @get:Rule
    val rule = createComposeRule()

    private val empty = SetRowValues(1, null, "", "", completed = false)

    @Test
    fun withoutHandler_noRpeCellIsRendered() {
        rule.setContent { AppTheme { SetRow(empty, {}, {}, {}, testTagPrefix = "row") } }

        rule.onNodeWithTag("row_rpe").assertDoesNotExist()
    }

    @Test
    fun withHandler_cellIs48Dp_showsTheEmptyMarker_andReportsTheTap() {
        var taps = 0
        rule.setContent {
            AppTheme {
                SetRow(empty, {
                }, {}, {}, testTagPrefix = "row", onRpeClick = { taps++ }, rpeClickLabel = "RPE de la serie 1")
            }
        }

        rule.onNodeWithTag("row_rpe").assertHasClickAction().assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, taps)
        rule.onNodeWithTag("row_rpe").assertTextEquals("—")
    }

    @Test
    fun withHandler_showsTheLoggedValue() {
        rule.setContent {
            AppTheme {
                SetRow(empty.copy(rpe = "7,5"), {}, {}, {}, testTagPrefix = "row", onRpeClick = {})
            }
        }

        rule.onNodeWithTag("row_rpe").assertTextEquals("7,5")
    }

    @Test
    fun aCompletedRow_keepsTheRpeCellClickable() {
        rule.setContent {
            AppTheme {
                SetRow(empty.copy(completed = true), {}, {}, {}, testTagPrefix = "row", onRpeClick = {})
            }
        }

        rule.onNodeWithTag("row_rpe").assertHasClickAction()
    }

    @Test
    fun withoutRpe_theCellHasNoClickAction() {
        rule.setContent { AppTheme { SetRow(empty, {}, {}, {}, testTagPrefix = "row") } }

        rule.onNodeWithTag("row_rpe").assertDoesNotExist()
        rule.onNodeWithTag("row_check").assertHasClickAction()
    }

    @Test
    fun header_showsRpeColumnOnlyOnRequest() {
        rule.setContent {
            AppTheme {
                Column {
                    SetTableHeader(Modifier.testTag("plain"))
                    SetTableHeader(Modifier.testTag("withRpe"), showRpe = true)
                }
            }
        }

        rule.onNodeWithTag("plain").assertHasNoClickAction()
        rule.onNode(hasText("RPE"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun at360DpAnd130PercentFont_weightAndRepsStayVisibleAndRpeKeeps48Dp() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2.75f, fontScale = 1.3f)) {
                AppTheme {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
                        SetTableHeader(showRpe = true)
                        SetRow(
                            SetRowValues(1, "85 kg × 8\nRPE 8", "102,25", "123", completed = false, rpe = "7,5"),
                            {},
                            {},
                            {},
                            testTagPrefix = "row",
                            onRpeClick = {},
                        )
                    }
                }
            }
        }

        rule.onNodeWithTag("row_weight").assertIsDisplayed()
        rule.onNodeWithTag("row_reps").assertIsDisplayed()
        rule.onNodeWithTag("row_rpe").assertWidthIsAtLeast(48.dp).assertIsDisplayed()
        // The weighted columns must be wide enough for their text: a 3-digit reps value is not clipped.
        val reps = rule.onNodeWithTag("row_reps").getUnclippedBoundsInRoot()
        assertTrue("reps column too narrow: ${reps.width}", reps.width >= 36.dp)
        val weight = rule.onNodeWithTag("row_weight").getUnclippedBoundsInRoot()
        assertTrue("weight column too narrow: ${weight.width}", weight.width >= 56.dp)
    }
}
