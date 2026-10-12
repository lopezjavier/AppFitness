package io.github.lopezjavier.appfitness.app

import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TopLevelTabTest {
    private val tags = Regex("^[a-z]+(_[a-z0-9]+)*$")

    @Test
    fun start_isDiary() {
        assertEquals(TopLevelTab.Diary, TopLevelTab.Start)
    }

    @Test
    fun avatarTarget_isProfile() {
        assertEquals(TopLevelTab.Profile, TopLevelTab.AvatarTarget)
    }

    @Test
    fun entries_followTheBarOrder() {
        assertEquals(
            listOf(TopLevelTab.Diary, TopLevelTab.Workout, TopLevelTab.Progress, TopLevelTab.Profile),
            TopLevelTab.entries,
        )
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
        assertEquals(
            listOf("nav_tab_diary", "nav_tab_workout", "nav_tab_progress", "nav_tab_profile"),
            TopLevelTab.entries.map { it.testTag },
        )
        TopLevelTab.entries.forEach { assertTrue(tags.matches(it.testTag), it.testTag) }
    }

    @Test
    fun labelsAndIcons_areDistinctPerTab() {
        val tabs = TopLevelTab.entries
        assertEquals(tabs.size, tabs.map { it.label }.toSet().size)
        assertEquals(tabs.size, tabs.map { it.icon }.toSet().size)
    }

    @Test
    fun everyRoute_hasAUniqueSerializer() {
        val routes = TopLevelTab.entries.flatMap { listOf(it.graph, it.startRoute) }
        val names =
            routes.map { route ->
                when (route) {
                    is DiaryGraph -> serializer<DiaryGraph>().descriptor.serialName
                    is WorkoutGraph -> serializer<WorkoutGraph>().descriptor.serialName
                    is ProgressGraph -> serializer<ProgressGraph>().descriptor.serialName
                    is ProfileGraph -> serializer<ProfileGraph>().descriptor.serialName
                    is DiaryHome -> serializer<DiaryHome>().descriptor.serialName
                    is WorkoutHome -> serializer<WorkoutHome>().descriptor.serialName
                    is ProgressHome -> serializer<ProgressHome>().descriptor.serialName
                    is ProfileHome -> serializer<ProfileHome>().descriptor.serialName
                }
            }
        assertEquals(routes.size, names.toSet().size)
    }
}
