package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary_filled
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.nav_workout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppNavigationTest {
    private val withFilled =
        AppNavigationItem(
            Res.string.nav_diary,
            Res.drawable.ic_nav_diary,
            "nav_tab_diary",
            Res.drawable.ic_nav_diary_filled,
        )
    private val withoutFilled =
        AppNavigationItem(Res.string.nav_workout, Res.drawable.ic_nav_workout, "nav_tab_workout")

    @Test
    fun iconFor_itemWithFilledVariant_switchesWhenSelected() {
        assertEquals(Res.drawable.ic_nav_diary_filled, withFilled.iconFor(selected = true))
        assertEquals(Res.drawable.ic_nav_diary, withFilled.iconFor(selected = false))
    }

    @Test
    fun iconFor_itemWithoutFilledVariant_keepsTheSameIcon() {
        assertEquals(Res.drawable.ic_nav_workout, withoutFilled.iconFor(selected = true))
        assertEquals(Res.drawable.ic_nav_workout, withoutFilled.iconFor(selected = false))
    }

    @Test
    fun colors_haveNoPill_limeSelected_greyUnselected() {
        val colors = AppNavigationDefaults.colors(KineticDarkColors)
        assertEquals(Color.Transparent, colors.indicator)
        assertEquals(KineticDarkColors.neonLime, colors.selected)
        assertEquals(KineticDarkColors.textSecondary, colors.unselected)
    }

    @Test
    fun labelStyle_selectedIsExtraBold_unselectedIsSemiBold() {
        val base = appTypography(FontFamily.Default).labelTechnical
        val selected = AppNavigationDefaults.labelStyle(base, selected = true)
        val unselected = AppNavigationDefaults.labelStyle(base, selected = false)
        assertEquals(FontWeight.W800, selected.fontWeight)
        assertEquals(FontWeight.W600, unselected.fontWeight)
        assertEquals(base.fontSize, selected.fontSize)
        assertEquals(base.letterSpacing, selected.letterSpacing)
    }

    @Test
    fun contentHeight_andIconSize_matchTheMockup() {
        assertEquals(64.dp, AppNavigationDefaults.ContentHeight)
        assertEquals(22.dp, AppNavigationDefaults.IconSize)
    }

    @Test
    fun contentHeight_keepsTheItemAboveTheMinimumTouchTarget() {
        assertTrue(AppNavigationDefaults.ContentHeight >= 48.dp)
    }
}
