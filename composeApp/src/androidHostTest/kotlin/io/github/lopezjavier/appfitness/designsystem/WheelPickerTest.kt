package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TAG = "wheel"

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per behaviour of the wheel.
class WheelPickerTest {
    @get:Rule
    val rule = createComposeRule()

    private var value by mutableIntStateOf(178)
    private var range by mutableStateOf(100..250)

    private fun show(initial: Int = 178, initialRange: IntRange = 100..250) {
        value = initial
        range = initialRange
        rule.setContent {
            AppTheme {
                WheelPicker(
                    range = range,
                    value = value,
                    onValueChange = { value = it },
                    label = { it.toString() },
                    contentDescription = "Altura",
                    stateDescription = { "$it cm" },
                    testTagPrefix = TAG,
                )
            }
        }
    }

    private fun setProgress(target: Float) {
        rule.onNodeWithTag(TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(target) }
        rule.waitForIdle()
    }

    @Test
    fun setProgressClamps_aboveTheRangeGoesToTheMaximum() {
        show()
        setProgress(260f)
        assertEquals(250, value)
    }

    @Test
    fun setProgressClamps_belowTheRangeGoesToTheMinimum() {
        show(initial = 70, initialRange = 20..400)
        setProgress(15f)
        assertEquals(20, value)
    }

    @Test
    fun setProgress_insideTheRangeSelectsThatValue() {
        show()
        setProgress(180f)
        assertEquals(180, value)
    }

    @Test
    fun semantics_nameTheFieldAndSpeakTheValueWithItsUnit() {
        show()
        rule.onNodeWithTag(TAG).assertContentDescriptionEquals("Altura")
        rule.onNodeWithTag(TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "178 cm"))
        rule.onNodeWithTag(TAG).assert(
            SemanticsMatcher("range 100..250 at 178") {
                val info = it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
                info == ProgressBarRangeInfo(178f, 100f..250f, 149)
            },
        )
    }

    @Test
    fun items_neverExistOutsideTheRange() {
        show()
        rule.onNodeWithTag("${TAG}_item_251").assertDoesNotExist()
        rule.onNodeWithTag("${TAG}_item_99").assertDoesNotExist()
        rule.onNodeWithTag("${TAG}_item_178").assertExists()
    }

    @Test
    fun items_areAtLeast48dpTall() {
        show()
        rule.onNodeWithTag("${TAG}_item_178").assertHeightIsAtLeast(48.dp)
        assertTrue(WheelPickerDefaults.ItemHeight >= 48.dp)
    }

    @Test
    fun tappingANeighbourItem_selectsIt() {
        show()
        rule.onNodeWithTag("${TAG}_item_179").performClick()
        rule.waitForIdle()
        assertEquals(179, value)
        rule.onNodeWithTag("${TAG}_item_179").assertIsDisplayed()
    }

    @Test
    fun anExternalValueChange_bringsTheItemIntoView() {
        show()
        rule.runOnIdle { value = 200 }
        rule.waitForIdle()
        rule.onNodeWithTag("${TAG}_item_200").assertIsDisplayed()
    }

    @Test
    fun swipingTheWheel_reportsAnotherValueOnceItSettles() {
        show()
        rule.onNodeWithTag(TAG).performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertTrue("value moved up from 178 but is $value", value > 178)
    }

    @Test
    fun aShrinkingRange_keepsTheWheelOnAValidItem() {
        show(initial = 31, initialRange = 1..31)
        rule.runOnIdle {
            range = 1..28
            value = 28
        }
        rule.waitForIdle()
        rule.onNodeWithTag("${TAG}_item_29").assertDoesNotExist()
        rule.onNodeWithTag("${TAG}_item_28").assertIsDisplayed()
    }
}
