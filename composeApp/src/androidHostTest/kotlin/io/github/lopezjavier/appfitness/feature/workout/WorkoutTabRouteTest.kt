package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.lopezjavier.appfitness.app.AppScaffold
import io.github.lopezjavier.appfitness.app.RoutinesSlot
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-11 "Continuing after closing the app" and the shell: the Entreno tab, the live route and the bottom bar. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class WorkoutTabRouteTest {
    @get:Rule
    val rule = createComposeRule()

    private var entry by mutableStateOf<WorkoutEntryState>(WorkoutEntryState.Routines)
    private var openedLive = 0
    private var openedHistory = 0
    private lateinit var nav: NavHostController

    private val routinesSlot: RoutinesSlot = { modifier, _, onOpenLive, onOpenHistory ->
        WorkoutEntryHost(entry, onOpenLive, onOpenHistory, modifier) { routinesModifier, openLive ->
            Box(routinesModifier.fillMaxSize().testTag("routines_screen")) {
                Box(Modifier.size(48.dp).testTag("routines_start_stub").clickable(onClick = openLive))
            }
        }
    }

    private fun showShell() {
        rule.setContent {
            AppTheme {
                nav = rememberNavController()
                AppScaffold(
                    navController = nav,
                    routines = { modifier, builder, live, history ->
                        routinesSlot(modifier, builder, {
                            openedLive++
                            live()
                        }) {
                            openedHistory++
                            history()
                        }
                    },
                    history = { modifier, _, _ -> Box(modifier.fillMaxSize().testTag("history_screen")) },
                    liveSession = { modifier, host ->
                        Box(modifier.fillMaxSize().testTag("live_screen")) {
                            Box(Modifier.size(48.dp).testTag("live_stub_back").clickable(onClick = host.onClose))
                        }
                    },
                )
            }
        }
        rule.onNodeWithTag("nav_tab_workout").performClick()
        rule.waitForIdle()
    }

    @Test
    fun entrenoWithoutASession_showsMisRutinas() {
        showShell()

        rule.onNodeWithTag("routines_screen").assertExists()
        rule.onNodeWithTag("workout_continue").assertDoesNotExist()
        rule.onNodeWithTag("live_screen").assertDoesNotExist()
    }

    @Test
    fun entrenoWithASessionInProgress_opensTheLiveScreen_withoutTheBottomBar() {
        entry = WorkoutEntryState.Live
        showShell()

        rule.onNodeWithTag("live_screen").assertExists()
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()
    }

    @Test
    fun backFromTheLiveScreen_keepsTheSessionAndOffersToContinue_withoutReopeningIt() {
        entry = WorkoutEntryState.Live
        showShell()

        rule.onNodeWithTag("live_stub_back").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("routines_screen").assertExists()
        rule.onNodeWithTag("live_screen").assertDoesNotExist()
        rule.onNodeWithTag("workout_continue").assertExists()
        rule.onNodeWithTag("nav_bar").assertExists()
    }

    @Test
    fun continueCard_reopensTheLiveScreen() {
        entry = WorkoutEntryState.Live
        showShell()
        rule.onNodeWithTag("live_stub_back").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("workout_continue").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("live_screen").assertExists()
    }

    @Test
    fun afterTheSessionEnds_theNextOneIsOpenedAgain() {
        entry = WorkoutEntryState.Live
        showShell()
        rule.onNodeWithTag("live_stub_back").performClick()
        rule.waitForIdle()

        entry = WorkoutEntryState.Routines
        rule.waitForIdle()
        entry = WorkoutEntryState.Live
        rule.waitForIdle()

        rule.onNodeWithTag("live_screen").assertExists()
    }

    @Test
    fun startingFromMisRutinas_opensTheLiveScreenOnce() {
        showShell()

        rule.onNodeWithTag("routines_start_stub").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("live_screen").assertExists()
        assertEquals(1, openedLive)
    }

    @Test
    fun historialButton_isVisibleInEveryState_andOpensTheHistory() {
        entry = WorkoutEntryState.Routines
        showShell()

        rule.onNodeWithTag("workout_history").assertExists()
        rule.onNodeWithTag("workout_history").performClick()
        rule.waitForIdle()

        assertEquals(1, openedHistory)
        rule.onNodeWithTag("history_screen").assertExists()
        rule.onNodeWithTag("nav_bar").assertDoesNotExist()
    }

    @Test
    fun historialButton_staysVisibleWhileASessionIsInProgress() {
        entry = WorkoutEntryState.Live
        showShell()
        rule.onNodeWithTag("live_stub_back").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("workout_continue").assertExists()
        rule.onNodeWithTag("workout_history").assertExists()
    }
}
