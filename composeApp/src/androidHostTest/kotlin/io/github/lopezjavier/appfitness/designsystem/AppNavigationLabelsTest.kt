package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.sp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_profile
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_trainer
import io.github.lopezjavier.appfitness.composeapp.resources.ic_nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.nav_trainer
import io.github.lopezjavier.appfitness.composeapp.resources.nav_workout
import io.github.lopezjavier.appfitness.composeapp.resources.profile_title
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** C-16: the navigation labels are sentence case (bar and rail) and use the 12 sp `bodySmall` base. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppNavigationLabelsTest {
    @get:Rule
    val rule = createComposeRule()

    private val items = listOf(
        AppNavigationItem(Res.string.nav_diary, Res.drawable.ic_nav_diary, "nav_tab_diary"),
        AppNavigationItem(Res.string.nav_workout, Res.drawable.ic_nav_workout, "nav_tab_workout"),
        AppNavigationItem(Res.string.nav_trainer, Res.drawable.ic_nav_trainer, "nav_tab_trainer"),
        AppNavigationItem(Res.string.profile_title, Res.drawable.ic_nav_profile, "nav_tab_profile"),
    )

    private fun show(type: AppNavigationType) {
        rule.setContent {
            AppTheme { Navigation(type) }
        }
    }

    @Composable
    private fun Navigation(type: AppNavigationType) {
        AppNavigation(items, selectedIndex = 1, onItemSelect = {}, type = type, windowInsets = WindowInsets(0))
    }

    private fun assertSentenceCaseLabels() {
        listOf("Diario", "Entreno", "Trainer", "Perfil").forEach {
            rule.onNodeWithContentDescription(it).assertExists()
            rule.onAllNodesWithContentDescription(it.uppercase()).assertCountEquals(0)
        }
        rule.onNodeWithTag("nav_tab_workout").assertIsSelected()
    }

    @Test
    fun bar_showsSentenceCaseLabels_andMarksTheSelectedTab() {
        show(AppNavigationType.Bar)

        assertSentenceCaseLabels()
    }

    @Test
    fun rail_showsSentenceCaseLabels_andMarksTheSelectedTab() {
        show(AppNavigationType.Rail)

        assertSentenceCaseLabels()
    }

    // The bar and the rail draw their label with NavLabel, whose default modifier hides the text from semantics.
    @Test
    fun navLabel_rendersTheVisibleTextInSentenceCase() {
        rule.setContent {
            AppTheme { Column { items.forEach { NavLabel(it, selected = false, modifier = Modifier) } } }
        }

        listOf("Diario", "Entreno", "Trainer", "Perfil").forEach {
            rule.onNodeWithText(it, useUnmergedTree = true).assertExists()
            rule.onAllNodesWithText(it.uppercase(), useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Test
    fun labelBase_isBodySmallAt12sp() {
        var base: androidx.compose.ui.text.TextStyle? = null
        rule.setContent { AppTheme { base = AppTheme.typography.bodySmall } }

        assertEquals(12.sp, base!!.fontSize)
    }
}
