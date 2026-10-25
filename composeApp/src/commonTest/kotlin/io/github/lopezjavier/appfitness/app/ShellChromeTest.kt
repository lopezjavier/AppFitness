package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShellChromeTest {
    private val tabRoutes: List<TabRoute> =
        listOf(DiaryGraph, DiaryHome, WorkoutGraph, WorkoutHome, ProfileGraph, ProfileHome)

    @Test
    fun catalogRoute_chrome_isFullScreenWithCatalogTitle() {
        assertEquals(ShellChrome.FullScreen(Res.string.catalog_title), CatalogRoute.chrome)
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
    fun tabRoutes_chrome_isTabsWithTheirTab() {
        tabRoutes.forEach { assertEquals(ShellChrome.Tabs(it.tab), it.chrome, it.toString()) }
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
                LiveSessionRoute,
                HistoryRoute,
                WorkoutCalendarRoute,
                SessionDetailRoute::class,
                EditWorkoutRoute::class,
                PastWorkoutRoute::class,
                StrengthRoute,
                MuscleVolumeRoute,
                ExerciseProgressRoute::class,
                FoodSearchRoute::class,
                AddFoodRoute::class,
                CreateFoodRoute::class,
                EditFoodRoute::class,
                TemplateDetailsRoute::class,
                MealSlotsRoute,
                DiaryHistoryRoute,
                DiaryDayRoute::class,
                WeightRoute,
            ),
            AppDestinations.fullScreenRoutes.map {
                if (it is CatalogRoute ||
                    it is PreferencesRoute ||
                    it is MealSlotsRoute ||
                    it is LiveSessionRoute ||
                    it is HistoryRoute ||
                    it is WorkoutCalendarRoute ||
                    it is StrengthRoute ||
                    it is MuscleVolumeRoute ||
                    it is DiaryHistoryRoute ||
                    it is WeightRoute
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
        AppDestinations.fullScreenRoutes.filter { it != LiveSessionRoute }
            .forEach { assertIs<ShellChrome.FullScreen>(it.chrome) }
    }

    @Test
    fun liveSessionRoute_drawsItsOwnBar_andHasNoBottomBar() {
        assertEquals(ShellChrome.Own, LiveSessionRoute.chrome)
    }

    @Test
    fun historyRoutes_haveFullScreenChrome_withTheirTopBarTags() {
        assertEquals("history_top_bar", (HistoryRoute.chrome as ShellChrome.FullScreen).topBarTag)
        assertEquals("calendar_top_bar", (WorkoutCalendarRoute.chrome as ShellChrome.FullScreen).topBarTag)
        assertEquals("session_detail_top_bar", (SessionDetailRoute("s1").chrome as ShellChrome.FullScreen).topBarTag)
        assertEquals("edit_workout_top_bar", (EditWorkoutRoute("s1").chrome as ShellChrome.FullScreen).topBarTag)
        assertEquals("past_workout_top_bar", (PastWorkoutRoute().chrome as ShellChrome.FullScreen).topBarTag)
    }

    @Test
    fun diaryHistoryRoutes_haveFullScreenChrome_withTheirTopBarTags() {
        assertEquals("diary_history", (DiaryHistoryRoute.chrome as ShellChrome.FullScreen).topBarTag)
        assertEquals("diary_day", (DiaryDayRoute("2026-10-18").chrome as ShellChrome.FullScreen).topBarTag)
    }

    @Test
    fun weightRoute_hasFullScreenChrome_withItsTopBarTag() {
        assertEquals("weight", (WeightRoute.chrome as ShellChrome.FullScreen).topBarTag)
    }

    @Test
    fun strengthRoutes_haveFullScreenChrome_withTheirTopBarTags() {
        assertEquals("strength", (StrengthRoute.chrome as ShellChrome.FullScreen).topBarTag)
        assertEquals("muscle_volume", (MuscleVolumeRoute.chrome as ShellChrome.FullScreen).topBarTag)
        assertEquals("exercise_progress", (ExerciseProgressRoute("e1").chrome as ShellChrome.FullScreen).topBarTag)
    }
}
