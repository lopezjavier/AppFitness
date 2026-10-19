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
    fun tabRoutes_chrome_isTabsWithTheirTab() {
        tabRoutes.forEach { assertEquals(ShellChrome.Tabs(it.tab), it.chrome, it.toString()) }
    }

    @Test
    fun fullScreenRoutes_containsTheFullScreenDestinations_andNoTabRoute() {
        assertEquals(
            listOf(
                CatalogRoute,
                PreferencesRoute,
                ExercisePickerRoute::class,
                ExerciseDetailRoute::class,
                RoutineBuilderRoute::class,
            ),
            AppDestinations.fullScreenRoutes.map {
                if (it is CatalogRoute ||
                    it is PreferencesRoute
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
    fun fullScreenRoutes_allHaveFullScreenChrome() {
        AppDestinations.fullScreenRoutes.forEach { assertIs<ShellChrome.FullScreen>(it.chrome) }
    }
}
