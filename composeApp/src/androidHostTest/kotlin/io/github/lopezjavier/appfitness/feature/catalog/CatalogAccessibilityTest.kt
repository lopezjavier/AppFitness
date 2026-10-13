package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MIN_TOUCH_DP = 48f
private const val TOLERANCE_DP = 0.5f

private val ExpectedSections =
    listOf(
        "cards", "buttons", "chips", "segmented", "ring", "macro_bars", "macro_sliders", "metric_tiles",
        "trend_pills", "number_input", "set_checkbox", "set_rows", "rest_timer", "state_messages", "top_bar",
        "navigation", "skeleton", "wip", "theme",
    ).map { "catalog_section_$it" }

/**
 * US-03 "Accessibility of interactive components": every interactive node of the component catalog has a
 * touch target of at least 48 x 48 dp and an accessible label. The catalog is scrolled item by item, because
 * a lazy list only composes what is on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class CatalogAccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private val interactive: SemanticsMatcher =
        hasClickAction() or isToggleable() or isSelectable() or
            SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress) or
            SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

    /** What the checks need from one interactive node, read while the node is attached. */
    private data class Probe(val key: String, val widthDp: Float, val heightDp: Float, val labelled: Boolean)

    /** Every interactive node seen while scrolling the whole catalog, keyed by tag (or bounds) to deduplicate. */
    private fun collectInteractiveNodes(): Map<String, Probe> {
        // The skeleton shimmer is an infinite animation: drive the clock by hand instead of waiting for idle.
        rule.mainClock.autoAdvance = false
        rule.setContent { AppTheme { ComponentCatalogScreen(Modifier.fillMaxSize()) } }
        val density = rule.density.density
        val found = linkedMapOf<String, Probe>()
        val list = rule.onNodeWithTag("catalog_screen")
        repeat(CATALOG_ITEM_COUNT) { index ->
            list.performScrollToIndex(index)
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodes(interactive).fetchSemanticsNodes().forEach { node ->
                val key = node.config.getOrNull(SemanticsProperties.TestTag) ?: "bounds_${node.boundsInRoot}"
                val bounds = node.touchBoundsInRoot
                // Items composed ahead of the viewport are not placed yet (0 x 0): seen again once on screen.
                if (bounds.isEmpty) return@forEach
                found.putIfAbsent(key, Probe(key, bounds.width / density, bounds.height / density, node.hasLabel()))
            }
        }
        return found
    }

    @Test
    fun everyInteractiveNode_touchTargetAtLeast48dp() {
        val probes = collectInteractiveNodes()
        // Guards against a vacuous pass: representative nodes of every kind must have been measured.
        val expectedTags =
            listOf(
                "catalog_button_primary_enabled", "catalog_chip_1w", "catalog_segment_deficit",
                "catalog_macro_slider_protein", "catalog_number_input_weight", "catalog_set_checkbox_unchecked",
                "catalog_set_row_3_check", "catalog_rest_timer_start", "catalog_empty_state_action",
                "catalog_error_state_action", "catalog_topbar_tabs_avatar", "catalog_topbar_full_back",
                "catalog_nav_tab_diary", "catalog_nav_tab_progress",
            )
        assertTrue(probes.keys.containsAll(expectedTags), "not measured: ${expectedTags - probes.keys}")
        val tooSmall =
            probes.values
                .filter { it.widthDp < MIN_TOUCH_DP - TOLERANCE_DP || it.heightDp < MIN_TOUCH_DP - TOLERANCE_DP }
                .map { "${it.key}: ${it.widthDp} x ${it.heightDp} dp" }
        assertTrue(tooSmall.isEmpty(), "touch target below 48 dp: $tooSmall")
    }

    @Test
    fun everyInteractiveNode_hasAccessibleLabel() {
        val unlabelled = collectInteractiveNodes().values.filter { !it.labelled }.map { it.key }
        assertTrue(unlabelled.isEmpty(), "interactive nodes without an accessible label: $unlabelled")
    }

    @Test
    fun catalog_containsEverySection6Component() {
        rule.mainClock.autoAdvance = false
        rule.setContent { AppTheme { ComponentCatalogScreen(Modifier.fillMaxSize()) } }
        val list = rule.onNodeWithTag("catalog_screen")
        val seen = linkedSetOf<String>()
        repeat(CATALOG_ITEM_COUNT) { index ->
            list.performScrollToIndex(index)
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodes(
                SemanticsMatcher("catalog section heading") { node ->
                    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("catalog_section_") == true
                },
            ).fetchSemanticsNodes().forEach { node ->
                seen += node.config[SemanticsProperties.TestTag]
            }
        }
        assertEquals(ExpectedSections.toSet(), seen)
    }

    private fun SemanticsNode.hasLabel(): Boolean {
        val description = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        val text = config.getOrNull(SemanticsProperties.Text).orEmpty()
        val editable = config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        return description.any { it.isNotBlank() } || text.any { it.text.isNotBlank() } || editable.isNotBlank()
    }

    private companion object {
        /** One lazy item per section: 4 basics + 5 data-viz + 4 workout inputs + 1 + 4 shell + 1 theme. */
        const val CATALOG_ITEM_COUNT = 19
    }
}
