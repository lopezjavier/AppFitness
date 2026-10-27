package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShellChromeTest {
    private val tabRoutes: List<TabRoute> =
        listOf(DiaryGraph, DiaryHome, WorkoutGraph, WorkoutHome, ProfileGraph, ProfileHome)

    @Test
    fun catalogRoute_chrome_isScreen_becauseItDrawsItsOwnBarThroughAppScreen() {
        assertEquals(ShellChrome.Screen, CatalogRoute.chrome)
    }

    @Test
    fun preferencesRoute_chrome_isFullScreenWithPreferencesTitle() {
        assertEquals(
            ShellChrome.FullScreen(Res.string.preferences_title, topBarTag = "preferences"),
            PreferencesRoute.chrome,
        )
    }

    @Test
    fun editEntryRoute_chrome_keepsTheDiaryTabChrome_soTheBarsDoNotRedraw() {
        assertEquals(ShellChrome.Tabs(TopLevelTab.Diary), EditEntryRoute("e1", "2026-10-19").chrome)
        assertTrue(AppDestinations.fullScreenRoutes.none { it is EditEntryRoute })
    }

    @Test
    fun saveTemplateRoute_chrome_keepsTheDiaryTabChrome_likeTheEditSheet() {
        assertEquals(ShellChrome.Tabs(TopLevelTab.Diary), SaveTemplateRoute("breakfast", "2026-10-19").chrome)
        assertTrue(AppDestinations.fullScreenRoutes.none { it is SaveTemplateRoute })
    }

    @Test
    fun tabRoutes_chrome_isTabsWithTheirTab_exceptTheRootsThatDrawTheirOwnHeader() {
        tabRoutes.filter { it != DiaryHome && it != WorkoutHome }
            .forEach { assertEquals(ShellChrome.Tabs(it.tab), it.chrome, it.toString()) }
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Diary), DiaryHome.chrome)
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Workout), WorkoutHome.chrome)
    }

    @Test
    @Suppress("ComplexCondition") // Lists the routes that are objects.
    fun fullScreenRoutes_containsTheFullScreenDestinations_andNoTabRoute() {
        assertEquals(
            listOf(
                CatalogRoute,
                PreferencesRoute,
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
                WeightRoute,
                WeightHistoryRoute,
            ),
            AppDestinations.fullScreenRoutes.map {
                if (it is CatalogRoute ||
                    it is PreferencesRoute ||
                    it is MealSlotsRoute ||
                    it is MealDefaultsRoute ||
                    it is LiveSessionRoute ||
                    it is HistoryRoute ||
                    it is StrengthRoute ||
                    it is MuscleVolumeRoute ||
                    it is StatsRoute ||
                    it is DiaryHistoryRoute ||
                    it is WeightRoute ||
                    it is WeightHistoryRoute
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
    fun fullScreenRoutes_allHaveFullScreenChrome_exceptTheOnesThatDrawTheirOwnBar() {
        AppDestinations.fullScreenRoutes.filter { !it.usesAppScreen }
            .forEach { assertIs<ShellChrome.FullScreen>(it.chrome) }
    }

    @Test
    fun liveSessionRoute_drawsItsBarThroughAppScreen_andHasNoBottomBar() {
        assertTrue(LiveSessionRoute.usesAppScreen)
        assertEquals(ShellChrome.Screen, LiveSessionRoute.chrome)
    }

    @Test
    fun historial_drawsItsOwnBarThroughAppScreen() {
        assertTrue(HistoryRoute.usesAppScreen)
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
        assertEquals(ShellChrome.Screen, WeightRoute.chrome)
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
