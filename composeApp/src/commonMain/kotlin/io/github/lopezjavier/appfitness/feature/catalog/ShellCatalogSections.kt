package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.action_edit
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_nav_rail_caption
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_navigation
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_skeleton
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_top_bar
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_wip
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_topbar_action_favourite
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_topbar_count
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_topbar_sample_subtitle
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_topbar_sample_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary_filled
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_profile
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.ic_notifications
import io.github.lopezjavier.appfitness.composeapp.resources.ic_star
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_title
import io.github.lopezjavier.appfitness.composeapp.resources.topbar_back_cd
import io.github.lopezjavier.appfitness.composeapp.resources.wip_footer
import io.github.lopezjavier.appfitness.composeapp.resources.wip_message
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNavigation
import io.github.lopezjavier.appfitness.designsystem.AppNavigationItem
import io.github.lopezjavier.appfitness.designsystem.AppNavigationType
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBar
import io.github.lopezjavier.appfitness.designsystem.AppTopBarAction
import io.github.lopezjavier.appfitness.designsystem.AppTopBarButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WipPlaceholder
import org.jetbrains.compose.resources.stringResource

private val WipSampleHeight: Dp = Spacing.lg * 20
private val RailSampleHeight: Dp = Spacing.xl * 15
private val SkeletonSampleHeight: Dp = Spacing.xl * 4
private const val NAV_DEFAULT_INDEX = 0

/** Catalog sections of the shell components and the loading placeholders (T-026). */
internal fun LazyListScope.shellItems() {
    item { TopBarSection() }
    item { NavigationSection() }
    overlayItems()
    item { SkeletonSection() }
    item { WipSection() }
}

private const val BADGE_SAMPLE_COUNT = 3

@Composable
private fun TopBarSection() {
    var count by rememberSaveable { mutableIntStateOf(0) }
    val backDescription = stringResource(Res.string.topbar_back_cd)
    val back = remember(backDescription) { AppTopBarButton(backDescription) { count++ } }
    CatalogSection(stringResource(Res.string.catalog_section_top_bar), "catalog_section_top_bar") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            // The embedded bars pad no system insets and carry their own tags, so the shell's stay unique.
            AppTopBar(
                title = stringResource(Res.string.nav_diary),
                windowInsets = WindowInsets(0),
                testTagPrefix = "catalog_topbar_tabs",
            )
            AppTopBar(
                title = stringResource(Res.string.profile_title),
                navigationButton = back,
                windowInsets = WindowInsets(0),
                testTagPrefix = "catalog_topbar_full",
            )
            ActionBarSamples()
        }
        Text(
            text = stringResource(Res.string.catalog_topbar_count, count.toString()),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("catalog_topbar_count"),
        )
    }
}

/** Top bars with 0, 1, 2 and 3 actions (the third goes to the overflow sheet) and one with a subtitle. */
@Composable
private fun ActionBarSamples() {
    val edit = stringResource(Res.string.action_edit)
    val favourite = stringResource(Res.string.catalog_topbar_action_favourite)
    val delete = stringResource(Res.string.action_delete)
    val title = stringResource(Res.string.catalog_topbar_sample_title)
    val all =
        remember(edit, favourite, delete) {
            listOf(
                AppTopBarAction(Res.drawable.ic_edit, edit, {}),
                AppTopBarAction(Res.drawable.ic_star, favourite, {}),
                AppTopBarAction(Res.drawable.ic_delete, delete, {}),
            )
        }
    for (count in 0..all.size) {
        AppTopBar(
            title = title,
            actions = all.take(count),
            windowInsets = WindowInsets(0),
            testTagPrefix = "catalog_topbar_actions_$count",
        )
    }
    val bell = stringResource(Res.string.notifications_title)
    AppTopBar(
        title = title,
        actions = listOf(AppTopBarAction(Res.drawable.ic_notifications, bell, {}, badgeCount = BADGE_SAMPLE_COUNT)),
        windowInsets = WindowInsets(0),
        testTagPrefix = "catalog_topbar_badge",
    )
    AppTopBar(
        title = title,
        subtitle = stringResource(Res.string.catalog_topbar_sample_subtitle),
        actions = all.take(2),
        windowInsets = WindowInsets(0),
        testTagPrefix = "catalog_topbar_subtitle",
    )
}

@Composable
private fun NavigationSection() {
    var selected by rememberSaveable { mutableIntStateOf(NAV_DEFAULT_INDEX) }
    val items = remember { NavigationSampleItems }
    val railItems = remember { RailSampleItems }
    CatalogSection(stringResource(Res.string.catalog_section_navigation), "catalog_section_navigation") {
        AppNavigation(
            items = items,
            selectedIndex = selected,
            onItemSelect = { selected = it },
            modifier = Modifier.testTag("catalog_nav_bar"),
            windowInsets = WindowInsets(0),
        )
        Text(
            text = stringResource(Res.string.catalog_nav_rail_caption),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
        // Fixed height: the rail fills the height it is given, and it wraps its width.
        Row {
            AppNavigation(
                items = railItems,
                selectedIndex = selected,
                onItemSelect = { selected = it },
                modifier = Modifier.testTag("catalog_nav_rail").height(RailSampleHeight),
                type = AppNavigationType.Rail,
                windowInsets = WindowInsets(0),
            )
        }
    }
}

private val NavigationSampleItems =
    listOf(
        AppNavigationItem(
            Res.string.nav_diary,
            Res.drawable.ic_nav_diary,
            "catalog_nav_tab_diary",
            Res.drawable.ic_nav_diary_filled,
        ),
        AppNavigationItem(Res.string.nav_workout, Res.drawable.ic_nav_workout, "catalog_nav_tab_workout"),
        AppNavigationItem(Res.string.profile_title, Res.drawable.ic_nav_profile, "catalog_nav_tab_profile"),
    )

private val RailSampleItems =
    listOf(
        AppNavigationItem(
            Res.string.nav_diary,
            Res.drawable.ic_nav_diary,
            "catalog_rail_tab_diary",
            Res.drawable.ic_nav_diary_filled,
        ),
        AppNavigationItem(Res.string.nav_workout, Res.drawable.ic_nav_workout, "catalog_rail_tab_workout"),
        AppNavigationItem(Res.string.profile_title, Res.drawable.ic_nav_profile, "catalog_rail_tab_profile"),
    )

@Composable
private fun SkeletonSection() {
    CatalogSection(stringResource(Res.string.catalog_section_skeleton), "catalog_section_skeleton") {
        // SkeletonBox is decorative and clears its own semantics, so the tag lives on a wrapper.
        Box(Modifier.fillMaxWidth().testTag("catalog_skeleton")) {
            SkeletonBox(Modifier.fillMaxWidth().height(SkeletonSampleHeight), AppShapes.lg)
        }
    }
}

@Composable
private fun WipSection() {
    CatalogSection(stringResource(Res.string.catalog_section_wip), "catalog_section_wip") {
        // The placeholder is a full-size lazy list, so it gets bounded constraints from a fixed-height card.
        AppCard(Modifier.fillMaxWidth().height(WipSampleHeight)) {
            WipPlaceholder(
                message = stringResource(Res.string.wip_message),
                footer = stringResource(Res.string.wip_footer),
                testTagPrefix = "catalog",
            )
        }
    }
}
