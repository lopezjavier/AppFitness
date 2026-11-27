package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.app.AppScaffold
import io.github.lopezjavier.appfitness.app.CircumferenceSiteSlot
import io.github.lopezjavier.appfitness.app.ProfileSlot
import io.github.lopezjavier.appfitness.app.WeightHistorySlot
import io.github.lopezjavier.appfitness.app.WeightSlot
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-25 entry: Perfil > Estadísticas > Peso opens a full-screen destination and back returns to Perfil. T-324: a
 * circumference site row opens that site's screen, which survives recreation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class WeightNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val profileSlot: ProfileSlot = { modifier, actions ->
        Box(modifier.fillMaxSize().testTag("profile_screen")) {
            Box(Modifier.size(48.dp).testTag("profile_weight_row").clickable(onClick = actions.onOpenWeight))
        }
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private val waistRows = listOf(
        circumferenceRow("w1", CircumferenceSite.Waist, 86.5, "2026-09-20T08:30:00Z"),
        circumferenceRow("w2", CircumferenceSite.Waist, 84.5),
    )

    private val weightSlot: WeightSlot = { modifier, _, onOpenHistory, onBack, onOpenSite ->
        Box(modifier.fillMaxSize()) {
            WeightScreen(
                state = WeightUiState.Loading,
                onEvent = {},
                circumferences = CircumferencesUiState.Loading,
                onCircumferenceEvent = {},
                onOpenHistory = onOpenHistory,
                onBack = onBack,
            )
            CircumferenceSection(
                circumferenceSectionState(waistRows),
                LocalDate(2026, 10, 21),
                {},
                Modifier.align(Alignment.BottomCenter),
                onOpenSite = onOpenSite,
            )
        }
    }

    private var openedSite: String? = null
    private var siteBacks = 0

    private val siteSlot: CircumferenceSiteSlot = { modifier, site, onBack ->
        openedSite = site
        val parsed = CircumferenceSite.valueOf(site)
        CircumferenceSiteScreen(
            site = parsed,
            state = circumferenceSiteState(parsed, waistRows),
            today = LocalDate(2026, 10, 21),
            onEvent = {},
            onBack = {
                siteBacks++
                onBack()
            },
            modifier = modifier,
            timeZone = TimeZone.UTC,
        )
    }

    private val historySlot: WeightHistorySlot = { modifier, onBack ->
        WeightHistoryScreen(WeightUiState.Loading, {}, onBack, modifier)
    }

    @Composable
    private fun Shell() {
        AppTheme {
            AppScaffold(
                navController = rememberNavController(),
                profile = profileSlot,
                weight = weightSlot,
                weightHistory = historySlot,
                circumferenceSite = siteSlot,
            )
        }
    }

    private fun open(tester: StateRestorationTester? = null) {
        if (tester == null) rule.setContent { Shell() } else tester.setContent { Shell() }
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

    @Test
    fun circumferenceSiteCell_opensTheSiteScreenWithItsName_andBackReturnsToPeso() {
        open()

        tap("circumference_site_cell_waist")

        assertEquals("Waist", openedSite)
        rule.onNodeWithTag("circumference_site_title").assertTextEquals("Cintura")
        rule.onNodeWithTag("weight_screen").assertDoesNotExist()
        rule.onNodeWithTag("nav_tab_profile").assertDoesNotExist()

        tap("circumference_site_back")

        assertEquals(1, siteBacks)
        rule.onNodeWithTag("weight_screen").assertExists()
        rule.onNodeWithTag("circumference_site_screen").assertDoesNotExist()
    }

    @Test
    fun openSiteScreen_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        open(tester)
        tap("circumference_site_cell_waist")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("circumference_site_screen").assertExists()
        rule.onNodeWithTag("circumference_site_title").assertTextEquals("Cintura")
        rule.onNodeWithTag("weight_screen").assertDoesNotExist()
    }
}
