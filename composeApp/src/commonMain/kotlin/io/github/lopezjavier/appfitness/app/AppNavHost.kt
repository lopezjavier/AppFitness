package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import androidx.navigation.toRoute
import io.github.lopezjavier.appfitness.feature.catalog.ComponentCatalogScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryScreen
import io.github.lopezjavier.appfitness.feature.workout.WorkoutScreen

/**
 * Content of the Perfil root: its modifier (tab insets), the "Preferencias" opener and the catalog opener (null where
 * it is unavailable).
 */
internal typealias ProfileSlot =
    @Composable (modifier: Modifier, onOpenPreferences: () -> Unit, onOpenCatalog: (() -> Unit)?) -> Unit

/** Content of the full-screen Preferencias destination: its modifier (full-screen insets). */
internal typealias PreferencesSlot = @Composable (modifier: Modifier) -> Unit

/** Exercise picker content: modifier, select mode, and the two ways out (open a detail, return a selection). */
internal typealias ExercisePickerSlot =
    @Composable (
        modifier: Modifier,
        selectMode: Boolean,
        onOpenDetail: (String) -> Unit,
        onSelected: (String) -> Unit,
    ) -> Unit

/** Exercise detail content: modifier, exercise id, select mode and the select action. */
internal typealias ExerciseDetailSlot =
    @Composable (modifier: Modifier, exerciseId: String, selectMode: Boolean, onSelect: () -> Unit) -> Unit

/** Key under which a picker started in select mode leaves the chosen exercise id for the previous destination. */
const val SELECTED_EXERCISE_KEY = "selected_exercise_id"

/** Empty Perfil root for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultProfileSlot: ProfileSlot = { modifier, onOpenPreferences, _ ->
    Box(modifier.fillMaxSize().testTag("profile_screen")) {
        Box(Modifier.fillMaxSize().clickable(onClick = onOpenPreferences).testTag("profile_stub_open_preferences"))
    }
}

/** Empty picker for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultExercisePickerSlot: ExercisePickerSlot = { modifier, _, _, _ ->
    Box(modifier.fillMaxSize().testTag("exercise_picker_screen"))
}

/** Empty detail for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultExerciseDetailSlot: ExerciseDetailSlot = { modifier, _, _, _ ->
    Box(modifier.fillMaxSize().testTag("exercise_detail_screen"))
}

/** Empty Preferencias for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultPreferencesSlot: PreferencesSlot = { modifier ->
    Box(modifier.fillMaxSize().testTag("preferences_screen"))
}

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
    profile: ProfileSlot = DefaultProfileSlot,
    preferences: PreferencesSlot = DefaultPreferencesSlot,
    exercisePicker: ExercisePickerSlot = DefaultExercisePickerSlot,
    exerciseDetail: ExerciseDetailSlot = DefaultExerciseDetailSlot,
) {
    val catalogAvailable = features.isAvailable(CatalogRoute)
    val openCatalog = remember(navController) { { navController.navigateToFullScreen(CatalogRoute) } }
    val openPreferences = remember(navController) { { navController.navigateToFullScreen(PreferencesRoute) } }
    val openExerciseLibrary = remember(navController) { { navController.navigateToFullScreen(ExercisePickerRoute()) } }
    NavHost(navController = navController, startDestination = TopLevelTab.Start.graph, modifier = modifier) {
        navigation<DiaryGraph>(startDestination = TopLevelTab.Diary.startRoute) {
            composable<DiaryHome> { DiaryScreen(Modifier.padding(tabPadding)) }
        }
        navigation<WorkoutGraph>(startDestination = TopLevelTab.Workout.startRoute) {
            composable<WorkoutHome> { WorkoutScreen(Modifier.padding(tabPadding), openExerciseLibrary) }
        }
        navigation<ProfileGraph>(startDestination = TopLevelTab.Profile.startRoute) {
            composable<ProfileHome> {
                profile(Modifier.padding(tabPadding), openPreferences, if (catalogAvailable) openCatalog else null)
            }
        }
        composable<PreferencesRoute> { preferences(Modifier.padding(fullScreenPadding)) }
        composable<ExercisePickerRoute> { entry ->
            val selectMode = entry.toRoute<ExercisePickerRoute>().selectMode
            exercisePicker(
                Modifier.padding(fullScreenPadding),
                selectMode,
                { navController.navigateToFullScreen(ExerciseDetailRoute(it, selectMode)) },
                { navController.returnSelectedExercise(it) },
            )
        }
        composable<ExerciseDetailRoute> { entry ->
            val route = entry.toRoute<ExerciseDetailRoute>()
            exerciseDetail(
                Modifier.padding(fullScreenPadding),
                route.exerciseId,
                route.selectMode,
                { navController.returnSelectedExercise(route.exerciseId) },
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

/**
 * Hands [exerciseId] to the destination that opened the exercise picker (read it from its `savedStateHandle` under
 * [SELECTED_EXERCISE_KEY]) and pops the picker together with any detail above it.
 */
internal fun NavController.returnSelectedExercise(exerciseId: String) {
    val stack = currentBackStack.value
    val pickerIndex = stack.indexOfLast { it.destination.hasRoute(ExercisePickerRoute::class) }
    if (pickerIndex > 0) stack[pickerIndex - 1].savedStateHandle[SELECTED_EXERCISE_KEY] = exerciseId
    popBackStack<ExercisePickerRoute>(inclusive = true)
}
