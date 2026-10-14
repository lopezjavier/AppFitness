package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelected
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
class SegmentedSelectorNullSelectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val options = listOf(AppOption("Hombre", "seg_male"), AppOption("Mujer", "seg_female"))

    @Test
    fun nullSelection_rendersNoSelectedSegment_eachStill48dp() {
        rule.setContent { AppTheme { SegmentedSelector(options, selectedIndex = null, onSelect = {}) } }

        rule.onAllNodes(isSelected()).assertCountEquals(0)
        rule.onNodeWithTag("seg_male").assertIsNotSelected().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("seg_female").assertIsNotSelected().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun nullSelection_tappingASegmentReportsItsIndex() {
        var tapped = -1
        rule.setContent { AppTheme { SegmentedSelector(options, selectedIndex = null, onSelect = { tapped = it }) } }

        rule.onNodeWithTag("seg_female").performClick()

        assertEquals(1, tapped)
    }

    @Test
    fun withASelection_exactlyThatSegmentIsSelected() {
        rule.setContent { AppTheme { SegmentedSelector(options, selectedIndex = 0, onSelect = {}) } }

        rule.onNodeWithTag("seg_male").assertIsSelected()
        rule.onNodeWithTag("seg_female").assertIsNotSelected()
    }
}
