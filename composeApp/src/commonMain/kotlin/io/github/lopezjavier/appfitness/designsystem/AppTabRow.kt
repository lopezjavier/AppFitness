@file:Suppress("MatchingDeclarationName") // The file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of [AppTabRow]. */
object AppTabRowDefaults {
    val TabMinHeight: Dp = Spacing.minTouchTarget
    val IndicatorHeight: Dp = 2.dp

    /** Labels wrap to this many lines (large font scales) instead of being clipped. */
    const val LABEL_MAX_LINES = 2
}

/**
 * Equal-weight tab bar for the sections of one screen (for example Resumen / Historial / Cómo hacerlo). The
 * selected tab has a lime label and indicator, the others `textSecondary`; each tab is at least 48 dp tall and its
 * label wraps to two lines rather than being clipped. Stateless: the screen owns the selected index. Each tab
 * exposes `selected` with [Role.Tab] and is tagged with its [AppOption.testTag]; tapping the active tab does not
 * call [onSelect].
 */
@Composable
fun AppTabRow(options: List<AppOption>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleSelection.requireValid(selectedIndex, options.size)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
        options.forEachIndexed { index, option ->
            Tab(option, index == selectedIndex) { SingleSelection.onTap(selectedIndex, index)?.let(onSelect) }
        }
    }
}

@Composable
private fun RowScope.Tab(option: AppOption, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val base = AppTheme.typography.titleMedium
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = AppTabRowDefaults.TabMinHeight)
            .testTag(option.testTag)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = option.label,
            style = if (selected) base.copy(fontWeight = FontWeight.W700) else base,
            color = if (selected) colors.neonLime else colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = AppTabRowDefaults.LABEL_MAX_LINES,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.xs, vertical = Spacing.sm),
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
