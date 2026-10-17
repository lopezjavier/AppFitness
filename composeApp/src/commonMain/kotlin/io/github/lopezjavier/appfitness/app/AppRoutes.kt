package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary_filled
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_progress
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.nav_progress
import io.github.lopezjavier.appfitness.composeapp.resources.nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.profile_title
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
data object ProgressGraph : TabRoute

@Serializable
data object DiaryHome : TabRoute

@Serializable
data object WorkoutHome : TabRoute

@Serializable
data object ProgressHome : TabRoute

/** Full-screen profile, opened only from the Top App Bar avatar, above the tabs (ADR-22). */
@Serializable
data object ProfileRoute : AppRoute

/** Debug component catalog: local flavor only (see [AppFeatures]), opened from Profile. */
@Serializable
data object CatalogRoute : AppRoute

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
    Progress(
        ProgressGraph,
        ProgressHome,
        Res.string.nav_progress,
        Res.drawable.ic_nav_progress,
        Res.drawable.ic_nav_progress,
        "nav_tab_progress",
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
            ProgressGraph, ProgressHome -> TopLevelTab.Progress
        }

/** What the shell draws around a destination: top bar plus bottom bar, or a full-screen chrome. */
sealed interface ShellChrome {
    /** Top bar with avatar plus the bottom bar. */
    data class Tabs(val tab: TopLevelTab) : ShellChrome

    /** Top bar with a back arrow, no bottom bar. */
    data class FullScreen(val title: StringResource) : ShellChrome
}

/** Shell chrome of this route. Exhaustive on purpose. */
val AppRoute.chrome: ShellChrome
    get() =
        when (this) {
            is TabRoute -> ShellChrome.Tabs(tab)
            ProfileRoute -> ShellChrome.FullScreen(Res.string.profile_title)
            CatalogRoute -> ShellChrome.FullScreen(Res.string.catalog_title)
        }

/** Navigation entry points shared by the shell. */
object AppDestinations {
    /** Destination the Top App Bar avatar opens (ADR-22). */
    val AvatarTarget: AppRoute = ProfileRoute

    /**
     * Destinations drawn without the bottom bar. Used for chrome lookup only: whether a route is
     * reachable is decided by [AppFeatures].
     */
    val fullScreenRoutes: List<AppRoute> = listOf(ProfileRoute, CatalogRoute)
}
