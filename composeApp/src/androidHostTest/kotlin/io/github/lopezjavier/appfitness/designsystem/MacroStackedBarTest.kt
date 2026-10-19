package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-08 "Initial display": the stacked bar mirrors the split and speaks it as one node. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class MacroStackedBarTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(protein: Int, carbs: Int, fat: Int) {
        rule.setContent { AppTheme { MacroStackedBar(protein, carbs, fat, testTag = "bar") } }
        rule.waitForIdle()
    }

    private fun width(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().width

    @Test
    fun segmentWidths_areProportionalToThePercents() {
        show(35, 42, 23)
        val total = width("bar")
        assertEquals(total.value * 0.35f, width("bar_protein").value, 1f)
        assertEquals(total.value * 0.42f, width("bar_carbs").value, 1f)
        assertEquals(total.value * 0.23f, width("bar_fat").value, 1f)
    }

    @Test
    fun stateDescription_speaksTheSplit() {
        show(35, 42, 23)
        rule.onNodeWithTag("bar").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Proteínas 35 %, carbohidratos 42 %, grasas 23 %",
            ),
        )
    }

    @Test
    fun height_isTheToken() {
        show(30, 45, 25)
        rule.onNodeWithTag("bar").assertHeightIsEqualTo(12.dp)
    }
}
