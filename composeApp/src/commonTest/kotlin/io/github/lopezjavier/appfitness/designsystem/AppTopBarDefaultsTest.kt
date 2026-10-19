package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppTopBarDefaultsTest {
    @Test
    fun height_isTheMinimumBarHeight() {
        assertEquals(56.dp, AppTopBarDefaults.Height)
    }

    @Test
    fun navigationTouchTarget_meetsMinimum() {
        assertTrue(AppTopBarDefaults.NavigationTouchTarget >= 48.dp)
    }

    @Test
    fun navigationIcon_nestsInsideTheTouchTarget() {
        assertTrue(AppTopBarDefaults.NavigationIconSize <= AppTopBarDefaults.NavigationTouchTarget)
    }

    @Test
    fun startPaddingWithNavigation_alignsTheGlyphBoxWithTheScreenMargin() {
        val slack = (AppTopBarDefaults.NavigationTouchTarget - AppTopBarDefaults.NavigationIconSize) / 2
        assertEquals(Spacing.screenMargin, AppTopBarDefaults.StartPaddingWithNavigation + slack)
    }

    @Test
    fun testTags_matchTheContract() {
        val tags =
            listOf(
                AppTopBarDefaults.TestTag,
                AppTopBarDefaults.TitleTestTag,
                AppTopBarDefaults.BackTestTag,
            )
        assertEquals(listOf("topbar", "topbar_title", "topbar_back"), tags)
        val pattern = Regex("^[a-z]+(_[a-z0-9]+)*$")
        tags.forEach { assertTrue(pattern.matches(it), it) }
    }

    @Test
    fun defaultPrefix_keepsLegacyTags() {
        assertEquals("topbar_title", AppTopBarDefaults.titleTag(AppTopBarDefaults.TestTag))
        assertEquals("topbar_back", AppTopBarDefaults.backTag(AppTopBarDefaults.TestTag))
        assertEquals("catalog_topbar_full_back", AppTopBarDefaults.backTag("catalog_topbar_full"))
    }
}
