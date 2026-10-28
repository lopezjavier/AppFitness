package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Perfil "Actividad" row opens the screen; its empty-state action leads to Peso. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class ActivityNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val profileSlot: ProfileSlot = { modifier, _, _, _, _, _, _, _, onOpenActivity ->
        Box(modifier.fillMaxSize().testTag("profile_screen")) {
            Box(Modifier.size(48.dp).testTag("profile_open_activity").clickable(onClick = onOpenActivity))
        }
    }
    private val activitySlot: ActivitySlot = { modifier, onOpenWeight, onBack ->
        AppScreen("Actividad", modifier.testTag("activity_screen"), onBack = onBack, testTagPrefix = "activity") {
            Box(Modifier.size(48.dp).testTag("activity_open_weight").clickable(onClick = onOpenWeight))
        }
    }
    private val weightSlot: WeightSlot = { modifier, _, _, _ -> Box(modifier.fillMaxSize().testTag("weight_screen")) }

    private fun showShell() {
        rule.setContent {
            AppTheme {
                AppScaffold(
                    navController = rememberNavController(),
                    profile = profileSlot,
                    activity = activitySlot,
                    weight = weightSlot,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun perfilRow_opensActividad_withoutTheBottomBar_andBackReturnsToPerfil() {
        showShell()
        rule.onNodeWithTag("nav_tab_profile").performClick()
        rule.onNodeWithTag("profile_open_activity").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("activity_screen").assertExists()
        rule.onNodeWithTag("activity_title").assertTextEquals("Actividad")
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()

        rule.onNodeWithTag("activity_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("profile_screen").assertExists()
    }

    @Test
    fun emptyStateAction_opensPeso() {
        showShell()
        rule.onNodeWithTag("nav_tab_profile").performClick()
        rule.onNodeWithTag("profile_open_activity").performClick()
        rule.onNodeWithTag("activity_open_weight").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("weight_screen").assertExists()
    }
}
