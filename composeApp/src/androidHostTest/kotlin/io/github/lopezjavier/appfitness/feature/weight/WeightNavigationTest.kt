package io.github.lopezjavier.appfitness.feature.weight

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
import io.github.lopezjavier.appfitness.app.AppScaffold
import io.github.lopezjavier.appfitness.app.ProfileSlot
import io.github.lopezjavier.appfitness.app.WeightHistorySlot
import io.github.lopezjavier.appfitness.app.WeightSlot
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-25 entry: Perfil > Estadísticas > Peso opens a full-screen destination and back returns to Perfil. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class WeightNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val profileSlot: ProfileSlot = { modifier, _, _, _, _, onOpenWeight, _, _ ->
        Box(modifier.fillMaxSize().testTag("profile_screen")) {
            Box(Modifier.size(48.dp).testTag("profile_weight_row").clickable(onClick = onOpenWeight))
        }
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private val weightSlot: WeightSlot = { modifier, _, onOpenHistory, onBack ->
        WeightScreen(
            state = WeightUiState.Loading,
            onEvent = {},
            circumferences = CircumferencesUiState.Loading,
            onCircumferenceEvent = {},
            modifier = modifier,
            onOpenHistory = onOpenHistory,
            onBack = onBack,
        )
    }

    private val historySlot: WeightHistorySlot = { modifier, onBack ->
        WeightHistoryScreen(WeightUiState.Loading, {}, onBack, modifier)
    }

    private fun open() {
        rule.setContent {
            AppTheme {
                AppScaffold(
                    navController = rememberNavController(),
                    profile = profileSlot,
                    weight = weightSlot,
                    weightHistory = historySlot,
                )
            }
        }
        rule.waitForIdle()
        tap("nav_tab_profile")
        tap("profile_weight_row")
    }

    @Test
    fun weightRow_opensThePesoScreenWithItsOwnBar_andBackReturnsToPerfil() {
        open()

        rule.onNodeWithTag("weight_screen").assertExists()
        rule.onNodeWithTag("weight_title").assertTextEquals("Peso")
        rule.onNodeWithTag("nav_tab_profile").assertDoesNotExist()

        tap("weight_back")

        rule.onNodeWithTag("profile_screen").assertExists()
        rule.onNodeWithTag("weight_screen").assertDoesNotExist()
    }

    @Test
    fun calendarIcon_opensHistorialDePeso_andBackReturnsToPeso() {
        open()

        tap("weight_action_1")

        rule.onNodeWithTag("weight_history_screen").assertExists()
        rule.onNodeWithTag("weight_history_title").assertTextEquals("Historial de peso")
        rule.onNodeWithTag("weight_screen").assertDoesNotExist()

        tap("weight_history_back")

        rule.onNodeWithTag("weight_screen").assertExists()
        rule.onNodeWithTag("weight_history_screen").assertDoesNotExist()
    }
}
