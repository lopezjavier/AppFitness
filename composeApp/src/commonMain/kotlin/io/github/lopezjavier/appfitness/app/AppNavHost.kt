package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navigation
import io.github.lopezjavier.appfitness.feature.diary.DiaryScreen
import io.github.lopezjavier.appfitness.feature.profile.ProfileScreen
import io.github.lopezjavier.appfitness.feature.progress.ProgressScreen
import io.github.lopezjavier.appfitness.feature.workout.WorkoutScreen

/** Single type-safe NavHost with one nested graph (own back stack) per tab (ADR-10). */
@Composable
internal fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = TopLevelTab.Start.graph, modifier = modifier) {
        navigation<DiaryGraph>(startDestination = TopLevelTab.Diary.startRoute) {
            composable<DiaryHome> { DiaryScreen() }
        }
        navigation<WorkoutGraph>(startDestination = TopLevelTab.Workout.startRoute) {
            composable<WorkoutHome> { WorkoutScreen() }
        }
        navigation<ProgressGraph>(startDestination = TopLevelTab.Progress.startRoute) {
            composable<ProgressHome> { ProgressScreen() }
        }
        navigation<ProfileGraph>(startDestination = TopLevelTab.Profile.startRoute) {
            composable<ProfileHome> { ProfileScreen() }
        }
    }
}

/**
 * The tab-switch function: keeps each tab's back stack, state and scroll position. Re-tapping
 * the active tab is a no-op. Any other entry point to a tab (the avatar, T-022) uses it.
 */
internal fun NavController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Tab that owns the current destination ([TopLevelTab.Start] before the first entry exists). */
@Composable
internal fun NavController.currentTopLevelTab(): TopLevelTab {
    val destination = currentBackStackEntryAsState().value?.destination
    return TopLevelTab.entries.firstOrNull { tab ->
        destination?.hierarchy?.any { it.hasRoute(tab.graph::class) } == true
    } ?: TopLevelTab.Start
}
