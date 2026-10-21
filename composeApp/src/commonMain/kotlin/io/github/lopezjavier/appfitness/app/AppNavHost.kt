@file:Suppress("TooManyFunctions") // Slot types and one helper per destination.

package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navigation
import androidx.navigation.toRoute
import io.github.lopezjavier.appfitness.feature.catalog.ComponentCatalogScreen
import io.github.lopezjavier.appfitness.feature.diary.DiaryEditHost
import io.github.lopezjavier.appfitness.feature.templates.TemplateDetailsRoot
import io.github.lopezjavier.appfitness.feature.templates.TemplateEditorRoot
import kotlinx.datetime.LocalDate

/**
 * Content of the Perfil root: its modifier (tab insets), the "Preferencias" opener, the catalog opener (null where
 * it is unavailable), the "Fuerza" opener and the "Historial de comidas" opener.
 */
internal typealias ProfileSlot =
    @Composable (
        modifier: Modifier,
        onOpenPreferences: () -> Unit,
        onOpenCatalog: (() -> Unit)?,
        onOpenStrength: () -> Unit,
        onOpenDiaryHistory: () -> Unit,
    ) -> Unit

/** Content of the full-screen Preferencias destination: its modifier (full-screen insets). */
internal typealias PreferencesSlot = @Composable (modifier: Modifier, onOpenMeals: () -> Unit) -> Unit

/** Exercise picker content: modifier, select mode, and the two ways out (open a detail, return a selection). */
internal typealias ExercisePickerSlot =
    @Composable (
        modifier: Modifier,
        selectMode: Boolean,
        onOpenDetail: (String) -> Unit,
        onSelected: (String) -> Unit,
    ) -> Unit

/** Exercise detail content: modifier, exercise id, select mode, the select action and the progress opener. */
internal typealias ExerciseDetailSlot =
    @Composable (
        modifier: Modifier,
        exerciseId: String,
        selectMode: Boolean,
        onSelect: () -> Unit,
        onOpenProgress: () -> Unit,
    ) -> Unit

/** "Mis rutinas" content: its modifier (tab insets) and the opener of the builder (null id = new routine). */
internal typealias RoutinesSlot =
    @Composable (
        modifier: Modifier,
        onOpenBuilder: (String?) -> Unit,
        onOpenLiveSession: () -> Unit,
        onOpenHistory: () -> Unit,
    ) -> Unit

/**
 * The Diario tab; [onOpenPreferences] leads the "set your goal" prompt to Preferencias, [onAddFood] opens the food
 * search for a day (the meal is null for the Diary-level add action), [edit] the edit sheet of an entry
 * (and brings back an entry the sheet asked to delete). The same slot renders a past day ([DiaryDayRoute]).
 */
internal typealias DiarySlot =
    @Composable (
        modifier: Modifier,
        onOpenPreferences: () -> Unit,
        onAddFood: (slotId: String?, date: LocalDate) -> Unit,
        onSaveTemplate: (slotId: String, date: LocalDate) -> Unit,
        edit: DiaryEditHost,
    ) -> Unit

/** Diary history content: modifier (full-screen insets) and the opener of the Diary of a day. */
internal typealias DiaryHistorySlot = @Composable (modifier: Modifier, onOpenDay: (LocalDate) -> Unit) -> Unit

/** Key under which the edit sheet leaves the id of the entry to delete for the Diary. */
const val DELETED_ENTRY_KEY = "deleted_entry_id"

/**
 * Edit-entry sheet content for an entry and its ISO day; [onSaved] runs once the entry is updated, [onDeleteRequested]
 * when the user chose "Eliminar" and [onDismiss] when the sheet is closed without saving.
 */
internal typealias EditEntrySlot =
    @Composable (
        entryId: String,
        date: String,
        onSaved: () -> Unit,
        onDeleteRequested: (entryId: String) -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit

/**
 * Food search content: modifier (full-screen insets), the meal and day the search adds to ([slotId] null = no meal
 * preselected, [date] ISO), the opener of the add-food sheet (by food id), the opener of the create-food form
 * (scanned barcode and/or typed name), [onAdd], which runs once a comida was added to the Diary, and [onOpenTemplate],
 * the opener of the details of a comida (by id).
 */
internal typealias FoodSearchSlot =
    @Composable (
        modifier: Modifier,
        slotId: String?,
        date: String,
        onOpenFood: (String) -> Unit,
        onCreateFood: (barcode: String?, name: String?) -> Unit,
        onAdd: () -> Unit,
        onOpenTemplate: (String) -> Unit,
    ) -> Unit

/**
 * Save-as-comida sheet content for the meal [slotId] and the ISO day [date]; [onSaved] runs once the comida was
 * created and [onDismiss] when the sheet is closed without saving.
 */
internal typealias SaveTemplateSlot =
    @Composable (
        slotId: String,
        date: String,
        onSave: () -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit

/** Meals editor content: modifier (full-screen insets) and how to leave. */
internal typealias MealSlotsSlot = @Composable (modifier: Modifier, onClose: () -> Unit) -> Unit

/** Create-food form content: modifier, the prefilled barcode and name, and [onSaved] with the new food id. */
internal typealias CreateFoodSlot =
    @Composable (modifier: Modifier, barcode: String?, name: String?, onSaved: (String) -> Unit) -> Unit

/**
 * Add-food sheet content for a food, the meal tapped in the Diary (or null) and the ISO day; [onAdded] runs once the
 * entry is logged and [onDismiss] when the sheet is dismissed.
 */
internal typealias AddFoodSlot =
    @Composable (
        foodId: String,
        slotId: String?,
        date: String,
        onAdded: () -> Unit,
        onDismiss: () -> Unit,
    ) -> Unit

/** History content: modifier (full-screen insets) and the opener of a session detail (by session id). */
internal typealias HistorySlot = @Composable (modifier: Modifier, onOpenSession: (String) -> Unit) -> Unit

/**
 * Session detail content: modifier (full-screen insets), the session id, how to leave after a deletion and the opener
 * of the progress of one of its exercises (by exercise id).
 */
internal typealias SessionDetailSlot =
    @Composable (
        modifier: Modifier,
        sessionId: String,
        onClose: () -> Unit,
        onOpenProgress: (String) -> Unit,
    ) -> Unit

/** "Fuerza" content: modifier (full-screen insets), the progress opener (by exercise id) and "start workout". */
internal typealias StrengthSlot =
    @Composable (
        modifier: Modifier,
        onOpenExercise: (String) -> Unit,
        onOpenMuscleVolume: () -> Unit,
        onStartWorkout: () -> Unit,
    ) -> Unit

/** Weekly muscle volume content: modifier (full-screen insets) and "start workout" (empty state). */
internal typealias MuscleVolumeSlot = @Composable (modifier: Modifier, onStartWorkout: () -> Unit) -> Unit

/** Exercise progress content: modifier (full-screen insets) and the exercise id. */
internal typealias ExerciseProgressSlot = @Composable (modifier: Modifier, exerciseId: String) -> Unit

/**
 * What the live workout needs from navigation: the exercise the picker returned (to be consumed with
 * [onConsumeSelection]), how to open the picker, how to open the read-only detail of an exercise ([onOpenExercise],
 * back returns to the same session) and how to leave the screen (the session stays in progress).
 */
class LiveSessionHost(
    val selectedExerciseId: String?,
    val onConsumeSelection: () -> Unit,
    val onAddExercise: () -> Unit,
    val onOpenExercise: (String) -> Unit,
    val onClose: () -> Unit,
)

/** Live workout content: modifier (full-screen insets) and its [LiveSessionHost]. */
internal typealias LiveSessionSlot = @Composable (modifier: Modifier, host: LiveSessionHost) -> Unit

/**
 * What the routine builder needs from navigation: the routine to edit (null = new), the exercise the picker returned
 * (to be consumed with [onConsumeSelection]), how to open the picker, how to open the read-only detail of an exercise
 * ([onOpenExercise], back returns to the same draft) and how to close the screen.
 */
class RoutineBuilderHost(
    val routineId: String?,
    val selectedExerciseId: String?,
    val onConsumeSelection: () -> Unit,
    val onAddExercise: () -> Unit,
    val onOpenExercise: (String) -> Unit,
    val onClose: () -> Unit,
)

/** Routine builder content: modifier (full-screen insets) and its [RoutineBuilderHost]. */
internal typealias RoutineBuilderSlot = @Composable (modifier: Modifier, host: RoutineBuilderHost) -> Unit

/** Key under which a picker started in select mode leaves the chosen exercise id for the previous destination. */
const val SELECTED_EXERCISE_KEY = "selected_exercise_id"

/** Empty Perfil root for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultProfileSlot: ProfileSlot = { modifier, onOpenPreferences, _, _, _ ->
    Box(modifier.fillMaxSize().testTag("profile_screen")) {
        Box(Modifier.fillMaxSize().clickable(onClick = onOpenPreferences).testTag("profile_stub_open_preferences"))
    }
}

/** Empty picker for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultExercisePickerSlot: ExercisePickerSlot = { modifier, _, _, _ ->
    Box(modifier.fillMaxSize().testTag("exercise_picker_screen"))
}

/** Empty detail for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultExerciseDetailSlot: ExerciseDetailSlot = { modifier, _, _, _, _ ->
    Box(modifier.fillMaxSize().testTag("exercise_detail_screen"))
}

/** Empty "Mis rutinas" for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultRoutinesSlot: RoutinesSlot = { modifier, _, _, _ ->
    Box(modifier.fillMaxSize().testTag("routines_screen"))
}

/** Empty builder for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultRoutineBuilderSlot: RoutineBuilderSlot = { modifier, _ ->
    Box(modifier.fillMaxSize().testTag("routine_builder_screen"))
}

/** Empty live workout for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultLiveSessionSlot: LiveSessionSlot = { modifier, _ ->
    Box(modifier.fillMaxSize().testTag("live_screen"))
}

/** Empty diary for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultDiarySlot: DiarySlot = { modifier, _, _, _, _ ->
    Box(modifier.fillMaxSize().testTag("diary_screen"))
}

/** Empty diary history for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultDiaryHistorySlot: DiaryHistorySlot = { modifier, _ ->
    Box(modifier.fillMaxSize().testTag("diary_history_screen"))
}

/** Empty food search for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultFoodSearchSlot: FoodSearchSlot = { modifier, _, _, _, _, _, _ ->
    Box(modifier.fillMaxSize().testTag("food_search_screen"))
}

/** Empty create-food form for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultCreateFoodSlot: CreateFoodSlot = { modifier, _, _, _ ->
    Box(modifier.fillMaxSize().testTag("create_food_screen"))
}

/** Empty meals editor for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultMealSlotsSlot: MealSlotsSlot = { modifier, _ ->
    Box(modifier.fillMaxSize().testTag("meals_screen"))
}

/** No sheet for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultAddFoodSlot: AddFoodSlot = { _, _, _, _, _ -> }

/** No sheet for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultEditEntrySlot: EditEntrySlot = { _, _, _, _, _ -> }

/** No sheet for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultSaveTemplateSlot: SaveTemplateSlot = { _, _, _, _ -> }

/** Empty history for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultHistorySlot: HistorySlot = { modifier, _ ->
    Box(modifier.fillMaxSize().testTag("history_screen"))
}

/** Empty session detail for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultSessionDetailSlot: SessionDetailSlot = { modifier, _, _, _ ->
    Box(modifier.fillMaxSize().testTag("session_detail_screen"))
}

/** Empty "Fuerza" for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultStrengthSlot: StrengthSlot = { modifier, _, _, _ ->
    Box(modifier.fillMaxSize().testTag("strength_screen"))
}

/** Empty weekly muscle volume for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultMuscleVolumeSlot: MuscleVolumeSlot = { modifier, _ ->
    Box(modifier.fillMaxSize().testTag("muscle_volume_screen"))
}

/** Empty exercise progress for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultExerciseProgressSlot: ExerciseProgressSlot = { modifier, _ ->
    Box(modifier.fillMaxSize().testTag("exercise_progress_screen"))
}

/** Empty Preferencias for previews and shell tests; the real one is wired by `AppRoot`. */
internal val DefaultPreferencesSlot: PreferencesSlot = { modifier, _ ->
    Box(modifier.fillMaxSize().testTag("preferences_screen"))
}

/**
 * Single type-safe NavHost with one nested graph (own back stack) per tab (ADR-10) and the
 * full-screen destinations at the root, above the tabs (ADR-22). Tabs and full-screen
 * destinations get their own padding so hiding the bottom bar never resizes a tab's viewport.
 */
@Composable
@Suppress("LongMethod") // The destination table, one line per route.
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
    routines: RoutinesSlot = DefaultRoutinesSlot,
    routineBuilder: RoutineBuilderSlot = DefaultRoutineBuilderSlot,
    liveSession: LiveSessionSlot = DefaultLiveSessionSlot,
    diary: DiarySlot = DefaultDiarySlot,
    diaryHistory: DiaryHistorySlot = DefaultDiaryHistorySlot,
    foodSearch: FoodSearchSlot = DefaultFoodSearchSlot,
    addFood: AddFoodSlot = DefaultAddFoodSlot,
    editEntry: EditEntrySlot = DefaultEditEntrySlot,
    saveTemplate: SaveTemplateSlot = DefaultSaveTemplateSlot,
    createFood: CreateFoodSlot = DefaultCreateFoodSlot,
    mealSlots: MealSlotsSlot = DefaultMealSlotsSlot,
    history: HistorySlot = DefaultHistorySlot,
    sessionDetail: SessionDetailSlot = DefaultSessionDetailSlot,
    strength: StrengthSlot = DefaultStrengthSlot,
    exerciseProgress: ExerciseProgressSlot = DefaultExerciseProgressSlot,
    muscleVolume: MuscleVolumeSlot = DefaultMuscleVolumeSlot,
) {
    val catalogAvailable = features.isAvailable(CatalogRoute)
    val openCatalog = remember(navController) { { navController.navigateToFullScreen(CatalogRoute) } }
    val openPreferences = remember(navController) { { navController.navigateToFullScreen(PreferencesRoute) } }
    val openBuilder: (String?) -> Unit =
        remember(navController) { { id -> navController.navigateToFullScreen(RoutineBuilderRoute(id)) } }
    val openFoodSearch: (String?, LocalDate) -> Unit =
        remember(navController) {
            { slotId, date -> navController.navigateToFullScreen(FoodSearchRoute(slotId, date.toString())) }
        }
    val openSaveTemplate: (String, LocalDate) -> Unit = remember(navController) {
        { slotId, date -> navController.navigate(SaveTemplateRoute(slotId, date.toString())) }
    }
    val openDiaryHistory = remember(navController) { { navController.navigateToFullScreen(DiaryHistoryRoute) } }
    val openMeals = remember(navController) { { navController.navigateToFullScreen(MealSlotsRoute) } }
    val openLiveSession = remember(navController) { { navController.navigateToFullScreen(LiveSessionRoute) } }
    val openHistory = remember(navController) { { navController.navigateToFullScreen(HistoryRoute) } }
    val openStrength = remember(navController) { { navController.navigateToFullScreen(StrengthRoute) } }
    val openProgress: (String) -> Unit =
        remember(navController) { { id -> navController.navigateToFullScreen(ExerciseProgressRoute(id)) } }
    NavHost(navController = navController, startDestination = TopLevelTab.Start.graph, modifier = modifier) {
        navigation<DiaryGraph>(startDestination = TopLevelTab.Diary.startRoute) {
            composable<DiaryHome> { entry ->
                val destinationModifier = Modifier.padding(tabPadding)
                DiaryDestination(
                    navController,
                    entry,
                    diary,
                    openPreferences,
                    openFoodSearch,
                    openSaveTemplate,
                    destinationModifier,
                )
            }
        }
        composable<DiaryDayRoute> { entry ->
            val destinationModifier = Modifier.padding(fullScreenPadding)
            DiaryDestination(
                navController,
                entry,
                diary,
                openPreferences,
                openFoodSearch,
                openSaveTemplate,
                destinationModifier,
            )
        }
        composable<DiaryHistoryRoute> {
            diaryHistory(Modifier.padding(fullScreenPadding)) { date ->
                navController.navigateToFullScreen(DiaryDayRoute(date.toString()))
            }
        }
        // The sheets over the Diary or a past day are root destinations, so they open over whichever is below.
        dialog<SaveTemplateRoute>(dialogProperties = DialogProperties(usePlatformDefaultWidth = false)) { entry ->
            val route = entry.toRoute<SaveTemplateRoute>()
            saveTemplate(route.slotId, route.date, {
                navController.popBackStack()
            }, { navController.popBackStack() })
        }
        dialog<EditEntryRoute>(dialogProperties = DialogProperties(usePlatformDefaultWidth = false)) { entry ->
            val route = entry.toRoute<EditEntryRoute>()
            editEntry(
                route.entryId,
                route.date,
                { navController.popBackStack() },
                { id ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(DELETED_ENTRY_KEY, id)
                    navController.popBackStack()
                },
                { navController.popBackStack() },
            )
        }
        navigation<WorkoutGraph>(startDestination = TopLevelTab.Workout.startRoute) {
            composable<WorkoutHome> {
                routines(Modifier.padding(tabPadding), openBuilder, openLiveSession, openHistory)
            }
        }
        navigation<ProfileGraph>(startDestination = TopLevelTab.Profile.startRoute) {
            composable<ProfileHome> {
                profile(
                    Modifier.padding(tabPadding),
                    openPreferences,
                    if (catalogAvailable) openCatalog else null,
                    openStrength,
                    openDiaryHistory,
                )
            }
        }
        composable<PreferencesRoute> { preferences(Modifier.padding(fullScreenPadding), openMeals) }
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
                { openProgress(route.exerciseId) },
            )
        }
        composable<RoutineBuilderRoute> { entry ->
            val handle = entry.savedStateHandle
            val selected by handle.getStateFlow<String?>(SELECTED_EXERCISE_KEY, null).collectAsState()
            routineBuilder(
                Modifier.padding(fullScreenPadding),
                RoutineBuilderHost(
                    routineId = entry.toRoute<RoutineBuilderRoute>().routineId,
                    selectedExerciseId = selected,
                    onConsumeSelection = { handle[SELECTED_EXERCISE_KEY] = null },
                    onAddExercise = { navController.navigateToFullScreen(ExercisePickerRoute(selectMode = true)) },
                    onOpenExercise = {
                        navController.navigateToFullScreen(ExerciseDetailRoute(it, selectMode = false))
                    },
                    onClose = { navController.popBackStack() },
                ),
            )
        }
        liveSessionDestination(navController, fullScreenPadding, liveSession)
        foodDestinations(navController, fullScreenPadding, foodSearch, addFood, createFood)
        composable<MealSlotsRoute> {
            mealSlots(Modifier.padding(fullScreenPadding)) { navController.popBackStack() }
        }
        historyDestinations(navController, fullScreenPadding, history, sessionDetail, openProgress)
        strengthDestinations(navController, fullScreenPadding, strength, exerciseProgress, muscleVolume, openProgress)
        if (catalogAvailable) {
            composable<CatalogRoute> { ComponentCatalogScreen(Modifier.padding(fullScreenPadding)) }
        }
    }
}

/**
 * The Diary of the tab ([DiaryHome]) or of a past day ([DiaryDayRoute]): the edit sheet's deletion request comes back
 * through the entry's `savedStateHandle`.
 */
@Composable
@Suppress("LongParameterList") // The Diary's callbacks plus its entry.
private fun DiaryDestination(
    navController: NavHostController,
    entry: NavBackStackEntry,
    diary: DiarySlot,
    openPreferences: () -> Unit,
    openFoodSearch: (String?, LocalDate) -> Unit,
    openSaveTemplate: (String, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val handle = entry.savedStateHandle
    val deleted by handle.getStateFlow<String?>(DELETED_ENTRY_KEY, null).collectAsState()
    diary(
        modifier,
        openPreferences,
        openFoodSearch,
        openSaveTemplate,
        DiaryEditHost(
            onEditEntry = { id, date -> navController.navigate(EditEntryRoute(id, date.toString())) },
            pendingDeleteEntryId = deleted,
            onConsumeDelete = { handle[DELETED_ENTRY_KEY] = null },
        ),
    )
}

/**
 * The food search and, over it, the add-food sheet (a dialog destination, so the search stays visible behind the
 * sheet). Logging a food pops both back to the Diary.
 */
private fun NavGraphBuilder.foodDestinations(
    navController: NavHostController,
    fullScreenPadding: PaddingValues,
    foodSearch: FoodSearchSlot,
    addFood: AddFoodSlot,
    createFood: CreateFoodSlot,
) {
    composable<FoodSearchRoute> { entry ->
        val route = entry.toRoute<FoodSearchRoute>()
        foodSearch(
            Modifier.padding(fullScreenPadding),
            route.slotId,
            route.date,
            { navController.navigate(AddFoodRoute(it, route.slotId, route.date)) { launchSingleTop = true } },
            { barcode, name ->
                navController.navigate(CreateFoodRoute(barcode, name, route.slotId, route.date)) {
                    launchSingleTop = true
                }
            },
            // A comida went to the Diary: leave the search for it.
            { navController.popToDiary() },
            { templateId -> navController.navigate(TemplateDetailsRoute(templateId)) { launchSingleTop = true } },
        )
    }
    composable<TemplateDetailsRoute> { entry ->
        val route = entry.toRoute<TemplateDetailsRoute>()
        // Deleting pops back to the search; coming back from the editor reloads the details when they resume.
        TemplateDetailsRoot(
            templateId = route.templateId,
            onEdit = { navController.navigate(TemplateEditorRoute(route.templateId)) { launchSingleTop = true } },
            onDelete = { navController.popBackStack() },
            modifier = Modifier.padding(fullScreenPadding),
        )
    }
    composable<TemplateEditorRoute> { entry ->
        val route = entry.toRoute<TemplateEditorRoute>()
        // Saving pops back to the details, which reload when they resume (and so does the search later).
        TemplateEditorRoot(
            templateId = route.templateId,
            onSave = { navController.popBackStack() },
            modifier = Modifier.padding(fullScreenPadding),
        )
    }
    composable<CreateFoodRoute> { entry ->
        val route = entry.toRoute<CreateFoodRoute>()
        createFood(Modifier.padding(fullScreenPadding), route.barcode, route.name) { foodId ->
            // The form is replaced by the sheet: "Añadir" then pops back to the Diary like the normal flow.
            navController.navigate(AddFoodRoute(foodId, route.slotId, route.date)) {
                popUpTo<CreateFoodRoute> { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    dialog<AddFoodRoute>(dialogProperties = DialogProperties(usePlatformDefaultWidth = false)) { entry ->
        val route = entry.toRoute<AddFoodRoute>()
        addFood(
            route.foodId,
            route.slotId,
            route.date,
            { navController.popToDiary() },
            { navController.popBackStack() },
        )
    }
}

/** The history list and the detail of one of its sessions; deleting a session pops back to the list. */
private fun NavGraphBuilder.historyDestinations(
    navController: NavHostController,
    fullScreenPadding: PaddingValues,
    history: HistorySlot,
    sessionDetail: SessionDetailSlot,
    openProgress: (String) -> Unit,
) {
    composable<HistoryRoute> {
        history(Modifier.padding(fullScreenPadding)) { navController.navigateToFullScreen(SessionDetailRoute(it)) }
    }
    composable<SessionDetailRoute> { entry ->
        sessionDetail(
            Modifier.padding(fullScreenPadding),
            entry.toRoute<SessionDetailRoute>().sessionId,
            { navController.popBackStack() },
            openProgress,
        )
    }
}

/** "Fuerza" and the progress of one exercise; the empty state leads to the Entreno tab. */
@Suppress("LongParameterList") // One slot per destination.
private fun NavGraphBuilder.strengthDestinations(
    navController: NavHostController,
    fullScreenPadding: PaddingValues,
    strength: StrengthSlot,
    exerciseProgress: ExerciseProgressSlot,
    muscleVolume: MuscleVolumeSlot,
    openProgress: (String) -> Unit,
) {
    composable<StrengthRoute> {
        strength(
            Modifier.padding(fullScreenPadding),
            openProgress,
            { navController.navigateToFullScreen(MuscleVolumeRoute) },
        ) { navController.navigateToTab(TopLevelTab.Workout) }
    }
    composable<MuscleVolumeRoute> {
        muscleVolume(Modifier.padding(fullScreenPadding)) { navController.navigateToTab(TopLevelTab.Workout) }
    }
    composable<ExerciseProgressRoute> { entry ->
        exerciseProgress(Modifier.padding(fullScreenPadding), entry.toRoute<ExerciseProgressRoute>().exerciseId)
    }
}

/** The live workout destination: the picker's result comes back through the entry's `savedStateHandle`. */
private fun NavGraphBuilder.liveSessionDestination(
    navController: NavHostController,
    fullScreenPadding: PaddingValues,
    liveSession: LiveSessionSlot,
) {
    composable<LiveSessionRoute> { entry ->
        val handle = entry.savedStateHandle
        val selected by handle.getStateFlow<String?>(SELECTED_EXERCISE_KEY, null).collectAsState()
        liveSession(
            Modifier.padding(fullScreenPadding),
            LiveSessionHost(
                selectedExerciseId = selected,
                onConsumeSelection = { handle[SELECTED_EXERCISE_KEY] = null },
                onAddExercise = { navController.navigateToFullScreen(ExercisePickerRoute(selectMode = true)) },
                onOpenExercise = { navController.navigateToFullScreen(ExerciseDetailRoute(it, selectMode = false)) },
                onClose = { navController.popBackStack() },
            ),
        )
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

/**
 * Pops back to the Diary that opened the food search: the past day when it was opened from one, otherwise the tab
 * (a food or comida was logged there, so the search and its sheets leave).
 */
internal fun NavController.popToDiary() {
    val target = currentBackStack.value.lastOrNull {
        it.destination.hasRoute(DiaryDayRoute::class) || it.destination.hasRoute(DiaryHome::class)
    }
    if (target != null) popBackStack(target.destination.id, inclusive = false)
}

/**
 * Chrome to draw for [entry]. The edit and save-as-comida sheets keep the bars of the Diary below them (the tab's
 * top and bottom bar, or the full-screen bar of a past day), so opening one never swaps the chrome.
 */
internal fun NavController.chromeOf(entry: NavBackStackEntry?): ShellChrome {
    val destination = entry?.destination
    val isSheet = destination?.hasRoute(EditEntryRoute::class) == true ||
        destination?.hasRoute(SaveTemplateRoute::class) == true
    return if (isSheet) previousBackStackEntry.chrome() else entry.chrome()
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
