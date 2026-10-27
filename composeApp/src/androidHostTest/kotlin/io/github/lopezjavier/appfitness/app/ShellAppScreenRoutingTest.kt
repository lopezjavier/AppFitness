package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppMessages
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * US-56 same structure on every screen: a route on `AppScreen` (the catalog) draws its own bar and the shell draws
 * none; every other route keeps the shell's bar. The shell's message queue outlives the destination that sent it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ShellAppScreenRoutingTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: NavHostController
    private lateinit var messages: AppMessages

    private fun show(isOffline: Boolean = false) {
        rule.setContent {
            AppTheme {
                nav = rememberNavController()
                val scope = rememberCoroutineScope()
                messages = androidx.compose.runtime.remember { AppMessages(scope) }
                AppScaffold(
                    navController = nav,
                    features = AppFeatures(componentCatalog = true),
                    isOffline = isOffline,
                    messages = messages,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun open(route: AppRoute) {
        rule.runOnUiThread { nav.navigateToFullScreen(route) }
        rule.waitForIdle()
    }

    @Test
    fun catalogRoute_drawsItsOwnBar_andTheShellDrawsNone() {
        show()
        open(CatalogRoute)
        rule.onNodeWithTag("catalog_bar_title").assertTextEquals("Catálogo de componentes")
        rule.onNodeWithTag("topbar").assertDoesNotExist()
        rule.onNodeWithTag("catalog_screen").assertExists()
    }

    @Test
    fun catalogBack_popsToTheTab() {
        show()
        open(CatalogRoute)
        rule.onNodeWithTag("catalog_bar_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("catalog_screen").assertDoesNotExist()
        rule.onNodeWithTag("diary_screen").assertExists()
        rule.onNodeWithTag("nav_bar").assertExists()
    }

    @Test
    fun preferencesHub_drawsNoShellBar_andHasNoBottomBar() {
        show()
        open(PreferencesRoute)
        rule.onNodeWithTag("topbar").assertDoesNotExist()
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()
    }

    @Test
    fun perfilRoot_drawsItsOwnHeader_butKeepsTheBottomBar() {
        show()
        rule.onNodeWithTag("nav_tab_profile").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("topbar").assertDoesNotExist()
        rule.onNodeWithTag("nav_bar").assertExists()
    }

    @Test
    fun diaryTabRoot_drawsItsOwnHeader_butKeepsTheBottomBar() {
        show()
        rule.onNodeWithTag("topbar").assertDoesNotExist()
        rule.onNodeWithTag("diary_screen").assertExists()
        rule.onNodeWithTag("nav_bar").assertExists()
    }

    @Test
    fun offlineBanner_sitsAboveTheCatalogBar() {
        show(isOffline = true)
        open(CatalogRoute)
        val banner = rule.onNodeWithTag("offline_banner").getUnclippedBoundsInRoot()
        val bar = rule.onNodeWithTag("catalog_bar").getUnclippedBoundsInRoot()
        assertTrue(bar.top >= banner.bottom - 0.5.dp, "bar top ${bar.top} banner bottom ${banner.bottom}")
    }

    @Test
    fun message_isShownOnTheShellAndSurvivesNavigatingAway() {
        show()
        open(PreferencesRoute)
        rule.runOnUiThread { messages.show("Guardado") }
        rule.waitForIdle()
        rule.onNodeWithTag("app_message").assertIsDisplayed().assertTextEquals("Guardado")

        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()

        rule.onNodeWithTag("app_message").assertIsDisplayed().assertTextEquals("Guardado")
    }

    @Test
    fun message_sitsAboveTheBottomBar() {
        show()
        rule.runOnUiThread { messages.show("Guardado") }
        rule.waitForIdle()
        val message = rule.onNodeWithTag("app_message").getUnclippedBoundsInRoot()
        val bar = rule.onNodeWithTag("nav_bar").getUnclippedBoundsInRoot()
        assertTrue(message.bottom <= bar.top + 0.5.dp, "message bottom ${message.bottom} bar top ${bar.top}")
    }
}
