package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [AppTabRow]: selected semantics, 48 dp tabs, callbacks and large font scales. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppTabRowTest {
    @get:Rule
    val rule = createComposeRule()

    private val options = listOf(
        AppOption("Resumen", "tab_summary"),
        AppOption("Historial", "tab_history"),
        AppOption("Cómo hacerlo", "tab_how_to"),
    )

    private fun show(selected: Int = 0, fontScale: Float = 1f, onSelect: (Int) -> Unit = {}) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                AppTheme { AppTabRow(options, selected, onSelect, Modifier) }
            }
        }
    }

    @Test
    fun onlyTheSelectedTab_isSelected_andEveryTabIsATab() {
        show(selected = 1)

        rule.onNodeWithTag("tab_history").assertIsSelected()
        rule.onNodeWithTag("tab_summary").assertIsNotSelected()
        rule.onNodeWithTag("tab_how_to").assertIsNotSelected()
        options.forEach {
            rule.onNodeWithTag(it.testTag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
    }

    @Test
    fun everyTab_isAtLeast48DpTall() {
        show()

        options.forEach { rule.onNodeWithTag(it.testTag).assertHeightIsAtLeast(48.dp) }
    }

    @Test
    fun tappingAnotherTab_reportsItsIndex_andTappingTheActiveOneDoesNot() {
        val taps = mutableListOf<Int>()
        show(selected = 0, onSelect = { taps += it })

        rule.onNodeWithTag("tab_summary").performClick()
        rule.onNodeWithTag("tab_how_to").performClick()

        assertEquals(listOf(2), taps)
    }

    @Test
    fun at130PercentFont_allLabelsStayInsideTheirTabs() {
        show(fontScale = 1.3f)

        val tabs = options.map { rule.onNodeWithTag(it.testTag).getUnclippedBoundsInRoot() }
        assertTrue(tabs.all { it.height >= 48.dp })
        assertEquals(1, tabs.map { it.height }.distinct().size)
        options.forEachIndexed { index, option ->
            val label = rule.onNodeWithText(option.label, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(label.left >= tabs[index].left && label.right <= tabs[index].right)
            assertTrue(label.top >= tabs[index].top && label.bottom <= tabs[index].bottom)
        }
    }
}
