package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_picker_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_title
import io.github.lopezjavier.appfitness.composeapp.resources.food_search_title
import io.github.lopezjavier.appfitness.composeapp.resources.history_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary_filled
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_profile
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_title
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_title
import io.github.lopezjavier.appfitness.composeapp.resources.routine_builder_title
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_title
import io.github.lopezjavier.appfitness.composeapp.resources.strength_title
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

/** Exercise picker (US-09). In [selectMode] a row returns the exercise to the caller instead of opening the detail. */
@Serializable
data class ExercisePickerRoute(val selectMode: Boolean = false) : AppRoute

/** Detail of one exercise; in [selectMode] it offers "Seleccionar". */
@Serializable
data class ExerciseDetailRoute(val exerciseId: String, val selectMode: Boolean = false) : AppRoute

/** Routine builder (US-10): a new routine when [routineId] is null, otherwise the edit of that routine. */
@Serializable
data class RoutineBuilderRoute(val routineId: String? = null) : AppRoute

/** The live workout (US-11): the single in-progress session, with its own top bar; its summary lives inside it. */
@Serializable
data object LiveSessionRoute : AppRoute

/** The history of completed sessions (US-14), opened from the Entreno tab. */
@Serializable
data object HistoryRoute : AppRoute

/** Detail of one completed session: sets, records, correction and deletion. */
@Serializable
data class SessionDetailRoute(val sessionId: String) : AppRoute

/** "Fuerza": the strength summary card, opened from the Perfil root (US-15). */
@Serializable
data object StrengthRoute : AppRoute

/** Weekly volume per muscle group (US-15), opened from Fuerza. */
@Serializable
data object MuscleVolumeRoute : AppRoute

/** Progress charts of one exercise (US-15), opened from Fuerza, the exercise detail and the session detail. */
@Serializable
data class ExerciseProgressRoute(val exerciseId: String) : AppRoute

/**
 * Food search (US-17), opened from the Diary. [slotId] is the meal tapped there (null = the Diary-level add action)
 * and [date] the ISO day the food is logged to.
 */
@Serializable
data class FoodSearchRoute(val slotId: String? = null, val date: String) : AppRoute

/** The add-food sheet of [foodId], presented over the food search; [slotId] and [date] as in [FoodSearchRoute]. */
@Serializable
data class AddFoodRoute(val foodId: String, val slotId: String? = null, val date: String) : AppRoute

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

    /** No bottom bar and no shell top bar: the destination draws its own (the shell keeps the status bar inset). */
    data object Own : ShellChrome
}

/** Shell chrome of this route. Exhaustive on purpose. */
val AppRoute.chrome: ShellChrome
    get() =
        when (this) {
            is TabRoute -> ShellChrome.Tabs(tab)

            CatalogRoute -> ShellChrome.FullScreen(Res.string.catalog_title)

            PreferencesRoute -> ShellChrome.FullScreen(Res.string.preferences_title, topBarTag = "preferences")

            is ExercisePickerRoute ->
                ShellChrome.FullScreen(Res.string.exercise_picker_title, topBarTag = "exercise_picker_top_bar")

            is ExerciseDetailRoute ->
                ShellChrome.FullScreen(Res.string.exercise_detail_title, topBarTag = "exercise_detail_top_bar")

            is RoutineBuilderRoute ->
                ShellChrome.FullScreen(Res.string.routine_builder_title, topBarTag = "routine_builder_top_bar")

            LiveSessionRoute -> ShellChrome.Own

            HistoryRoute -> ShellChrome.FullScreen(Res.string.history_title, topBarTag = "history_top_bar")

            is SessionDetailRoute ->
                ShellChrome.FullScreen(Res.string.session_detail_title, topBarTag = "session_detail_top_bar")

            StrengthRoute -> ShellChrome.FullScreen(Res.string.strength_title, topBarTag = "strength")

            MuscleVolumeRoute -> ShellChrome.FullScreen(Res.string.muscle_volume_title, topBarTag = "muscle_volume")

            is ExerciseProgressRoute ->
                ShellChrome.FullScreen(Res.string.exercise_progress_title, topBarTag = "exercise_progress")

            // The sheet shares the search chrome, so opening it does not redraw the bars.
            is FoodSearchRoute, is AddFoodRoute ->
                ShellChrome.FullScreen(Res.string.food_search_title, topBarTag = "food_search")
        }

/** Navigation entry points shared by the shell. */
object AppDestinations {
    /**
     * Destinations drawn without the bottom bar. Used for chrome lookup only: whether a route is
     * reachable is decided by [AppFeatures].
     */
    val fullScreenRoutes: List<AppRoute> =
        listOf(
            CatalogRoute,
            PreferencesRoute,
            ExercisePickerRoute(),
            ExerciseDetailRoute("", false),
            RoutineBuilderRoute(),
            LiveSessionRoute,
            HistoryRoute,
            SessionDetailRoute(""),
            StrengthRoute,
            MuscleVolumeRoute,
            ExerciseProgressRoute(""),
            FoodSearchRoute(null, ""),
            AddFoodRoute("", null, ""),
        )
}
