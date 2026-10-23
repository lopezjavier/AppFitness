package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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

    private val reported = mutableListOf<Double>()
    private var dismissed = 0

    private fun show(current: Double? = null) {
        rule.setContent {
            AppTheme {
                RpePickerSheet(current, 2, onSelect = { reported += it }, onDismiss = { dismissed++ })
            }
        }
        rule.waitForIdle()
    }

    private fun top(value: Double) =
        rule.onNodeWithTag(RpePickerDefaults.optionTag(value)).fetchSemanticsNode().boundsInRoot.top

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
    fun optionsAreListedFromTenAtTheTopDownToSix() {
        show()

        assertEquals((10 downTo 6).map { it.toDouble() }, RpePickerDefaults.Options.filter { it % 1.0 == 0.0 })
        assertTrue(top(10.0) < top(8.0))
        assertTrue(top(8.0) < top(6.0))
    }

    @Test
    fun tappingAnOption_reportsItOnce_withoutGuardarOrQuitarNodes() {
        show()

        rule.onNodeWithTag("rpe_picker_option_7_5").performClick()

        assertEquals(listOf(7.5), reported)
        rule.onNodeWithTag("rpe_picker_done").assertDoesNotExist()
        rule.onNodeWithTag("rpe_picker_clear").assertDoesNotExist()
    }

    @Test
    fun theCurrentValueIsSelected_andTheOthersAreNot() {
        show(current = 8.0)

        rule.onNodeWithTag("rpe_picker_option_8").assertIsSelected()
        rule.onNodeWithTag("rpe_picker_option_8_5").assertIsNotSelected()
    }

    @Test
    fun closing_callsOnDismissOnly() {
        show()

        rule.onNodeWithTag("rpe_picker_close").performClick()

        assertTrue(reported.isEmpty())
        assertEquals(1, dismissed)
    }
}
