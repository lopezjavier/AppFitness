package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class RpePickerSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val reported = mutableListOf<Double?>()
    private var dismissed = 0

    private fun show(current: Double? = null) {
        rule.setContent {
            AppTheme {
                RpePickerSheet(current, 2, onDone = { reported += it }, onDismiss = { dismissed++ })
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun showsNineOptions_eachAtLeast48DpTall() {
        show()

        RpePickerDefaults.Options.forEach {
            rule.onNodeWithTag(RpePickerDefaults.optionTag(it)).assertHeightIsAtLeast(48.dp)
        }
        assertEquals(9, RpePickerDefaults.Options.size)
        assertEquals("rpe_picker_option_7_5", RpePickerDefaults.optionTag(7.5))
        assertEquals("rpe_picker_option_10", RpePickerDefaults.optionTag(10.0))
    }

    @Test
    fun choosingAnOptionAndGuardar_reportsIt() {
        show()

        rule.onNodeWithTag("rpe_picker_option_7_5").performClick()
        rule.onNodeWithTag("rpe_picker_option_7_5").assertIsSelected()
        rule.onNodeWithTag("rpe_picker_done").performClick()

        assertEquals(listOf<Double?>(7.5), reported)
    }

    @Test
    fun guardarIsDisabled_untilTheStagedValueDiffers() {
        show(current = 8.0)

        rule.onNodeWithTag("rpe_picker_done").assertIsNotEnabled()
        rule.onNodeWithTag("rpe_picker_option_8_5").performClick()
        rule.onNodeWithTag("rpe_picker_done").assertIsEnabled()
    }

    @Test
    fun quitarRpe_onlyStagesTheClear_andGuardarReportsNull() {
        show(current = 8.0)

        rule.onNodeWithTag("rpe_picker_clear").performClick()
        assertTrue(reported.isEmpty())
        rule.onNodeWithTag("rpe_picker_clear").assertDoesNotExist()
        rule.onNodeWithTag("rpe_picker_done").performClick()

        assertEquals(listOf<Double?>(null), reported)
    }

    @Test
    fun quitarRpe_isHiddenWhenNothingIsStaged() {
        show()

        rule.onNodeWithTag("rpe_picker_clear").assertDoesNotExist()
    }

    @Test
    fun closing_reportsNothingToSave() {
        show()

        rule.onNodeWithTag("rpe_picker_option_9").performClick()
        rule.onNodeWithTag("rpe_picker_close").performClick()

        assertTrue(reported.isEmpty())
        assertEquals(1, dismissed)
    }

    @Test
    fun stagedChoice_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                var open by rememberSaveable { mutableStateOf(true) }
                if (open) {
                    RpePickerSheet(null, 1, onDone = {
                        reported += it
                        open = false
                    }, onDismiss = { open = false })
                }
            }
        }
        rule.onNodeWithTag("rpe_picker_option_7_5").performClick()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag("rpe_picker_option_7_5").assertIsSelected()
        rule.onNodeWithTag("rpe_picker_done").performClick()

        assertEquals(listOf<Double?>(7.5), reported)
    }
}
