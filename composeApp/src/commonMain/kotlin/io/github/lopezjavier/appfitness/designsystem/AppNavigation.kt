package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_profile
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_progress
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.nav_profile
import io.github.lopezjavier.appfitness.composeapp.resources.nav_progress
import io.github.lopezjavier.appfitness.composeapp.resources.nav_workout
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val MAX_LABEL_FONT_SCALE = 1.3f

/** One destination of [AppNavigation]. [testTag] goes on the selectable item. */
@Immutable
data class AppNavigationItem(val label: StringResource, val icon: DrawableResource, val testTag: String)

/**
 * Bottom navigation bar (phone). The selected item uses `neonLime` over a `glowLime` pill; the
 * rest use `textSecondary`. Labels are uppercase and their font scale is clamped to 130% so the
 * longest one never clips; each item keeps the original casing as its content description.
 * T-028 adds the NavigationRail variant behind this same API.
 *
 * @param items destinations in display order.
 * @param selectedIndex index into [items] of the active destination.
 * @param onItemSelect called with the index of the tapped destination.
 */
@Composable
fun AppNavigation(
    items: List<AppNavigationItem>,
    selectedIndex: Int,
    onItemSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = AppTheme.colors.borderSubtle
    NavigationBar(
        modifier =
        modifier
            .testTag("nav_bar")
            .drawBehind {
                drawLine(borderColor, Offset.Zero, Offset(size.width, 0f), strokeWidth = AppBorder.thin.toPx())
            },
        containerColor = AppTheme.colors.surfaceBase,
        tonalElevation = 0.dp,
    ) {
        items.forEachIndexed { index, item ->
            AppNavigationBarItem(item, selected = index == selectedIndex, onClick = { onItemSelect(index) })
        }
    }
}

@Composable
private fun RowScope.AppNavigationBarItem(item: AppNavigationItem, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val description = stringResource(item.label)
    val density = LocalDensity.current
    val labelDensity =
        remember(density) { Density(density.density, density.fontScale.coerceAtMost(MAX_LABEL_FONT_SCALE)) }
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(painterResource(item.icon), contentDescription = null) },
        label = {
            CompositionLocalProvider(LocalDensity provides labelDensity) {
                Text(
                    text = description.uppercase(),
                    style = AppTheme.typography.labelTechnical,
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
            selectedIconColor = colors.neonLime,
            selectedTextColor = colors.neonLime,
            indicatorColor = colors.glowLime,
            unselectedIconColor = colors.textSecondary,
            unselectedTextColor = colors.textSecondary,
        ),
    )
}

@Preview
@Composable
private fun AppNavigationPreview() {
    val items =
        listOf(
            AppNavigationItem(Res.string.nav_diary, Res.drawable.ic_nav_diary, "nav_tab_diary"),
            AppNavigationItem(Res.string.nav_workout, Res.drawable.ic_nav_workout, "nav_tab_workout"),
            AppNavigationItem(Res.string.nav_progress, Res.drawable.ic_nav_progress, "nav_tab_progress"),
            AppNavigationItem(Res.string.nav_profile, Res.drawable.ic_nav_profile, "nav_tab_profile"),
        )
    AppTheme { AppNavigation(items, selectedIndex = 0, onItemSelect = {}) }
}
