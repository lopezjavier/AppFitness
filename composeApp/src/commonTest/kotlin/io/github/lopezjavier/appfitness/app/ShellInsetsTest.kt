package io.github.lopezjavier.appfitness.app

import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppNavigationType
import kotlin.test.Test
import kotlin.test.assertEquals

class ShellInsetsTest {
    @Test
    fun bar_keepsInnerStartAndBarHeight() {
        val insets = tabContentInsets(AppNavigationType.Bar, 64.dp, 80.dp, 4.dp, 10.dp)
        assertEquals(TabContentInsets(start = 4.dp, bottom = 64.dp), insets)
    }

    @Test
    fun rail_startIsRailWidth_noBarGap() {
        val insets = tabContentInsets(AppNavigationType.Rail, 64.dp, 96.dp, 4.dp, 10.dp)
        assertEquals(TabContentInsets(start = 96.dp, bottom = 10.dp), insets)
    }

    @Test
    fun rail_withoutSystemInset_bottomIsZero() {
        val insets = tabContentInsets(AppNavigationType.Rail, 64.dp, 80.dp, 0.dp, 0.dp)
        assertEquals(0.dp, insets.bottom)
    }
}
