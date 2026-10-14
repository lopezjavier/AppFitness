package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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

/** Layout of [AppNavigation]: bottom bar on phones, side rail from 600 dp (see [windowWidthClassFor]). */
enum class AppNavigationType { Bar, Rail }

/** Sizes, colours and label style of [AppNavigation]. */
object AppNavigationDefaults {
    /** Icon glyph size (mockup: 22). */
    val IconSize: Dp = 22.dp

    /** Bar height above the system navigation inset (mockup: 64). */
    val ContentHeight: Dp = 64.dp

    /** Minimum rail width (M3 NavigationRail container width); the rail grows to fit its header. */
    val RailMinWidth: Dp = 80.dp

    /** Insets the bar pads for; alias of [windowInsets] for [AppNavigationType.Bar]. */
    val windowInsets: WindowInsets
        @Composable get() = windowInsets(AppNavigationType.Bar)

    /**
     * Insets each layout pads for. The rail sits below the top bar, which already pads the top, so it
     * only pads start and bottom (display cutouts in landscape).
     */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun windowInsets(type: AppNavigationType): WindowInsets = when (type) {
        AppNavigationType.Bar -> WindowInsets.navigationBars

        AppNavigationType.Rail ->
            WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Bottom)
    }

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
 * Navigation for the top-level tabs: a bottom bar on phones, a side rail from 600 dp wide. The selected
 * item uses `neonLime`, an extra-bold label and its filled icon when it has one, with no pill
 * (ADR-22); the rest use `textSecondary`. Labels are uppercase and their font scale is clamped to
 * 130% so the longest one never clips; each item keeps the original casing as its content description.
 *
 * @param items destinations in display order.
 * @param selectedIndex index into [items] of the active destination.
 * @param onItemSelect called with the index of the tapped destination.
 * @param type [AppNavigationType.Bar] or [AppNavigationType.Rail].
 * @param windowInsets insets it pads for; pass `WindowInsets(0)` when it is embedded mid-screen.
 *   The caller tags it through [modifier] (the shell uses `nav_bar` / `nav_rail`).
 * @param header content above the items; used only by the rail (its font scale is clamped to 130%
 *   too), ignored by the bar.
 */
@Composable
fun AppNavigation(
    items: List<AppNavigationItem>,
    selectedIndex: Int,
    onItemSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    type: AppNavigationType = AppNavigationType.Bar,
    windowInsets: WindowInsets = AppNavigationDefaults.windowInsets(type),
    header: (@Composable ColumnScope.() -> Unit)? = null,
) {
    when (type) {
        AppNavigationType.Bar -> AppNavigationBar(items, selectedIndex, onItemSelect, windowInsets, modifier)

        AppNavigationType.Rail ->
            AppNavigationRail(items, selectedIndex, onItemSelect, windowInsets, header, modifier)
    }
}

@Composable
private fun AppNavigationBar(
    items: List<AppNavigationItem>,
    selectedIndex: Int,
    onItemSelect: (Int) -> Unit,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
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
            val selected = index == selectedIndex
            NavigationBarItem(
                selected = selected,
                onClick = { onItemSelect(index) },
                icon = { NavIcon(item, selected) },
                modifier = item.itemModifier(),
                alwaysShowLabel = true,
                label = { NavLabel(item, selected) },
                colors = navBarItemColors(),
            )
        }
    }
}

@Suppress("LongParameterList") // Mirrors the public AppNavigation contract, split per layout.
@Composable
private fun AppNavigationRail(
    items: List<AppNavigationItem>,
    selectedIndex: Int,
    onItemSelect: (Int) -> Unit,
    windowInsets: WindowInsets,
    header: (@Composable ColumnScope.() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val borderColor = AppTheme.colors.borderSubtle
    NavigationRail(
        modifier =
        modifier.drawWithContent {
            drawContent()
            val stroke = AppBorder.thin.toPx()
            val x = if (layoutDirection == LayoutDirection.Ltr) size.width - stroke / 2 else stroke / 2
            drawLine(borderColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
        },
        containerColor = AppTheme.colors.surfaceBase,
        header =
        header?.let { content ->
            {
                CompositionLocalProvider(LocalDensity provides clampedLabelDensity()) {
                    Column(
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        content()
                        Spacer(Modifier.height(Spacing.xl))
                    }
                }
            }
        },
        windowInsets = windowInsets,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            NavigationRailItem(
                selected = selected,
                onClick = { onItemSelect(index) },
                icon = { NavIcon(item, selected) },
                modifier = item.itemModifier(),
                alwaysShowLabel = true,
                label = { NavLabel(item, selected) },
                colors = navRailItemColors(),
            )
        }
    }
}

@Composable
private fun previewItems(): List<AppNavigationItem> = listOf(
    AppNavigationItem(
        Res.string.nav_diary,
        Res.drawable.ic_nav_diary,
        "nav_tab_diary",
        Res.drawable.ic_nav_diary_filled,
    ),
    AppNavigationItem(Res.string.nav_workout, Res.drawable.ic_nav_workout, "nav_tab_workout"),
    AppNavigationItem(Res.string.nav_progress, Res.drawable.ic_nav_progress, "nav_tab_progress"),
)

@Preview
@Composable
private fun AppNavigationPreview() {
    AppTheme { AppNavigation(previewItems(), selectedIndex = 0, onItemSelect = {}) }
}

@Preview
@Composable
private fun AppNavigationRailPreview() {
    AppTheme {
        AppNavigation(
            previewItems(),
            selectedIndex = 0,
            onItemSelect = {},
            type = AppNavigationType.Rail,
            header = { UniversalFab("+ Registro", onClick = {}) },
        )
    }
}
