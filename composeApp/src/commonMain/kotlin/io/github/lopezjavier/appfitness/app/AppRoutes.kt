package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_title
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_title
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_title
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_title
import io.github.lopezjavier.appfitness.composeapp.resources.edit_food_title
import io.github.lopezjavier.appfitness.composeapp.resources.edit_workout_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_picker_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_title
import io.github.lopezjavier.appfitness.composeapp.resources.food_search_title
import io.github.lopezjavier.appfitness.composeapp.resources.history_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary_filled
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_profile
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.meal_defaults_title
import io.github.lopezjavier.appfitness.composeapp.resources.meals_title
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_title
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.past_workout_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_title
import io.github.lopezjavier.appfitness.composeapp.resources.routine_builder_title
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_title
import io.github.lopezjavier.appfitness.composeapp.resources.strength_title
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_title
import io.github.lopezjavier.appfitness.composeapp.resources.weight_title
import io.github.lopezjavier.appfitness.designsystem.AppTopBarDefaults
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

/** The training calendar (US-37): trained days by month, year and years, opened from the history. */
@Serializable
data object WorkoutCalendarRoute : AppRoute

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
data class PastWorkoutRoute(val routineId: String? = null, val sessionId: String? = null) : AppRoute

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

/** The weight screen (US-25): current weight and the measurement calendar, opened from Perfil > Estadísticas. */
@Serializable
data object WeightRoute : AppRoute

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

            WorkoutCalendarRoute -> ShellChrome.FullScreen(Res.string.calendar_title, topBarTag = "calendar_top_bar")

            is SessionDetailRoute ->
                ShellChrome.FullScreen(Res.string.session_detail_title, topBarTag = "session_detail_top_bar")

            is EditWorkoutRoute ->
                ShellChrome.FullScreen(Res.string.edit_workout_title, topBarTag = "edit_workout_top_bar")

            is PastWorkoutRoute ->
                ShellChrome.FullScreen(Res.string.past_workout_title, topBarTag = "past_workout_top_bar")

            StrengthRoute -> ShellChrome.FullScreen(Res.string.strength_title, topBarTag = "strength")

            MuscleVolumeRoute -> ShellChrome.FullScreen(Res.string.muscle_volume_title, topBarTag = "muscle_volume")

            is ExerciseProgressRoute ->
                ShellChrome.FullScreen(Res.string.exercise_progress_title, topBarTag = "exercise_progress")

            // The sheet shares the search chrome, so opening it does not redraw the bars.
            is FoodSearchRoute, is AddFoodRoute ->
                ShellChrome.FullScreen(Res.string.food_search_title, topBarTag = "food_search")

            is CreateFoodRoute -> ShellChrome.FullScreen(Res.string.create_food_title, topBarTag = "create_food")

            is EditFoodRoute -> ShellChrome.FullScreen(Res.string.edit_food_title, topBarTag = "edit_food")

            is TemplateDetailsRoute ->
                ShellChrome.FullScreen(Res.string.template_details_title, topBarTag = "template_details")

            MealSlotsRoute -> ShellChrome.FullScreen(Res.string.meals_title, topBarTag = "meals")

            MealDefaultsRoute ->
                ShellChrome.FullScreen(Res.string.meal_defaults_title, topBarTag = "meal_defaults")

            DiaryHistoryRoute ->
                ShellChrome.FullScreen(Res.string.diary_history_title, topBarTag = "diary_history")

            is DiaryDayRoute -> ShellChrome.FullScreen(Res.string.nav_diary, topBarTag = "diary_day")

            WeightRoute -> ShellChrome.FullScreen(Res.string.weight_title, topBarTag = "weight")

            // A sheet over the Diary tab or over a past day: the shell draws the chrome of the entry below it
            // instead (see [chromeOf]); this one is the fallback for the sheet opened over the Diary tab.
            is EditEntryRoute, is SaveTemplateRoute -> ShellChrome.Tabs(TopLevelTab.Diary)
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
            WorkoutCalendarRoute,
            SessionDetailRoute(""),
            EditWorkoutRoute(""),
            PastWorkoutRoute(),
            StrengthRoute,
            MuscleVolumeRoute,
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
            WeightRoute,
        )
}
