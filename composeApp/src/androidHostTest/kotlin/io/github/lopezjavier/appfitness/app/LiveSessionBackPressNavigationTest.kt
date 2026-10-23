package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryHost
import io.github.lopezjavier.appfitness.feature.workout.WorkoutEntryState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-11: after "Empezar", one back press leaves the live screen and never reopens it (T-097d). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class LiveSessionBackPressNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: NavHostController
    private val entryState = MutableStateFlow<WorkoutEntryState>(WorkoutEntryState.Routines)

    @Test
    fun startThenOneBack_landsOnTheEntrenoTab_evenWhenTheRecheckFindsTheSession() {
        rule.setContent {
            AppTheme {
                nav = rememberNavController()
                NavHost(nav, startDestination = WorkoutHome) {
                    composable<WorkoutHome> {
                        val state by entryState.collectAsState()
                        WorkoutEntryHost(
                            state = state,
                            onOpenLiveSession = { nav.navigateToFullScreen(LiveSessionRoute) },
                            onOpenHistory = {},
                        ) { modifier, open ->
                            Box(modifier.size(48.dp).clickable(onClick = open).testTag("routines_start"))
                        }
                    }
                    composable<LiveSessionRoute> { Box(Modifier.fillMaxSize().testTag("live_screen")) }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("routines_start").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("live_screen").assertExists()
        // The view model learns about the session on resume, after the user came back.
        rule.runOnUiThread { nav.popBackStack() }
        rule.waitForIdle()
        entryState.value = WorkoutEntryState.Live
        rule.waitForIdle()

        rule.onNodeWithTag("live_screen").assertDoesNotExist()
        assertEquals(true, nav.currentDestination?.route?.contains("WorkoutHome"))
    }
}
