package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary_filled
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_profile
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.profile_title
import kotlinx.serialization.SerialName
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

/** "Preferencias": the hub of the settings sections, opened from the gear of the Perfil root (ADR-37, US-56). */
@Serializable
data object PreferencesRoute : AppRoute

/**
 * One Preferencias editor (US-56): [section] is the `PreferencesSection.key` (`personal`, `energy`, `macros`,
 * `cooking`, `training`). The argument is stored as `section`, the key under which its view model reads it.
 */
@Serializable
data class PreferencesSectionRoute(val section: String) : AppRoute

/** "Cuenta": sign out and delete the account, opened from the Preferencias hub (US-56). */
@Serializable
data object AccountRoute : AppRoute

/** "Acerca de": app name, version, credits and open-source licences, opened from the Perfil root (US-45). */
@Serializable
data object AboutRoute : AppRoute

/** Exercise picker (US-09). In [selectMode] a row returns the exercise to the caller instead of opening the detail. */
@Serializable
data class ExercisePickerRoute(val selectMode: Boolean = false) : AppRoute

/** Detail of one exercise; in [selectMode] it offers "Seleccionar". */
@Serializable
data class ExerciseDetailRoute(val exerciseId: String, val selectMode: Boolean = false) : AppRoute

/** Routine builder (US-10): a new routine when [routineId] is null, otherwise the edit of that routine. */
@Serializable
data class RoutineBuilderRoute(val routineId: String? = null) : AppRoute

/** Details of one routine (T-124): its exercises, last time done, start, edit and delete. */
@Serializable
data class RoutineDetailsRoute(val routineId: String) : AppRoute

/** The live workout (US-11): the single in-progress session, with its own top bar; its summary lives inside it. */
@Serializable
data object LiveSessionRoute : AppRoute

/** Historial de entrenos (US-37): the training calendar, opened from the Entreno tab. */
@Serializable
data object HistoryRoute : AppRoute

/** Detail of one completed session: sets, records, correction and deletion. */
@Serializable
data class SessionDetailRoute(val sessionId: String) : AppRoute

/** Edit screen of a completed session (US-37): name, description, gym, rests, exercises and sets, one "Guardar". */
@Serializable
data class EditWorkoutRoute(val sessionId: String) : AppRoute

/**
 * Form to log a workout of a past date (US-37), prefilled from the routine [routineId] or from the finished workout
 * [sessionId] used as a template; both null = an empty workout.
 */
@Serializable
data class PastWorkoutRoute(
    val routineId: String? = null,
    val sessionId: String? = null,
    /** ISO yyyy-MM-dd day the workout is logged on; null = yesterday. */
    val date: String? = null,
) : AppRoute

/** "Fuerza": the strength summary card, opened from the Perfil root (US-15). */
@Serializable
data object StrengthRoute : AppRoute

/** Weekly volume per muscle group (US-15), opened from Fuerza. */
@Serializable
data object MuscleVolumeRoute : AppRoute

/** "Estadisticas": training statistics and muscle distribution (US-40, US-41), opened from the Perfil root. */
@Serializable
data object StatsRoute : AppRoute

/** Progress charts of one exercise (US-15), opened from Fuerza, the exercise detail and the session detail. */
@Serializable
data class ExerciseProgressRoute(val exerciseId: String) : AppRoute

/**
 * Food search (US-17), opened from the Diary. [slotId] is the meal tapped there (null = the Diary-level add action)
 * and [date] the ISO day the food is logged to. With [pick] (also on the food routes it opens) it chooses a food for a
 * comida draft instead: [slotId] and [date] are then unused and the result goes back to the comida editor.
 */
@Serializable
data class FoodSearchRoute(val slotId: String? = null, val date: String, val pick: Boolean = false) : AppRoute

/**
 * Create-food form (US-18), opened from the search with the scanned [barcode] and/or the typed [name]; [slotId] and
 * [date] are carried on to the add-food sheet of the new food. [prefill] is a JSON `NewFoodDraft` (AI photo estimate).
 */
@Serializable
data class CreateFoodRoute(
    val barcode: String? = null,
    val name: String? = null,
    val slotId: String? = null,
    val date: String,
    val pick: Boolean = false,
    val prefill: String? = null,
) : AppRoute

/**
 * The edit form of the manual food [foodId] (US-47), opened from its add-food sheet (Navigation closes the sheet
 * when it is left); [slotId] and [date] are carried back to the sheet that reopens after "Guardar".
 */
@Serializable
data class EditFoodRoute(val foodId: String, val slotId: String? = null, val date: String, val pick: Boolean = false) :
    AppRoute

/** The add-food sheet of [foodId], presented over the food search; [slotId] and [date] as in [FoodSearchRoute]. */
@Serializable
data class AddFoodRoute(val foodId: String, val slotId: String? = null, val date: String, val pick: Boolean = false) :
    AppRoute

/**
 * The edit sheet of the diary entry [entryId] of the ISO day [date] (US-19), presented over the Diary. It keeps the
 * Diary's chrome (it is not a full-screen destination).
 */
@Serializable
data class EditEntryRoute(val entryId: String, val date: String) : AppRoute

/**
 * The save-as-comida sheet of the meal [slotId] of the ISO day [date] (US-21), presented over the Diary like
 * [EditEntryRoute] (it keeps the Diary's chrome).
 */
@Serializable
data class SaveTemplateRoute(val slotId: String, val date: String) : AppRoute

/**
 * The full-screen, directly editable details of the comida [templateId] (US-53), opened from the Comidas tab of the
 * food search; "Añadir" logs the foods as shown to the meal [slotId] (null = by the time of day) of the ISO day [date].
 */
@Serializable
data class TemplateDetailsRoute(val templateId: String, val slotId: String? = null, val date: String) : AppRoute

/** The meals editor (US-23), opened from Perfil -> Preferencias. */
@Serializable
data object MealSlotsRoute : AppRoute

/** The default comidas editor (US-51), opened from the meals editor. */
@Serializable
data object MealDefaultsRoute : AppRoute

/** The diary history (US-24): a month calendar with daily and period summaries, opened from Perfil > Estadísticas. */
@Serializable
data object DiaryHistoryRoute : AppRoute

/** What the weight screen opens on arrival, set by a reminder of the notification center (US-57). */
@Serializable
enum class WeightEntryTarget {
    /** Today's weight entry (the pencil, ADR-48). */
    Weight,

    /** The new-circumferences sheet. */
    Circumference,
}

/**
 * The weight screen (US-25): current weight, goal, trend and circumferences, opened from Perfil > Estadísticas. With
 * [openEntry] it opens the matching entry once on arrival (a reminder was tapped); null just shows the screen.
 */
@Serializable
data class WeightRoute(val openEntry: WeightEntryTarget? = null) : AppRoute

/** "Centro de avisos": the pending reminders and their switches, opened from the bell of the tab roots (US-57). */
@Serializable
data object NotificationCenterRoute : AppRoute

/** "Historial de peso": the measurement calendar, opened from the calendar action of [WeightRoute]. */
@Serializable
data object WeightHistoryRoute : AppRoute

/**
 * The Diary of one past [date] (ISO yyyy-MM-dd), opened from the history in a full-screen chrome with a back arrow. The
 * argument is stored as `diary_date`, the key under which the Diary's view model restores its visible day.
 */
@Serializable
data class DiaryDayRoute(@SerialName("diary_date") val date: String) : AppRoute

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

/** What the shell draws around a destination: every route draws its own header through `AppScreen` (T-128). */
sealed interface ShellChrome {
    /**
     * A tab root: the route draws its header through `AppScreen` but keeps the bottom bar and the rail. The shell
     * reserves only the status bar.
     */
    data class TabScreen(val tab: TopLevelTab) : ShellChrome

    /**
     * No bottom bar and no shell title bar: the route draws its bar through `AppScreen`.
     * The shell reserves the status bar so the offline banner sits below it.
     */
    data object Screen : ShellChrome
}

/**
 * Shell chrome of this route: [ShellChrome.TabScreen] for a tab root or graph, and for the edit and save-as-comida
 * sheets (the shell draws the chrome of the entry below them instead, see [chromeOf]); [ShellChrome.Screen] for the
 * rest.
 */
val AppRoute.chrome: ShellChrome
    get() =
        when (this) {
            is TabRoute -> ShellChrome.TabScreen(tab)
            is EditEntryRoute, is SaveTemplateRoute -> ShellChrome.TabScreen(TopLevelTab.Diary)
            else -> ShellChrome.Screen
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
            PreferencesSectionRoute(""),
            AccountRoute,
            ExercisePickerRoute(),
            ExerciseDetailRoute("", false),
            RoutineBuilderRoute(),
            RoutineDetailsRoute(""),
            LiveSessionRoute,
            HistoryRoute,
            SessionDetailRoute(""),
            EditWorkoutRoute(""),
            PastWorkoutRoute(),
            StrengthRoute,
            MuscleVolumeRoute,
            StatsRoute,
            ExerciseProgressRoute(""),
            FoodSearchRoute(null, ""),
            AddFoodRoute("", null, ""),
            CreateFoodRoute(date = ""),
            EditFoodRoute("", null, ""),
            TemplateDetailsRoute("", null, ""),
            MealSlotsRoute,
            MealDefaultsRoute,
            DiaryHistoryRoute,
            DiaryDayRoute(""),
            WeightRoute(),
            WeightHistoryRoute,
            AboutRoute,
            NotificationCenterRoute,
        )
}
