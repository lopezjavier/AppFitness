package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.profile_title
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShellChromeTest {
    private val tabRoutes: List<TabRoute> =
        listOf(DiaryGraph, DiaryHome, WorkoutGraph, WorkoutHome, ProgressGraph, ProgressHome)

    @Test
    fun avatarTarget_isProfileRoute() {
        assertEquals(ProfileRoute, AppDestinations.AvatarTarget)
    }

    @Test
    fun avatarTarget_isFullScreen() {
        assertFalse(AppDestinations.AvatarTarget is TabRoute)
        assertIs<ShellChrome.FullScreen>(AppDestinations.AvatarTarget.chrome)
    }

    @Test
    fun profileRoute_chrome_isFullScreenWithProfileTitle() {
        assertEquals(ShellChrome.FullScreen(Res.string.profile_title), ProfileRoute.chrome)
    }

    @Test
    fun tabRoutes_chrome_isTabsWithTheirTab() {
        tabRoutes.forEach { assertEquals(ShellChrome.Tabs(it.tab), it.chrome, it.toString()) }
    }

    @Test
    fun fullScreenRoutes_containsProfile_andNoTabRoute() {
        assertTrue(ProfileRoute in AppDestinations.fullScreenRoutes)
        assertTrue(AppDestinations.fullScreenRoutes.none { it is TabRoute })
    }

    @Test
    fun fullScreenRoutes_allHaveFullScreenChrome() {
        AppDestinations.fullScreenRoutes.forEach { assertIs<ShellChrome.FullScreen>(it.chrome) }
    }
}
