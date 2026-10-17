package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import io.github.lopezjavier.appfitness.feature.catalog.ComponentCatalogScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryScreen
import io.github.lopezjavier.appfitness.feature.profile.ProfileScreen
import io.github.lopezjavier.appfitness.feature.progress.ProgressScreen
import io.github.lopezjavier.appfitness.feature.workout.WorkoutScreen

/**
 * Single type-safe NavHost with one nested graph (own back stack) per tab (ADR-10) and the
 * full-screen destinations at the root, above the tabs (ADR-22). Tabs and full-screen
 * destinations get their own padding so hiding the bottom bar never resizes a tab's viewport.
 */
@Composable
internal fun AppNavHost(
    navController: NavHostController,
    tabPadding: PaddingValues,
    fullScreenPadding: PaddingValues,
    features: AppFeatures,
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit = {},
) {
    val catalogAvailable = features.isAvailable(CatalogRoute)
    val openCatalog = remember(navController) { { navController.navigateToFullScreen(CatalogRoute) } }
    NavHost(navController = navController, startDestination = TopLevelTab.Start.graph, modifier = modifier) {
        navigation<DiaryGraph>(startDestination = TopLevelTab.Diary.startRoute) {
            composable<DiaryHome> { DiaryScreen(Modifier.padding(tabPadding)) }
        }
        navigation<WorkoutGraph>(startDestination = TopLevelTab.Workout.startRoute) {
            composable<WorkoutHome> { WorkoutScreen(Modifier.padding(tabPadding)) }
        }
        navigation<ProgressGraph>(startDestination = TopLevelTab.Progress.startRoute) {
            composable<ProgressHome> { ProgressScreen(Modifier.padding(tabPadding)) }
        }
        composable<ProfileRoute> {
            ProfileScreen(
                Modifier.padding(fullScreenPadding),
                onOpenCatalog = if (catalogAvailable) openCatalog else null,
                onSignOut = onSignOut,
            )
        }
        if (catalogAvailable) {
            composable<CatalogRoute> { ComponentCatalogScreen(Modifier.padding(fullScreenPadding)) }
        }
    }
}

/**
 * The tab-switch function: keeps each tab's back stack, state and scroll position. Re-tapping
 * the active tab is a no-op. It also pops any full-screen destination above the tabs.
 */
internal fun NavController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Pushes a full-screen destination on top of the current tab. No `popUpTo`: the tab entries stay
 * on the back stack, so their state and scroll survive until back.
 */
internal fun NavController.navigateToFullScreen(route: AppRoute) {
    navigate(route) { launchSingleTop = true }
}

/** The [AppDestinations.fullScreenRoutes] entry this destination is, or null for a tab destination. */
internal fun NavDestination?.fullScreenRoute(): AppRoute? =
    AppDestinations.fullScreenRoutes.firstOrNull { this?.hasRoute(it::class) == true }

/** True when this destination is one of [AppDestinations.fullScreenRoutes]. */
internal fun NavDestination?.isFullScreen(): Boolean = fullScreenRoute() != null

/**
 * Back guard: only pop when the top entry is still the full-screen entry the top bar was drawn
 * for, so a stale tap can never pop the tab underneath.
 */
internal fun shouldPopFullScreen(topEntryId: String?, drawnEntryId: String?, topIsFullScreen: Boolean): Boolean =
    topEntryId != null && topEntryId == drawnEntryId && topIsFullScreen

/** Pops the full-screen destination that [drawnEntry] belongs to, if it is still on top. */
internal fun NavController.popFullScreen(drawnEntry: NavBackStackEntry?) {
    val top = currentBackStackEntry
    if (shouldPopFullScreen(top?.id, drawnEntry?.id, top?.destination.isFullScreen())) popBackStack()
}

/** Chrome of an entry's destination ([TopLevelTab.Start] tabs before the first entry exists). */
internal fun NavBackStackEntry?.chrome(): ShellChrome {
    val destination = this?.destination
    val fullScreen = destination.fullScreenRoute()
    if (fullScreen != null) return fullScreen.chrome
    val tab =
        TopLevelTab.entries.firstOrNull { tab ->
            destination?.hierarchy?.any { it.hasRoute(tab.graph::class) } == true
        } ?: TopLevelTab.Start
    return ShellChrome.Tabs(tab)
}
