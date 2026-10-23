package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.swipeLeft
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun set(index: Int, done: Boolean = false) = LiveSetRow(index, "", "", done)

private fun controlsState(sets: List<LiveSetRow> = listOf(set(1), set(2), set(3), set(4)), pausedAt: Instant? = null) =
    LiveSessionUiState.Active(
        name = "Torso",
        startedAt = SESSION_START,
        exercises = listOf(LiveExercise(0, "e1", "Press banca", null, sets)),
        pausedAt = pausedAt,
    )

/** US-38 session controls on the live screen: pause/resume, empty finish and swipe-delete of a set. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class LiveSessionControlsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<LiveSessionEvent>()

    private fun show(state: LiveSessionUiState.Active) {
        rule.setContent {
            AppTheme { LiveSessionScreen(state, 600, { events += it }, {}, onOpenExercise = {}) }
        }
        rule.waitForIdle()
    }

    private fun openMenu() {
        rule.onNodeWithTag("live_menu_button").performClick()
        rule.waitForIdle()
    }

    @Test
    fun running_menuOffersPause_andSendsIt() {
        show(controlsState())

        openMenu()
        rule.onNodeWithTag("live_menu_resume").assertDoesNotExist()
        rule.onNodeWithText("Pausar entreno").assertExists()
        rule.onNodeWithTag("live_menu_pause").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.PauseSession), events)
    }

    @Test
    fun paused_showsPausedLabelInsteadOfLive_andMenuOffersResume() {
        show(controlsState(pausedAt = SESSION_START))

        rule.onNodeWithTag("live_pause_label").assertExists()
        rule.onNodeWithText("EN PAUSA").assertExists()
        rule.onNodeWithText("EN VIVO").assertDoesNotExist()
        val description = rule.onNodeWithTag("live_timer").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.StateDescription)
        assertEquals("En pausa", description)

        openMenu()
        rule.onNodeWithTag("live_menu_pause").assertDoesNotExist()
        rule.onNodeWithText("Reanudar entreno").assertExists()
        rule.onNodeWithTag("live_menu_resume").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.ResumeSession), events)
    }

    @Test
    fun running_hasNoPausedLabel() {
        show(controlsState())

        rule.onNodeWithTag("live_pause_label").assertDoesNotExist()
        rule.onNodeWithText("EN VIVO").assertExists()
    }

    @Test
    fun noCompletedSet_finishIsDisabledWithAReason() {
        show(controlsState())

        openMenu()

        rule.onNodeWithTag("live_menu_finish").assertIsNotEnabled()
        rule.onNodeWithTag("live_menu_finish_reason").assertExists()
        rule.onNodeWithText("Completa al menos una serie para finalizar").assertExists()
    }

    @Test
    fun oneCompletedSet_finishIsEnabledWithoutReason() {
        show(controlsState(sets = listOf(set(1, done = true), set(2))))

        openMenu()

        rule.onNodeWithTag("live_menu_finish").assertIsEnabled()
        rule.onNodeWithTag("live_menu_finish_reason").assertDoesNotExist()
    }

    @Test
    fun swipingSetTwo_sendsDeleteAtOnce_withoutUndo() {
        show(controlsState())

        rule.onNodeWithTag("live_set_0_2_swipe").performTouchInput { swipeLeft(startX = right - 1f, endX = 0f) }
        rule.waitForIdle()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.DeleteSet(0, 2)), events)
        rule.onNodeWithText("Deshacer").assertDoesNotExist()
    }

    @Test
    fun afterTheDeleteTheStateRendersThreeSets() {
        show(controlsState(sets = listOf(set(1), set(2), set(3))))

        listOf(1, 2, 3).forEach { rule.onNodeWithTag("live_set_0_$it").assertExists() }
        rule.onNodeWithTag("live_set_0_4").assertDoesNotExist()
    }

    @Test
    fun setRow_exposesTheAccessibilityDeleteAction() {
        show(controlsState())

        val actions = rule.onNodeWithTag("live_set_0_3_swipe").fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions)
        assertNotNull(actions?.firstOrNull { it.label == "Eliminar serie 3" })
    }

    @Test
    fun busy_disablesTheSwipeDelete() {
        show(controlsState().copy(busy = true))

        val actions = rule.onNodeWithTag("live_set_0_2_swipe").fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions)
        assertEquals(true, actions.isNullOrEmpty())
    }

    @Test
    fun pausedState_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            var paused by rememberSaveable { mutableStateOf(false) }
            AppTheme {
                LiveSessionScreen(
                    state = controlsState(pausedAt = if (paused) SESSION_START else null),
                    elapsedSeconds = 600,
                    onEvent = { if (it is LiveSessionEvent.PauseSession) paused = true },
                    onBack = {},
                    onOpenExercise = {},
                )
            }
        }
        openMenu()
        rule.onNodeWithTag("live_menu_pause").performClick()
        rule.onNodeWithTag("live_pause_label").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("live_pause_label").assertExists()
    }
}
