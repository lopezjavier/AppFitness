package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppTopBarDefaultsTest {
    @Test
    fun avatarTouchTarget_meetsMinimum() {
        assertTrue(AppTopBarDefaults.AvatarTouchTarget >= 48.dp)
    }

    @Test
    fun avatarSizes_nestInsideTheTouchTarget() {
        assertTrue(AppTopBarDefaults.AvatarSize <= AppTopBarDefaults.AvatarTouchTarget)
        assertTrue(AppTopBarDefaults.AvatarIconSize < AppTopBarDefaults.AvatarSize)
    }

    @Test
    fun height_isTheMinimumBarHeight() {
        assertEquals(56.dp, AppTopBarDefaults.Height)
    }

    @Test
    fun endPadding_alignsTheVisualCircleWithTheScreenMargin() {
        val slack = (AppTopBarDefaults.AvatarTouchTarget - AppTopBarDefaults.AvatarSize) / 2
        assertEquals(Spacing.screenMargin, AppTopBarDefaults.EndPadding + slack)
    }

    @Test
    fun testTags_matchTheContract() {
        val tags = listOf(AppTopBarDefaults.TestTag, AppTopBarDefaults.TitleTestTag, AppTopBarDefaults.AvatarTestTag)
        assertEquals(listOf("topbar", "topbar_title", "topbar_avatar"), tags)
        val pattern = Regex("^[a-z]+(_[a-z0-9]+)*$")
        tags.forEach { assertTrue(pattern.matches(it), it) }
    }
}
