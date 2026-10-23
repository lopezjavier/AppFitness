package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-11 "Resuming or discarding from Mis rutinas": the in-progress card with continue and discard. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One scenario per test.
class WorkoutEntryHostTest {
    @get:Rule
    val rule = createComposeRule()

    private val sessions = FakeWorkoutSessionRepository()
    private val connectivity = FakeConnectivityObserver()
    private var openedLive = 0

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()): WorkoutEntryViewModel {
        sessions.session = sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1))),
        )
        return WorkoutEntryViewModel(sessions, connectivity, savedState)
    }

    private fun show(vm: WorkoutEntryViewModel) {
        rule.setContent { AppTheme { Entry(vm) } }
        rule.waitForIdle()
    }

    @Composable
    private fun Entry(vm: WorkoutEntryViewModel) {
        val state by vm.state.collectAsState()
        val asked by vm.discardAsked.collectAsState()
        val discarding by vm.discarding.collectAsState()
        val online by vm.isOnline.collectAsState()
        WorkoutEntryHost(
            state = state,
            onOpenLiveSession = { openedLive++ },
            onOpenHistory = {},
            discardAsked = asked,
            discarding = discarding,
            isOnline = online,
            onEvent = vm::onEvent,
        ) { modifier, _ -> Box(modifier.fillMaxSize()) }
    }

    @Test
    fun card_showsContinueAndDiscard_withSpanishDescriptions_andBigTargets() {
        show(viewModel())

        rule.onNodeWithTag("workout_continue").assert(hasContentDescription("Continuar entreno"))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("workout_discard").assert(hasContentDescription("Descartar entreno"))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun continueButton_opensTheLiveSession() {
        show(viewModel())
        val before = openedLive

        rule.onNodeWithTag("workout_continue").performClick()

        assertEquals(before + 1, openedLive)
    }

    @Test
    fun trash_asksForConfirmation_andConfirmDiscardsAndRemovesTheCard() {
        show(viewModel())

        rule.onNodeWithTag("workout_discard").performClick()
        rule.onNodeWithText("¿Descartar entreno?").assertExists()
        rule.onNodeWithTag("workout_discard_confirm_confirm").performClick()
        rule.waitForIdle()

        assertEquals(listOf("s1"), sessions.discarded)
        rule.onNodeWithTag("workout_continue").assertDoesNotExist()
    }

    @Test
    fun cancel_keepsTheCard_andDiscardsNothing() {
        show(viewModel())

        rule.onNodeWithTag("workout_discard").performClick()
        rule.onNodeWithTag("workout_discard_confirm_cancel").performClick()
        rule.waitForIdle()

        assertEquals(emptyList<String>(), sessions.discarded)
        rule.onNodeWithTag("workout_continue").assertExists()
        rule.onNodeWithText("¿Descartar entreno?").assertDoesNotExist()
    }

    @Test
    fun offline_disablesTheTrash() {
        show(viewModel())
        connectivity.state.value = false
        rule.waitForIdle()

        rule.onNodeWithTag("workout_discard").assertIsNotEnabled()
    }

    @Test
    fun startingFromRoutines_thenBackWithLiveFound_doesNotReopenTheLiveScreen() {
        val state = MutableStateFlow<WorkoutEntryState>(WorkoutEntryState.Routines)
        val tester = StateRestorationTester(rule)
        var openFromRoutines: () -> Unit = {}
        tester.setContent {
            AppTheme {
                val current by state.collectAsState()
                WorkoutEntryHost(
                    state = current,
                    onOpenLiveSession = { openedLive++ },
                    onOpenHistory = {},
                ) { modifier, open ->
                    openFromRoutines = open
                    Box(modifier.fillMaxSize())
                }
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { openFromRoutines() }
        assertEquals(1, openedLive)

        // Back from the live screen: the tab is recomposed with the stale state, then recheck finds the session.
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        state.value = WorkoutEntryState.Live
        rule.waitForIdle()

        assertEquals(1, openedLive)
    }

    @Test
    fun afterTheSessionEnds_aNewSessionOpensTheLiveScreenOnce() {
        val state = MutableStateFlow<WorkoutEntryState>(WorkoutEntryState.Live)
        rule.setContent {
            AppTheme {
                val current by state.collectAsState()
                WorkoutEntryHost(state = current, onOpenLiveSession = {
                    openedLive++
                }, onOpenHistory = {}) { modifier, _ ->
                    Box(modifier.fillMaxSize())
                }
            }
        }
        rule.waitForIdle()
        state.value = WorkoutEntryState.Routines
        rule.waitForIdle()
        state.value = WorkoutEntryState.Live
        rule.waitForIdle()

        assertEquals(2, openedLive)
    }

    @Test
    fun openConfirmation_isRestoredByARecreatedViewModel() {
        val savedState = SavedStateHandle()
        viewModel(savedState).onEvent(WorkoutEntryEvent.RequestDiscard)

        show(viewModel(savedState))

        rule.onNodeWithText("¿Descartar entreno?").assertExists()
    }
}
