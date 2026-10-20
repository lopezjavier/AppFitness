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
                SessionDetailRoute::class,
                StrengthRoute,
                MuscleVolumeRoute,
                ExerciseProgressRoute::class,
                FoodSearchRoute::class,
                AddFoodRoute::class,
                CreateFoodRoute::class,
                MealSlotsRoute,
            ),
            AppDestinations.fullScreenRoutes.map {
                if (it is CatalogRoute ||
                    it is PreferencesRoute ||
                    it is MealSlotsRoute ||
                    it is LiveSessionRoute ||
                    it is HistoryRoute ||
                    it is StrengthRoute ||
                    it is MuscleVolumeRoute
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
        assertEquals("session_detail_top_bar", (SessionDetailRoute("s1").chrome as ShellChrome.FullScreen).topBarTag)
    }

    @Test
    fun strengthRoutes_haveFullScreenChrome_withTheirTopBarTags() {
        assertEquals("strength", (StrengthRoute.chrome as ShellChrome.FullScreen).topBarTag)
        assertEquals("muscle_volume", (MuscleVolumeRoute.chrome as ShellChrome.FullScreen).topBarTag)
        assertEquals("exercise_progress", (ExerciseProgressRoute("e1").chrome as ShellChrome.FullScreen).topBarTag)
    }
}
