package io.github.lopezjavier.appfitness.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShellChromeTest {
    private val tabRoutes: List<TabRoute> =
        listOf(DiaryGraph, DiaryHome, WorkoutGraph, WorkoutHome, ProfileGraph, ProfileHome)

    @Test
    fun catalogRoute_chrome_isScreen_becauseItDrawsItsOwnBarThroughAppScreen() {
        assertEquals(ShellChrome.Screen, CatalogRoute.chrome)
    }

    @Test
    fun preferencesRoutes_chrome_isScreen_becauseTheyDrawTheirOwnBarThroughAppScreen() {
        assertEquals(ShellChrome.Screen, PreferencesRoute.chrome)
        assertEquals(ShellChrome.Screen, PreferencesSectionRoute("macros").chrome)
        assertEquals(ShellChrome.Screen, AccountRoute.chrome)
    }

    @Test
    fun editEntryRoute_chrome_keepsTheDiaryTabChrome_soTheBarsDoNotRedraw() {
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Diary), EditEntryRoute("e1", "2026-10-19").chrome)
        assertTrue(AppDestinations.fullScreenRoutes.none { it is EditEntryRoute })
    }

    @Test
    fun saveTemplateRoute_chrome_keepsTheDiaryTabChrome_likeTheEditSheet() {
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Diary), SaveTemplateRoute("breakfast", "2026-10-19").chrome)
        assertTrue(AppDestinations.fullScreenRoutes.none { it is SaveTemplateRoute })
    }

    @Test
    fun tabRoutes_chrome_isTabScreenWithTheirTab() {
        tabRoutes.forEach { assertEquals(ShellChrome.TabScreen(it.tab), it.chrome, it.toString()) }
    }

    @Test
    @Suppress("ComplexCondition", "CyclomaticComplexMethod", "LongMethod") // Lists the routes that are objects.
    fun fullScreenRoutes_containsTheFullScreenDestinations_andNoTabRoute() {
        assertEquals(
            listOf(
                CatalogRoute,
                PreferencesRoute,
                PreferencesSectionRoute::class,
                AccountRoute,
                ExercisePickerRoute::class,
                ExerciseDetailRoute::class,
                RoutineBuilderRoute::class,
                RoutineDetailsRoute::class,
                LiveSessionRoute,
                HistoryRoute,
                SessionDetailRoute::class,
                EditWorkoutRoute::class,
                PastWorkoutRoute::class,
                StrengthRoute,
                MuscleVolumeRoute,
                StatsRoute,
                ExerciseProgressRoute::class,
                FoodSearchRoute::class,
                AddFoodRoute::class,
                CreateFoodRoute::class,
                EditFoodRoute::class,
                TemplateDetailsRoute::class,
                MealSlotsRoute,
                MealDefaultsRoute,
                DiaryHistoryRoute,
                DiaryDayRoute::class,
                WeightRoute::class,
                WeightHistoryRoute,
                AboutRoute,
                NotificationCenterRoute,
                CheckInRoute,
            ),
            AppDestinations.fullScreenRoutes.map {
                if (it is CatalogRoute ||
                    it is PreferencesRoute ||
                    it is AccountRoute ||
                    it is MealSlotsRoute ||
                    it is MealDefaultsRoute ||
                    it is LiveSessionRoute ||
                    it is HistoryRoute ||
                    it is StrengthRoute ||
                    it is MuscleVolumeRoute ||
                    it is StatsRoute ||
                    it is DiaryHistoryRoute ||
                    it is WeightHistoryRoute ||
                    it is AboutRoute ||
                    it is NotificationCenterRoute ||
                    it is CheckInRoute
                ) {
                    it
                } else {
                    it::class
                }
            },
        )
        assertTrue(AppDestinations.fullScreenRoutes.none { it is TabRoute })
    }

    @Test
    fun fullScreenRoutes_allHaveScreenChrome() {
        AppDestinations.fullScreenRoutes.forEach { assertEquals(ShellChrome.Screen, it.chrome, it.toString()) }
    }

    @Test
    fun liveSessionRoute_drawsItsBarThroughAppScreen_andHasNoBottomBar() {
        assertEquals(ShellChrome.Screen, LiveSessionRoute.chrome)
    }

    @Test
    fun historial_drawsItsOwnBarThroughAppScreen() {
        assertEquals(ShellChrome.Screen, HistoryRoute.chrome)
    }

    @Test
    fun workoutDetailRoutes_drawTheirOwnBarThroughAppScreen() {
        assertEquals(ShellChrome.Screen, SessionDetailRoute("s1").chrome)
        assertEquals(ShellChrome.Screen, EditWorkoutRoute("s1").chrome)
        assertEquals(ShellChrome.Screen, PastWorkoutRoute().chrome)
    }

    @Test
    fun diaryHistoryRoutes_drawTheirOwnBarThroughAppScreen() {
        assertEquals(ShellChrome.Screen, DiaryHistoryRoute.chrome)
        assertEquals(ShellChrome.Screen, DiaryDayRoute("2026-10-18").chrome)
    }

    @Test
    fun weightRoutes_drawTheirOwnBarThroughAppScreen() {
        assertEquals(ShellChrome.Screen, WeightRoute().chrome)
        assertEquals(ShellChrome.Screen, WeightRoute(WeightEntryTarget.Circumference).chrome)
        assertEquals(ShellChrome.Screen, NotificationCenterRoute.chrome)
        assertEquals(ShellChrome.Screen, WeightHistoryRoute.chrome)
    }

    @Test
    fun statisticsRoutes_drawTheirOwnBarThroughAppScreen() {
        assertEquals(ShellChrome.Screen, StatsRoute.chrome)
        assertEquals(ShellChrome.Screen, StrengthRoute.chrome)
        assertEquals(ShellChrome.Screen, MuscleVolumeRoute.chrome)
        assertEquals(ShellChrome.Screen, ExerciseProgressRoute("e1").chrome)
    }
}
