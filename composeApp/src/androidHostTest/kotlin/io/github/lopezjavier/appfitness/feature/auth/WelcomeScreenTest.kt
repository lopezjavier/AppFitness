package io.github.lopezjavier.appfitness.feature.auth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppLogoDefaults
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val MIN_TOUCH_DP = 48f
private const val MIN_PRIMARY_DP = 52f

/** US-05 Welcome scenarios as the screen shows them: actions, error copy, in-flight state, sizes and a11y. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario and accessibility contract.
class WelcomeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<WelcomeEvent>()

    private fun show(state: WelcomeUiState = WelcomeUiState(), showTestLogin: Boolean = false) {
        events.clear()
        rule.setContent {
            AppTheme { WelcomeScreen(state = state, showTestLogin = showTestLogin, onEvent = { events += it }) }
        }
    }

    @Test
    fun idle_showsBrandAndGoogleButton_noErrorNoTagline() {
        show()
        rule.onNodeWithTag("welcome_screen").assertExists()
        rule.onNodeWithTag("welcome_title", useUnmergedTree = true).assertTextEquals("AppFitness")
        rule.onNodeWithTag("welcome_google_button").assertTextEquals("Continuar con Google").assertIsEnabled()
        rule.onNodeWithTag("welcome_error_message").assertDoesNotExist()
        rule.onAllNodesWithText("Calorías, peso y gimnasio en un solo sitio").assertCountEquals(0)
    }

    @Test
    fun testLoginButton_absentWhenNotAvailable() {
        show(showTestLogin = false)
        rule.onNodeWithTag("welcome_test_login_button").assertDoesNotExist()
    }

    @Test
    fun testLoginButton_presentWhenAvailable_andSendsTheEvent() {
        show(showTestLogin = true)
        rule.onNodeWithTag("welcome_test_login_button").assertTextEquals("Test login").performClick()
        assertTrue(events == listOf(WelcomeEvent.TestLogin))
    }

    @Test
    fun googleButton_sendsTheEvent() {
        show()
        rule.onNodeWithTag("welcome_google_button").performClick()
        assertTrue(events == listOf(WelcomeEvent.ContinueWithGoogle))
    }

    @Test
    fun noNetworkError_showsMessageOnly_andTheGoogleButtonRetries() {
        show(WelcomeUiState(error = WelcomeError.NoNetwork))
        rule.onNodeWithText("Sin conexión: no se puede iniciar sesión.").assertExists()
        rule.onAllNodesWithText("Reintentar").assertCountEquals(0)
        rule.onNodeWithTag("welcome_google_button").assertIsEnabled().performClick()
        assertTrue(events == listOf(WelcomeEvent.ContinueWithGoogle))
    }

    @Test
    fun genericError_showsItsOwnMessage() {
        show(WelcomeUiState(error = WelcomeError.Generic))
        rule.onNodeWithText("No se pudo iniciar sesión. Inténtalo de nuevo.").assertExists()
    }

    @Test
    fun errorBlock_isAPoliteLiveRegion() {
        show(WelcomeUiState(error = WelcomeError.NoNetwork))
        rule.onNodeWithTag("welcome_error_message").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
    }

    @Test
    fun cancelledPicker_showsNothing() {
        // The ViewModel maps a cancellation to the idle state: no message and no retry.
        show(WelcomeUiState(inFlight = false, error = null))
        rule.onNodeWithTag("welcome_error_message").assertDoesNotExist()
    }

    @Test
    fun inFlight_disablesBothButtons_andAnnouncesSigningIn() {
        show(WelcomeUiState(inFlight = true), showTestLogin = true)
        rule.onNodeWithTag("welcome_google_button").assertIsNotEnabled().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Iniciando sesión"),
        )
        rule.onNodeWithTag("welcome_test_login_button").assertIsNotEnabled()
    }

    @Test
    fun touchTargets_meetMinimums() {
        show(showTestLogin = true)
        rule.onNodeWithTag("welcome_google_button").assertHeightIsAtLeast(MIN_PRIMARY_DP.dp)
        rule.onNodeWithTag("welcome_test_login_button").assertHeightIsAtLeast(MIN_TOUCH_DP.dp)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-xxhdpi")
    fun shortWindow_keepsTheGoogleButtonVisible() {
        show(WelcomeUiState(error = WelcomeError.NoNetwork), showTestLogin = true)
        rule.onNodeWithTag("welcome_google_button").assertIsDisplayed()
        rule.onNodeWithTag("welcome_test_login_button").assertIsDisplayed()
    }

    @Test
    fun logo_hasDescription_andCompactSize() {
        show()
        rule.onNodeWithTag("welcome_logo", useUnmergedTree = true)
            .assertContentDescriptionEquals("Logo de AppFitness")
            .assertWidthIsEqualTo(AppLogoDefaults.Width)
            .assertHeightIsEqualTo(AppLogoDefaults.height(AppLogoDefaults.Width))
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun logo_isLargerOnWideWindows_andActionZoneIsCapped() {
        show(showTestLogin = true)
        rule.onNodeWithTag("welcome_logo", useUnmergedTree = true).assertWidthIsEqualTo(AppLogoDefaults.WidthLarge)
        assertEquals(340.dp, AppLogoDefaults.WidthLarge)
        val width = rule.onNodeWithTag("welcome_google_button").getUnclippedBoundsInRoot()
        assertTrue("action zone capped at 360 dp", (width.right - width.left) <= 360.dp + 0.5.dp)
    }

    @Test
    fun brandGroup_isMergedIntoOneNode() {
        show()
        rule.onNodeWithTag("welcome_brand").assertExists()
        rule.onNodeWithTag("welcome_brand").assert(
            SemanticsMatcher("merged brand reads logo and name") { node ->
                val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }
                val description =
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
                text.contains("AppFitness") && description.contains("Logo de AppFitness")
            },
        )
    }

    @Test
    fun largeFont_buttonsStayReachable() {
        events.clear()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
                AppTheme { WelcomeScreen(WelcomeUiState(), showTestLogin = true, onEvent = { events += it }) }
            }
        }
        rule.onNodeWithTag("welcome_google_button").performClick()
        rule.onNodeWithTag("welcome_test_login_button").performClick()
        assertTrue(events == listOf(WelcomeEvent.ContinueWithGoogle, WelcomeEvent.TestLogin))
    }
}
