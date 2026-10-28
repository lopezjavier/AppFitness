package io.github.lopezjavier.appfitness.app

import kotlin.test.Test
import kotlin.test.assertEquals

/** T-128: every route draws its own bar through AppScreen: a tab root is TabScreen, any other route is Screen. */
class AppRoutesChromeTest {
    @Test
    fun catalogRoute_hasScreenChrome() {
        assertEquals(ShellChrome.Screen, CatalogRoute.chrome)
    }

    @Test
    fun theDiaryRoutes_haveScreenChrome() {
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Diary), DiaryHome.chrome)
        assertEquals(ShellChrome.Screen, DiaryHistoryRoute.chrome)
        assertEquals(ShellChrome.Screen, DiaryDayRoute("2026-10-26").chrome)
    }

    @Test
    fun theWeightRoutes_haveScreenChrome() {
        listOf(WeightRoute(), WeightHistoryRoute, NotificationCenterRoute).forEach {
            assertEquals(ShellChrome.Screen, it.chrome, it.toString())
        }
    }

    @Test
    fun theRoutineRoutes_haveScreenChrome() {
        listOf(RoutineDetailsRoute("r1"), RoutineBuilderRoute(), RoutineBuilderRoute("r1")).forEach {
            assertEquals(ShellChrome.Screen, it.chrome, it.toString())
        }
    }

    @Test
    fun theExerciseRoutes_haveScreenChrome() {
        listOf(ExercisePickerRoute(), ExercisePickerRoute(selectMode = true), ExerciseDetailRoute("e")).forEach {
            assertEquals(ShellChrome.Screen, it.chrome, it.toString())
        }
    }

    @Test
    fun theStatisticsRoutes_haveScreenChrome() {
        listOf(StrengthRoute, MuscleVolumeRoute, StatsRoute, ExerciseProgressRoute("e")).forEach {
            assertEquals(ShellChrome.Screen, it.chrome, it.toString())
        }
    }

    @Test
    fun theEntrenoRoot_hasTheTabScreenChrome() {
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Workout), WorkoutHome.chrome)
    }

    @Test
    fun theFoodRoutes_haveScreenChrome() {
        val food = listOf(
            FoodSearchRoute(date = "2026-10-26"),
            AddFoodRoute("f", date = "2026-10-26"),
            CreateFoodRoute(date = "2026-10-26"),
            EditFoodRoute("f", date = "2026-10-26"),
            TemplateDetailsRoute("t", date = "2026-10-26"),
        )

        food.forEach {
            assertEquals(ShellChrome.Screen, it.chrome, it.toString())
        }
    }

    @Test
    fun liveSession_hasScreenChrome() {
        assertEquals(ShellChrome.Screen, LiveSessionRoute.chrome)
    }

    @Test
    fun theMealRoutes_haveScreenChrome() {
        listOf(MealSlotsRoute, MealDefaultsRoute).forEach {
            assertEquals(ShellChrome.Screen, it.chrome, it.toString())
        }
    }

    @Test
    fun thePreferenciasRoutes_haveScreenChrome() {
        listOf(PreferencesRoute, PreferencesSectionRoute("energy"), AccountRoute).forEach {
            assertEquals(ShellChrome.Screen, it.chrome, it.toString())
        }
    }

    @Test
    fun thePerfilRoot_hasTheTabScreenChrome() {
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Profile), ProfileHome.chrome)
    }

    @Test
    fun theDiarySheets_keepTheDiaryTabChrome() {
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Diary), EditEntryRoute("e", "2026-10-26").chrome)
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Diary), SaveTemplateRoute("s", "2026-10-26").chrome)
    }

    @Test
    fun tabRoutes_haveTheTabScreenChrome() {
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Diary), DiaryGraph.chrome)
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Workout), WorkoutGraph.chrome)
        assertEquals(ShellChrome.TabScreen(TopLevelTab.Profile), ProfileGraph.chrome)
    }
}
