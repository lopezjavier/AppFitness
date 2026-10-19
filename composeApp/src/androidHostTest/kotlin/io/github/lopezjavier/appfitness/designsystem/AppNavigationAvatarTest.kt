package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_avatar_person
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.profile_title
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-26: the Perfil item of the navigation shows the user's avatar instead of a glyph. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppNavigationAvatarTest {
    @get:Rule
    val rule = createComposeRule()

    private val items =
        listOf(
            AppNavigationItem(Res.string.nav_diary, Res.drawable.ic_nav_diary, "nav_tab_diary"),
            AppNavigationItem(
                Res.string.profile_title,
                Res.drawable.ic_avatar_person,
                "nav_tab_profile",
                showsAvatar = true,
            ),
        )

    private fun show(selected: Int) {
        rule.setContent { AppTheme { AppNavigation(items, selected, onItemSelect = {}) } }
        rule.waitForIdle()
    }

    @Test
    fun profileItem_showsThePlaceholder_whenThereIsNoPhoto() {
        show(0)
        rule.onNodeWithTag("nav_tab_profile_avatar_placeholder", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("nav_tab_profile_avatar_image", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun profileItem_labelIsTheAccessibleName_andSelectionComesFromTheTab() {
        show(1)
        rule.onNodeWithTag("nav_tab_profile").assertContentDescriptionEquals("Perfil").assertIsSelected()
    }

    @Test
    fun selectedProfile_drawsTheRing_byInsettingThePhoto() {
        var selected by mutableIntStateOf(0)
        rule.setContent { AppTheme { AppNavigation(items, selected, onItemSelect = {}) } }
        rule.waitForIdle()
        val tag = "nav_tab_profile_avatar_placeholder"
        val unselected = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().width
        selected = 1
        rule.waitForIdle()
        val ringed = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().width
        assertTrue("$ringed < $unselected", ringed < unselected)
    }
}
