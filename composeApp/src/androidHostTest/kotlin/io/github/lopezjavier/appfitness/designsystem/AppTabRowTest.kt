package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [AppTabRow]: selected semantics, 48 dp tabs, callbacks and large font scales. */
@Suppress("TooManyFunctions") // One scenario per test.
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

    private val fiveOptions = listOf(
        AppOption("Resumen", "five_summary"),
        AppOption("Similares", "five_similar"),
        AppOption("Historial", "five_history"),
        AppOption("Cómo hacerlo", "five_how_to"),
        AppOption("Preguntar", "five_chat"),
    )

    // 60 dp gives each of five tabs 12 dp, less than the 16 dp of label padding, so the row always has to scroll.
    private fun showFive(fontScale: Float, selected: Int = 0) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                AppTheme { AppTabRow(fiveOptions, selected, {}, Modifier.width(60.dp)) }
            }
        }
        rule.waitForIdle()
    }

    private fun assertScrollingTabsKeepLabelsOnOneLine() {
        val tabs = fiveOptions.map { rule.onNodeWithTag(it.testTag).getUnclippedBoundsInRoot() }
        fiveOptions.forEachIndexed { index, option ->
            val label = rule.onNodeWithText(option.label, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(tabs[index].width >= 72.dp)
            assertTrue(label.left >= tabs[index].left && label.right <= tabs[index].right)
            assertTrue(label.height < 28.dp)
        }
        assertTrue(tabs.last().right > 60.dp)
    }

    @Test
    fun fiveTabs_at100Dp_scrollWithWideTabs_andNeverBreakAWord() {
        showFive(fontScale = 1f)

        assertScrollingTabsKeepLabelsOnOneLine()
    }

    @Test
    fun fiveTabs_at130PercentFont_scrollWithWideTabs_andNeverBreakAWord() {
        showFive(fontScale = 1.3f)

        assertScrollingTabsKeepLabelsOnOneLine()
    }

    // Robolectric measures text at ~0 width, so every longest word "fits" and the row stays equal-weight here;
    // the scrolling branch is covered at 100 dp above and the real word-fit decision is checked on a device.
    private fun assertEqualWeightFiveTabsAt328Dp(fontScale: Float) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                AppTheme { AppTabRow(fiveOptions, 0, {}, Modifier.width(328.dp)) }
            }
        }
        rule.waitForIdle()

        val tabs = fiveOptions.map { rule.onNodeWithTag(it.testTag).assertIsDisplayed().getUnclippedBoundsInRoot() }
        // Pixel rounding of equal weights may differ by under 1 dp.
        assertTrue(tabs.maxOf { it.width.value } - tabs.minOf { it.width.value } < 1f)
        assertTrue(tabs.last().right <= 328.dp)
        assertTrue(tabs.all { it.height >= 48.dp })
    }

    @Test
    fun fiveTabs_at328Dp_areEqualWeightInTheTestEnvironment() {
        assertEqualWeightFiveTabsAt328Dp(fontScale = 1f)
    }

    @Test
    fun fiveTabs_at328DpAnd130PercentFont_areEqualWeightInTheTestEnvironment() {
        assertEqualWeightFiveTabsAt328Dp(fontScale = 1.3f)
    }

    @Test
    fun fiveTabs_keepTheSelectedTabVisible_whenItIsTheLastOne() {
        showFive(fontScale = 1f, selected = 4)

        rule.onNodeWithTag("five_chat").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun threeTabs_at360Dp_shareTheWidthEquallyWithoutScrolling() {
        show()

        val tabs = options.map { rule.onNodeWithTag(it.testTag).assertIsDisplayed().getUnclippedBoundsInRoot() }
        assertEquals(1, tabs.map { it.width }.distinct().size)
        assertEquals(360.dp, tabs.last().right)
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
