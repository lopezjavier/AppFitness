package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
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
class ViewModeSelectorTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersTheThreeModesInSpanish_withTagsFromThePrefix() {
        rule.setContent { AppTheme { ViewModeSelector(0, {}, testTagPrefix = "diary_view") } }

        rule.onNodeWithTag("diary_view_detail").assertTextContains("Detalle")
        rule.onNodeWithTag("diary_view_list").assertTextContains("Lista")
        rule.onNodeWithTag("diary_view_summary").assertTextContains("Resumen")
    }

    @Test
    fun onlyTheSelectedModeIsSelected_eachTarget48dp() {
        rule.setContent { AppTheme { ViewModeSelector(1, {}, testTagPrefix = "diary_view") } }

        rule.onNodeWithTag("diary_view_list").assertIsSelected().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("diary_view_detail").assertIsNotSelected()
        rule.onNodeWithTag("diary_view_summary").assertIsNotSelected()
    }

    @Test
    fun tappingAnotherMode_reportsItsIndex() {
        var tapped = -1
        rule.setContent { AppTheme { ViewModeSelector(0, { tapped = it }, testTagPrefix = "diary_view") } }

        rule.onNodeWithTag("diary_view_summary").performClick()

        assertEquals(2, tapped)
    }
}
