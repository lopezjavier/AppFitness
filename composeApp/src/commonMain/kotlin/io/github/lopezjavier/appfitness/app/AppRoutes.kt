package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary_filled
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_profile
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_title
import io.github.lopezjavier.appfitness.designsystem.AppTopBarDefaults
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** Type-safe navigation routes (ADR-10, ADR-22). Every route must have a [chrome]. */
@Serializable
sealed interface AppRoute

/** Routes that live inside one of the bottom bar tabs. Every one must be mapped to a tab in [tab]. */
@Serializable
sealed interface TabRoute : AppRoute

@Serializable
data object DiaryGraph : TabRoute

@Serializable
data object WorkoutGraph : TabRoute

@Serializable
data object ProfileGraph : TabRoute

@Serializable
data object DiaryHome : TabRoute

@Serializable
data object WorkoutHome : TabRoute

@Serializable
data object ProfileHome : TabRoute

/** Debug component catalog: local flavor only (see [AppFeatures]), opened from the Perfil root. */
@Serializable
data object CatalogRoute : AppRoute

/** "Preferencias": the personal data, energy and macro editor, opened from the Perfil root (ADR-37). */
@Serializable
data object PreferencesRoute : AppRoute

/**
 * Bottom bar tabs, in display order. Each one owns a nested graph (its own back stack).
 * [selectedIcon] is the filled variant when the glyph has one; otherwise it equals [icon].
 */
enum class TopLevelTab(
    val graph: TabRoute,
    val startRoute: TabRoute,
    val label: StringResource,
    val icon: DrawableResource,
    val selectedIcon: DrawableResource,
    val testTag: String,
) {
    Diary(
        DiaryGraph,
        DiaryHome,
        Res.string.nav_diary,
        Res.drawable.ic_nav_diary,
        Res.drawable.ic_nav_diary_filled,
        "nav_tab_diary",
    ),
    Workout(
        WorkoutGraph,
        WorkoutHome,
        Res.string.nav_workout,
        Res.drawable.ic_nav_workout,
        Res.drawable.ic_nav_workout,
        "nav_tab_workout",
    ),
    Profile(
        ProfileGraph,
        ProfileHome,
        Res.string.profile_title,
        Res.drawable.ic_nav_profile,
        Res.drawable.ic_nav_profile,
        "nav_tab_profile",
    ),
    ;

    companion object {
        val Start = Diary
    }
}

/** Tab that owns this route. Exhaustive on purpose: a new tab route does not compile until mapped. */
val TabRoute.tab: TopLevelTab
    get() =
        when (this) {
            DiaryGraph, DiaryHome -> TopLevelTab.Diary
            WorkoutGraph, WorkoutHome -> TopLevelTab.Workout
            ProfileGraph, ProfileHome -> TopLevelTab.Profile
        }

/** What the shell draws around a destination: top bar plus bottom bar, or a full-screen chrome. */
sealed interface ShellChrome {
    /** Top bar with the section title plus the bottom bar. */
    data class Tabs(val tab: TopLevelTab) : ShellChrome

    /** Top bar with a back arrow, no bottom bar. Its tags are `<topBarTag>`, `_title` and `_back`. */
    data class FullScreen(val title: StringResource, val topBarTag: String = AppTopBarDefaults.TestTag) : ShellChrome
}

/** Shell chrome of this route. Exhaustive on purpose. */
val AppRoute.chrome: ShellChrome
    get() =
        when (this) {
            is TabRoute -> ShellChrome.Tabs(tab)
            CatalogRoute -> ShellChrome.FullScreen(Res.string.catalog_title)
            PreferencesRoute -> ShellChrome.FullScreen(Res.string.preferences_title, topBarTag = "preferences")
        }

/** Navigation entry points shared by the shell. */
object AppDestinations {
    /**
     * Destinations drawn without the bottom bar. Used for chrome lookup only: whether a route is
     * reachable is decided by [AppFeatures].
     */
    val fullScreenRoutes: List<AppRoute> = listOf(CatalogRoute, PreferencesRoute)
}
