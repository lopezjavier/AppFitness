package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

/** The Perfil "Acerca de" row opens the About screen, without bottom bar, and back returns to Perfil. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AboutNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val profileSlot: ProfileSlot = { modifier, _, _, _, _, _, _, onOpenAbout ->
        Box(modifier.fillMaxSize().testTag("profile_screen")) {
            Box(Modifier.size(48.dp).testTag("profile_about_row").clickable(onClick = onOpenAbout))
        }
    }
    private val aboutSlot: AboutSlot = { modifier, onBack, onOpenDiagnostics ->
        AppScreen("Acerca de", modifier.testTag("about_screen"), onBack = onBack, testTagPrefix = "about") {
            Box(Modifier.size(48.dp).testTag("about_health_diagnostics_row").clickable(onClick = onOpenDiagnostics))
        }
    }
    private val diagnosticsSlot: HealthDiagnosticsSlot = { modifier, onBack ->
        AppScreen(
            "Diagnóstico",
            modifier.testTag("health_diagnostics_screen"),
            onBack = onBack,
            testTagPrefix = "health_diagnostics",
        ) {}
    }

    @Test
    fun perfilRow_opensAcercaDe_andBackReturnsToPerfil() {
        rule.setContent {
            AppTheme { AppScaffold(navController = rememberNavController(), profile = profileSlot, about = aboutSlot) }
        }
        rule.onNodeWithTag("nav_tab_profile").performClick()
        rule.onNodeWithTag("profile_about_row").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("about_screen").assertExists()
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()

        rule.onNodeWithTag("about_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("profile_screen").assertExists()
    }

    @Test
    fun diagnosticsRow_opensTheDiagnostic_andBackReturnsToAcercaDe() {
        rule.setContent {
            AppTheme {
                AppScaffold(
                    navController = rememberNavController(),
                    profile = profileSlot,
                    about = aboutSlot,
                    healthDiagnostics = diagnosticsSlot,
                )
            }
        }
        rule.onNodeWithTag("nav_tab_profile").performClick()
        rule.onNodeWithTag("profile_about_row").performClick()
        rule.onNodeWithTag("about_health_diagnostics_row").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("health_diagnostics_screen").assertExists()
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()

        rule.onNodeWithTag("health_diagnostics_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("about_screen").assertExists()
    }
}
