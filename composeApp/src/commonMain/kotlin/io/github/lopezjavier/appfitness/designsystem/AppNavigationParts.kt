package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemColors
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val MAX_LABEL_FONT_SCALE = 1.3f

@Composable
internal fun clampedLabelDensity(): Density {
    val density = LocalDensity.current
    return remember(density) { Density(density.density, density.fontScale.coerceAtMost(MAX_LABEL_FONT_SCALE)) }
}

@Composable
internal fun AppNavigationItem.itemModifier(): Modifier {
    val description = stringResource(label)
    return Modifier.testTag(testTag).semantics { contentDescription = description }
}

@Composable
internal fun NavIcon(item: AppNavigationItem, selected: Boolean) {
    Icon(
        painterResource(item.iconFor(selected)),
        contentDescription = null,
        modifier = Modifier.size(AppNavigationDefaults.IconSize),
    )
}

@Composable
internal fun NavLabel(item: AppNavigationItem, selected: Boolean) {
    CompositionLocalProvider(LocalDensity provides clampedLabelDensity()) {
        Text(
            text = stringResource(item.label).uppercase(),
            style = AppNavigationDefaults.labelStyle(AppTheme.typography.labelTechnical, selected),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
internal fun navBarItemColors(): NavigationBarItemColors {
    val colors = AppNavigationDefaults.colors(AppTheme.colors)
    return NavigationBarItemDefaults.colors(
        selectedIconColor = colors.selected,
        selectedTextColor = colors.selected,
        indicatorColor = colors.indicator,
        unselectedIconColor = colors.unselected,
        unselectedTextColor = colors.unselected,
    )
}

@Composable
internal fun navRailItemColors(): NavigationRailItemColors {
    val colors = AppNavigationDefaults.colors(AppTheme.colors)
    return NavigationRailItemDefaults.colors(
        selectedIconColor = colors.selected,
        selectedTextColor = colors.selected,
        indicatorColor = colors.indicator,
        unselectedIconColor = colors.unselected,
        unselectedTextColor = colors.unselected,
    )
}
