package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class SegmentedSelectorDisabledIndicesTest {
    @get:Rule
    val rule = createComposeRule()

    private val options = listOf(AppOption("Priorizar", "seg_first"), AppOption("Mantener", "seg_second"))

    @Test
    fun disabledIndex_isNotTappableWhileTheOtherSegmentIs() {
        val tapped = mutableListOf<Int>()
        rule.setContent {
            AppTheme {
                SegmentedSelector(options, selectedIndex = null, onSelect = {
                    tapped += it
                }, disabledIndices = setOf(0))
            }
        }

        rule.onNodeWithTag("seg_first").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("seg_second").assertIsEnabled().performClick()

        assertEquals(listOf(1), tapped)
    }
}
