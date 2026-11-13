@file:Suppress("MatchingDeclarationName") // The file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Sizes of [AppTabRow]. */
object AppTabRowDefaults {
    val TabMinHeight: Dp = Spacing.minTouchTarget
    val IndicatorHeight: Dp = 2.dp

    /** Minimum width of a tab in the scrolling layout. */
    val TabMinWidth: Dp = 72.dp

    /** Horizontal padding around every label. */
    val LabelPadding: Dp = Spacing.md
}

private const val EQUAL_WEIGHT_MAX_LINES = 2

private data class TabBounds(val start: Int, val width: Int)

/**
 * Whether every tab's longest word ([longestWordWidths], px) plus [padding] (px, both sides) fits its equal [share]
 * of the row (px); a word that exactly fills the share still fits.
 */
internal fun tabsFitEqualWeight(longestWordWidths: List<Int>, share: Int, padding: Int): Boolean =
    longestWordWidths.all { it + padding <= share }

/**
 * Tab bar for the sections of one screen (for example Resumen / Historial / Cómo hacerlo). Every label is 12 sp
 * (`bodySmall`, W600; the selected one W700, lime, with a lime indicator; the others `textSecondary`). When the
 * longest word of every label fits its equal share of the width the tabs are equal-weight and a label may wrap between
 * words; otherwise the row scrolls horizontally with content-width tabs of at least 72 dp and one-line labels, and
 * the selected tab is kept visible. Labels never break inside a word. Each tab is at least 48 dp tall. Stateless: the
 * screen owns the selected index. Each tab exposes `selected` with [Role.Tab] and is tagged with its
 * [AppOption.testTag]; tapping the active tab does not call [onSelect]. It measures through `BoxWithConstraints`, so do
 * not place it inside a parent that asks for intrinsic sizes.
 */

@Composable
fun AppTabRow(options: List<AppOption>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleSelection.requireValid(selectedIndex, options.size)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = AppTheme.typography.bodySmall
    val widestStyle = base.copy(fontWeight = FontWeight.W700)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val share = constraints.maxWidth / options.size.coerceAtLeast(1)
        val fits = remember(options, widestStyle, density, share) {
            val padding = with(density) { AppTabRowDefaults.LabelPadding.roundToPx() * 2 }
            tabsFitEqualWeight(
                options.map { option ->
                    option.label.split(' ').maxOf { measurer.measure(it, widestStyle).size.width }
                },
                share,
                padding,
            )
        }
        val onTap: (Int) -> Unit = { index -> SingleSelection.onTap(selectedIndex, index)?.let(onSelect) }
        if (fits) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
                options.forEachIndexed { index, option ->
                    Tab(option, index == selectedIndex, scrolling = false, onClick = {
                        onTap(index)
                    }, modifier = Modifier.weight(1f))
                }
            }
        } else {
            ScrollingTabs(options, selectedIndex, constraints.maxWidth, onTap)
        }
    }
}

@Composable
private fun ScrollingTabs(options: List<AppOption>, selectedIndex: Int, viewportWidth: Int, onTap: (Int) -> Unit) {
    val scroll = rememberScrollState()
    val viewportDp = with(LocalDensity.current) { viewportWidth.toDp() }
    val bounds = remember { mutableStateMapOf<Int, TabBounds>() }
    val selectedBounds = bounds[selectedIndex]
    LaunchedEffect(selectedIndex, selectedBounds, viewportWidth) {
        val tab = selectedBounds ?: return@LaunchedEffect
        val visibleStart = scroll.value
        val visibleEnd = visibleStart + viewportWidth
        when {
            tab.start < visibleStart -> scroll.animateScrollTo(tab.start)
            tab.start + tab.width > visibleEnd -> scroll.animateScrollTo(tab.start + tab.width - viewportWidth)
        }
    }
    Row(Modifier.horizontalScroll(scroll).widthIn(min = viewportDp).height(IntrinsicSize.Min).selectableGroup()) {
        options.forEachIndexed { index, option ->
            val placed = Modifier.onPlaced {
                bounds[index] = TabBounds(it.positionInParent().x.roundToInt(), it.size.width)
            }
            Tab(option, index == selectedIndex, scrolling = true, onClick = { onTap(index) }, modifier = placed)
        }
    }
}

@Composable
private fun Tab(
    option: AppOption,
    selected: Boolean,
    scrolling: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val base = AppTheme.typography.bodySmall
    Box(
        modifier
            .then(if (scrolling) Modifier.widthIn(min = AppTabRowDefaults.TabMinWidth) else Modifier)
            .fillMaxHeight()
            .heightIn(min = AppTabRowDefaults.TabMinHeight)
            .testTag(option.testTag)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = option.label,
            style = if (selected) base.copy(fontWeight = FontWeight.W700) else base.copy(fontWeight = FontWeight.W600),
            color = if (selected) colors.neonLime else colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = if (scrolling) 1 else EQUAL_WEIGHT_MAX_LINES,
            softWrap = !scrolling,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = AppTabRowDefaults.LabelPadding, vertical = Spacing.sm),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(AppTabRowDefaults.IndicatorHeight)
                .background(if (selected) colors.neonLime else colors.divider),
        )
    }
}

@Preview
@Composable
private fun AppTabRowPreview() {
    AppTheme {
        AppTabRow(
            options = listOf("Resumen", "Historial", "Cómo hacerlo").map { AppOption(it, "preview_tab_$it") },
            selectedIndex = 1,
            onSelect = {},
        )
    }
}
