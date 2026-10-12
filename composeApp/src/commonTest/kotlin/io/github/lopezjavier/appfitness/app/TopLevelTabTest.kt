package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary_filled
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TopLevelTabTest {
    private val tags = Regex("^[a-z]+(_[a-z0-9]+)*$")

    @Test
    fun start_isDiary() {
        assertEquals(TopLevelTab.Diary, TopLevelTab.Start)
    }

    @Test
    fun entries_followTheBarOrder() {
        assertEquals(listOf(TopLevelTab.Diary, TopLevelTab.Workout, TopLevelTab.Progress), TopLevelTab.entries)
    }

    @Test
    fun routes_mapBackToTheirTab() {
        TopLevelTab.entries.forEach { tab ->
            assertEquals(tab, tab.startRoute.tab)
            assertEquals(tab, tab.graph.tab)
        }
    }

    @Test
    fun routes_areDistinct() {
        val tabs = TopLevelTab.entries
        assertEquals(tabs.size, tabs.map { it.graph }.toSet().size)
        assertEquals(tabs.size, tabs.map { it.startRoute }.toSet().size)
        tabs.forEach { assertNotEquals(it.graph, it.startRoute) }
    }

    @Test
    fun testTags_matchTheContract() {
        val actual = TopLevelTab.entries.map { it.testTag }
        assertEquals(listOf("nav_tab_diary", "nav_tab_workout", "nav_tab_progress"), actual)
        assertFalse("nav_tab_profile" in actual)
        actual.forEach { assertTrue(tags.matches(it), it) }
    }

    @Test
    fun labelsAndIcons_areDistinctPerTab() {
        val tabs = TopLevelTab.entries
        assertEquals(tabs.size, tabs.map { it.label }.toSet().size)
        assertEquals(tabs.size, tabs.map { it.icon }.toSet().size)
    }

    @Test
    fun selectedIcon_onlyDiaryHasAFilledVariant() {
        assertEquals(Res.drawable.ic_nav_diary_filled, TopLevelTab.Diary.selectedIcon)
        assertNotEquals(TopLevelTab.Diary.icon, TopLevelTab.Diary.selectedIcon)
        assertEquals(TopLevelTab.Workout.icon, TopLevelTab.Workout.selectedIcon)
        assertEquals(TopLevelTab.Progress.icon, TopLevelTab.Progress.selectedIcon)
    }

    @Test
    fun everyRoute_hasAUniqueSerializer() {
        val names =
            listOf(
                serializer<DiaryGraph>(),
                serializer<WorkoutGraph>(),
                serializer<ProgressGraph>(),
                serializer<DiaryHome>(),
                serializer<WorkoutHome>(),
                serializer<ProgressHome>(),
                serializer<ProfileRoute>(),
            ).map { it.descriptor.serialName }
        assertEquals(7, names.toSet().size)
    }
}
