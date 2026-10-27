package io.github.lopezjavier.appfitness.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** T-118: only routes marked [usesAppScreen] lose the shell title bar; every other route keeps its chrome. */
class AppRoutesChromeTest {
    /** Route to the top bar test tag it had before T-118 (all full-screen). */
    private val legacyFullScreenTags: List<Pair<AppRoute, String>> =
        listOf(
            PreferencesRoute to "preferences",
            MealSlotsRoute to "meals",
            MealDefaultsRoute to "meal_defaults",
        )

    @Test
    fun catalogRoute_usesAppScreen_andHasScreenChrome() {
        assertTrue(CatalogRoute.usesAppScreen)
        assertEquals(ShellChrome.Screen, CatalogRoute.chrome)
    }

    @Test
    fun theDiaryRoutes_useAppScreen() {
        assertTrue(DiaryHome.usesAppScreen)
        assertTrue(DiaryHistoryRoute.usesAppScreen)
        assertTrue(DiaryDayRoute("2026-10-26").usesAppScreen)
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Diary), DiaryHome.chrome)
        assertEquals(ShellChrome.Screen, DiaryHistoryRoute.chrome)
        assertEquals(ShellChrome.Screen, DiaryDayRoute("2026-10-26").chrome)
    }

    @Test
    fun theWeightRoutes_useAppScreen_andHaveScreenChrome() {
        listOf(WeightRoute, WeightHistoryRoute).forEach {
            assertTrue(it.usesAppScreen, it.toString())
            assertEquals(ShellChrome.Screen, it.chrome, it.toString())
        }
    }

    @Test
    fun theRoutineRoutes_useAppScreen_andHaveScreenChrome() {
        listOf(RoutineDetailsRoute("r1"), RoutineBuilderRoute(), RoutineBuilderRoute("r1")).forEach {
            assertTrue(it.usesAppScreen, it.toString())
            assertEquals(ShellChrome.Screen, it.chrome, it.toString())
        }
    }

    @Test
    fun theExerciseRoutes_useAppScreen_andHaveScreenChrome() {
        listOf(ExercisePickerRoute(), ExercisePickerRoute(selectMode = true), ExerciseDetailRoute("e")).forEach {
            assertTrue(it.usesAppScreen, it.toString())
            assertEquals(ShellChrome.Screen, it.chrome, it.toString())
        }
    }

    @Test
    fun theStatisticsRoutes_useAppScreen_andHaveScreenChrome() {
        listOf(StrengthRoute, MuscleVolumeRoute, StatsRoute, ExerciseProgressRoute("e")).forEach {
            assertTrue(it.usesAppScreen, it.toString())
            assertEquals(ShellChrome.Screen, it.chrome, it.toString())
        }
    }

    @Test
    fun theEntrenoRoot_usesAppScreen_withTheTabScreenChrome() {
        assertTrue(WorkoutHome.usesAppScreen)
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Workout), WorkoutHome.chrome)
    }

    @Test
    fun theFoodRoutes_useAppScreen_andHaveScreenChrome() {
        val food = listOf(
            FoodSearchRoute(date = "2026-10-26"),
            AddFoodRoute("f", date = "2026-10-26"),
            CreateFoodRoute(date = "2026-10-26"),
            EditFoodRoute("f", date = "2026-10-26"),
            TemplateDetailsRoute("t", date = "2026-10-26"),
        )

        food.forEach {
            assertTrue(it.usesAppScreen, it.toString())
            assertEquals(ShellChrome.Screen, it.chrome, it.toString())
        }
    }

    @Test
    fun liveSession_drawsItsBarThroughAppScreen_withNoBottomBar() {
        assertTrue(LiveSessionRoute.usesAppScreen)
        assertEquals(ShellChrome.Screen, LiveSessionRoute.chrome)
    }

    @Test
    fun everyOtherRoute_doesNotUseAppScreen() {
        val others = legacyFullScreenTags.map { it.first } +
            listOf(EditEntryRoute("e", "2026-10-26"), SaveTemplateRoute("s", "2026-10-26")) +
            listOf(DiaryGraph, WorkoutGraph, ProfileGraph, ProfileHome)

        others.forEach { assertFalse(it.usesAppScreen, it.toString()) }
    }

    @Test
    fun legacyFullScreenRoutes_keepTheirShellTitleBarAndTag() {
        legacyFullScreenTags.forEach { (route, tag) ->
            val chrome = assertIs<ShellChrome.FullScreen>(route.chrome, route.toString())
            assertEquals(tag, chrome.topBarTag, route.toString())
        }
    }

    @Test
    fun theDiarySheets_keepTheirChrome() {
        assertEquals(ShellChrome.Tabs(TopLevelTab.Diary), EditEntryRoute("e", "2026-10-26").chrome)
        assertEquals(ShellChrome.Tabs(TopLevelTab.Diary), SaveTemplateRoute("s", "2026-10-26").chrome)
    }

    @Test
    fun tabRoutes_keepTheirTabsChrome() {
        assertEquals(ShellChrome.Tabs(TopLevelTab.Diary), DiaryGraph.chrome)
        assertEquals(ShellChrome.Tabs(TopLevelTab.Workout), WorkoutGraph.chrome)
        assertEquals(ShellChrome.Tabs(TopLevelTab.Profile), ProfileHome.chrome)
    }
}
