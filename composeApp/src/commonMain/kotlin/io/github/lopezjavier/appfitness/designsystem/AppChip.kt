package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Colours of one [AppChip] state. */
@Immutable
data class AppChipColors(val container: Color, val content: Color, val border: Color)

/** Sizes, colours and label style of [AppChip]. */
object AppChipDefaults {
    /** Visual height; the touch target grows to [MinTouchTarget]. */
    val Height: Dp = 32.dp

    /** Size of the interactive area (the Material minimum of 48 dp). */
    val MinTouchTarget: Dp = 48.dp
    val HorizontalPadding: Dp = Spacing.md
    val Shape = AppShapes.full

    /** Same width in both states, so selecting a chip never shifts the layout. */
    val BorderWidth: Dp = AppBorder.thin

    /** The selected chip differs in fill, text colour and weight, not only in border. */
    fun colors(selected: Boolean, appColors: AppColors): AppChipColors = if (selected) {
        AppChipColors(appColors.surfaceOverlay, appColors.athleticCyan, appColors.athleticCyan)
    } else {
        AppChipColors(appColors.surfaceRaised, appColors.textSecondary, appColors.borderSubtle)
    }

    /** Selected label is bold (W700): a non-colour cue. */
    fun labelStyle(base: TextStyle, selected: Boolean): TextStyle =
        if (selected) base.copy(fontWeight = FontWeight.W700) else base
}

/**
 * Pill chip for one choice of a single-select group (see [FilterChipRow]). It is exposed as a
 * radio button, so a screen reader announces the selection. The caller's [modifier] lands on the
 * selectable node.
 */
@Composable
fun AppChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppChipDefaults.colors(selected, AppTheme.colors)
    Box(
        modifier =
        modifier
            .sizeIn(minWidth = AppChipDefaults.MinTouchTarget, minHeight = AppChipDefaults.MinTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
            Modifier
                .height(AppChipDefaults.Height)
                .clip(AppChipDefaults.Shape)
                .background(colors.container)
                .border(AppChipDefaults.BorderWidth, colors.border, AppChipDefaults.Shape)
                .padding(horizontal = AppChipDefaults.HorizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = AppChipDefaults.labelStyle(AppTheme.typography.bodyMedium, selected),
                color = colors.content,
                maxLines = 1,
            )
        }
    }
}

/**
 * Horizontally scrollable group of single-select chips. [selectedIndex] is null (nothing selected) or a valid index of
 * [options]; tapping the active chip does not call [onSelect]. Edge padding is the caller's concern
 * (apply it to the row from the call site).
 */
@Composable
fun FilterChipRow(
    options: List<AppOption>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleSelection.requireValid(selectedIndex, options.size)
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            AppChip(
                label = option.label,
                selected = index == selectedIndex,
                onClick = { SingleSelection.onTap(selectedIndex, index)?.let(onSelect) },
                modifier =
                Modifier
                    .testTag(option.testTag)
                    .semantics { option.contentDescription?.let { contentDescription = it } },
            )
        }
    }
}

@Preview
@Composable
private fun FilterChipRowPreview() {
    AppTheme {
        FilterChipRow(
            options = listOf("1S", "1M", "3M", "1A").map { AppOption(it, "chip_$it") },
            selectedIndex = 1,
            onSelect = {},
            modifier = Modifier.padding(Spacing.screenMargin),
        )
    }
}
