package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShellChromeTest {
    private val tabRoutes: List<TabRoute> =
        listOf(DiaryGraph, DiaryHome, WorkoutGraph, WorkoutHome, ProgressGraph, ProgressHome, ProfileGraph, ProfileHome)

    @Test
    fun catalogRoute_chrome_isFullScreenWithCatalogTitle() {
        assertEquals(ShellChrome.FullScreen(Res.string.catalog_title), CatalogRoute.chrome)
    }

    @Test
    fun tabRoutes_chrome_isTabsWithTheirTab() {
        tabRoutes.forEach { assertEquals(ShellChrome.Tabs(it.tab), it.chrome, it.toString()) }
    }

    @Test
    fun fullScreenRoutes_containsOnlyTheCatalog_andNoTabRoute() {
        assertEquals(listOf<AppRoute>(CatalogRoute), AppDestinations.fullScreenRoutes)
        assertTrue(CatalogRoute in AppDestinations.fullScreenRoutes)
        assertTrue(AppDestinations.fullScreenRoutes.none { it is TabRoute })
    }

    @Test
    fun fullScreenRoutes_allHaveFullScreenChrome() {
        AppDestinations.fullScreenRoutes.forEach { assertIs<ShellChrome.FullScreen>(it.chrome) }
    }
}
