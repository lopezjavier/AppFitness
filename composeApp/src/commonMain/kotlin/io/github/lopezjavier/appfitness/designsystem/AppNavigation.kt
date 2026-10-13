package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary_filled
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_progress
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.nav_progress
import io.github.lopezjavier.appfitness.composeapp.resources.nav_workout
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val MAX_LABEL_FONT_SCALE = 1.3f

/**
 * One destination of [AppNavigation]. [testTag] goes on the selectable item. [selectedIcon] is the
 * filled glyph shown while active; it defaults to [icon] for glyphs with no filled variant.
 */
@Immutable
data class AppNavigationItem(
    val label: StringResource,
    val icon: DrawableResource,
    val testTag: String,
    val selectedIcon: DrawableResource = icon,
) {
    fun iconFor(selected: Boolean): DrawableResource = if (selected) selectedIcon else icon
}

/** Colours of [AppNavigation]; the [indicator] is transparent because the active tab has no pill (ADR-22). */
@Immutable
data class AppNavigationColors(val selected: Color, val unselected: Color, val indicator: Color)

/** Sizes, colours and label style of [AppNavigation]. */
object AppNavigationDefaults {
    /** Icon glyph size (mockup: 22). */
    val IconSize: Dp = 22.dp

    /** Bar height above the system navigation inset (mockup: 64). */
    val ContentHeight: Dp = 64.dp

    /** Insets the bar pads for; the single source of both its height and its padding. */
    val windowInsets: WindowInsets
        @Composable get() = WindowInsets.navigationBars

    /** Total bar height: [ContentHeight] plus the system navigation inset. */
    @Composable
    fun barHeight(insets: WindowInsets = windowInsets): Dp =
        ContentHeight + insets.asPaddingValues().calculateBottomPadding()

    fun colors(appColors: AppColors): AppNavigationColors = AppNavigationColors(
        selected = appColors.neonLime,
        unselected = appColors.textSecondary,
        indicator = Color.Transparent,
    )

    /** Active label is extra-bold (W800), the rest keep the base weight: the non-colour cue of the active tab. */
    fun labelStyle(base: TextStyle, selected: Boolean): TextStyle =
        if (selected) base.copy(fontWeight = FontWeight.W800) else base
}

/**
 * Bottom navigation bar (phone). The selected item uses `neonLime`, an extra-bold label and its
 * filled icon when it has one, with no pill (ADR-22); the rest use `textSecondary`. Labels are
 * uppercase and their font scale is clamped to 130% so the longest one never clips; each item
 * keeps the original casing as its content description.
 * T-028 adds the NavigationRail variant behind this same API.
 *
 * @param items destinations in display order.
 * @param selectedIndex index into [items] of the active destination.
 * @param onItemSelect called with the index of the tapped destination.
 * @param windowInsets insets the bar pads for; pass `WindowInsets(0)` when it is embedded mid-screen.
 *   The caller tags the bar itself through [modifier] (the shell uses `nav_bar`).
 */
@Composable
fun AppNavigation(
    items: List<AppNavigationItem>,
    selectedIndex: Int,
    onItemSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AppNavigationDefaults.windowInsets,
) {
    val borderColor = AppTheme.colors.borderSubtle
    val barHeight = AppNavigationDefaults.barHeight(windowInsets)
    NavigationBar(
        modifier =
        modifier
            .height(barHeight)
            .drawWithContent {
                drawContent()
                val y = AppBorder.thin.toPx() / 2
                drawLine(borderColor, Offset(0f, y), Offset(size.width, y), strokeWidth = AppBorder.thin.toPx())
            },
        containerColor = AppTheme.colors.surfaceBase,
        tonalElevation = 0.dp,
        windowInsets = windowInsets,
    ) {
        items.forEachIndexed { index, item ->
            AppNavigationBarItem(item, selected = index == selectedIndex, onClick = { onItemSelect(index) })
        }
    }
}

@Composable
private fun RowScope.AppNavigationBarItem(item: AppNavigationItem, selected: Boolean, onClick: () -> Unit) {
    val colors = AppNavigationDefaults.colors(AppTheme.colors)
    val description = stringResource(item.label)
    val density = LocalDensity.current
    val labelDensity =
        remember(density) { Density(density.density, density.fontScale.coerceAtMost(MAX_LABEL_FONT_SCALE)) }
    val labelStyle = AppNavigationDefaults.labelStyle(AppTheme.typography.labelTechnical, selected)
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                painterResource(item.iconFor(selected)),
                contentDescription = null,
                modifier = Modifier.size(AppNavigationDefaults.IconSize),
            )
        },
        label = {
            CompositionLocalProvider(LocalDensity provides labelDensity) {
                Text(
                    text = description.uppercase(),
                    style = labelStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        },
        modifier = Modifier.testTag(item.testTag).semantics { contentDescription = description },
        alwaysShowLabel = true,
        colors =
        NavigationBarItemDefaults.colors(
            selectedIconColor = colors.selected,
            selectedTextColor = colors.selected,
            indicatorColor = colors.indicator,
            unselectedIconColor = colors.unselected,
            unselectedTextColor = colors.unselected,
        ),
    )
}

@Preview
@Composable
private fun AppNavigationPreview() {
    val items =
        listOf(
            AppNavigationItem(
                Res.string.nav_diary,
                Res.drawable.ic_nav_diary,
                "nav_tab_diary",
                Res.drawable.ic_nav_diary_filled,
            ),
            AppNavigationItem(Res.string.nav_workout, Res.drawable.ic_nav_workout, "nav_tab_workout"),
            AppNavigationItem(Res.string.nav_progress, Res.drawable.ic_nav_progress, "nav_tab_progress"),
        )
    AppTheme { AppNavigation(items, selectedIndex = 0, onItemSelect = {}) }
}
