package io.github.lopezjavier.appfitness.app

import androidx.navigation.NavBackStackEntry
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.fab_action_add_food
import io.github.lopezjavier.appfitness.composeapp.resources.fab_action_log_weight
import io.github.lopezjavier.appfitness.composeapp.resources.fab_action_start_workout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FabActionTest {
    @Test
    fun diaryTab_mapsToAddFood() {
        assertEquals(FabAction.AddFood, ShellChrome.Tabs(TopLevelTab.Diary).fabAction())
    }

    @Test
    fun workoutTab_mapsToStartWorkout() {
        assertEquals(FabAction.StartWorkout, ShellChrome.Tabs(TopLevelTab.Workout).fabAction())
    }

    @Test
    fun progressTab_mapsToLogWeight() {
        assertEquals(FabAction.LogWeight, ShellChrome.Tabs(TopLevelTab.Progress).fabAction())
    }

    @Test
    fun profile_hasNoFab() {
        assertNull(ProfileRoute.chrome.fabAction())
    }

    @Test
    fun catalog_hasNoFab() {
        assertNull(CatalogRoute.chrome.fabAction())
    }

    @Test
    fun everyTab_hasADistinctAction() {
        val actions = TopLevelTab.entries.map { ShellChrome.Tabs(it).fabAction() }
        actions.forEach { assertNotNull(it) }
        assertEquals(actions.size, actions.toSet().size)
    }

    @Test
    fun beforeFirstEntry_startTabShowsItsFab() {
        val entry: NavBackStackEntry? = null
        assertEquals(FabAction.AddFood, entry.chrome().fabAction())
    }

    @Test
    fun titles_mapToTheirResources() {
        assertEquals(Res.string.fab_action_add_food, FabAction.AddFood.title)
        assertEquals(Res.string.fab_action_start_workout, FabAction.StartWorkout.title)
        assertEquals(Res.string.fab_action_log_weight, FabAction.LogWeight.title)
    }
}
