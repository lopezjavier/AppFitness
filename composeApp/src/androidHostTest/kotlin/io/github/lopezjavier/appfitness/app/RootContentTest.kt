package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-05: the root of the app follows the session (Welcome / loading / onboarding stub / Main). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class RootContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val welcomeStub: @androidx.compose.runtime.Composable () -> Unit = {
        Box(Modifier.fillMaxSize().testTag("welcome_screen"))
    }

    private val onboardingStub: @androidx.compose.runtime.Composable () -> Unit = {
        Box(Modifier.fillMaxSize().testTag("onboarding_screen"))
    }

    @Test
    fun loading_showsSkeleton_neverWelcome() {
        rule.mainClock.autoAdvance = false
        rule.setContent { App(rootRoute = RootRoute.Loading, welcome = welcomeStub) }
        rule.onNodeWithTag("root_loading").assertExists()
        rule.onNodeWithTag("welcome_screen").assertDoesNotExist()
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()
    }

    @Test
    fun welcome_showsTheWelcomeDestinationWithoutShellChrome() {
        rule.setContent { App(rootRoute = RootRoute.Welcome, welcome = welcomeStub) }
        rule.onNodeWithTag("welcome_screen").assertExists()
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()
        rule.onNodeWithTag("topbar").assertDoesNotExist()
    }

    @Test
    fun onboarding_showsStubWithoutNavBarFabOrAvatar() {
        rule.setContent { App(rootRoute = RootRoute.Onboarding, welcome = welcomeStub, onboarding = onboardingStub) }
        rule.onNodeWithTag("onboarding_screen").assertExists()
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()
        rule.onNodeWithTag("fab").assertDoesNotExist()
        rule.onNodeWithTag("topbar_avatar").assertDoesNotExist()
    }

    @Test
    fun profileError_showsRetry_andRetryCallsBack() {
        var retries = 0
        rule.setContent { App(rootRoute = RootRoute.ProfileError, onRetryProfile = { retries++ }) }
        rule.onNodeWithTag("root_error").assertExists()
        rule.onNodeWithTag("root_error_action").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun offline_bannerShowsOnEveryNonShellDestination() {
        var route by mutableStateOf<RootRoute>(RootRoute.Welcome)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            App(isOffline = true, rootRoute = route, welcome = welcomeStub, onboarding = onboardingStub)
        }
        listOf(RootRoute.Welcome, RootRoute.Loading, RootRoute.Onboarding, RootRoute.ProfileError).forEach {
            route = it
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            rule.onNodeWithTag("offline_banner").assertExists()
        }
    }

    @Test
    fun online_noBannerOutsideTheShell() {
        rule.setContent { App(isOffline = false, rootRoute = RootRoute.Welcome, welcome = welcomeStub) }
        rule.onNodeWithTag("offline_banner").assertDoesNotExist()
    }

    @Test
    fun signOutAndSignInAgain_resetsTabsToDiary() {
        var route by mutableStateOf<RootRoute>(RootRoute.Main)
        rule.setContent { App(rootRoute = route, welcome = welcomeStub) }
        rule.onNodeWithTag("nav_tab_workout").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("nav_tab_workout").assertIsSelected()

        route = RootRoute.Welcome
        rule.waitForIdle()
        rule.onNodeWithTag("welcome_screen").assertExists()
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()

        route = RootRoute.Main
        rule.waitForIdle()
        rule.onNodeWithTag("nav_tab_diary").assertIsSelected()
    }

    @Test
    fun signOutFromProfile_confirmingTheDialogCallsBack() {
        var signOuts = 0
        rule.setContent { App(rootRoute = RootRoute.Main, onSignOut = { signOuts++ }) }
        rule.onNodeWithTag("topbar_avatar").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("profile_sign_out_button").performClick()
        rule.onNodeWithTag("sign_out_dialog_confirm").performClick()
        assertEquals(1, signOuts)
    }
}
