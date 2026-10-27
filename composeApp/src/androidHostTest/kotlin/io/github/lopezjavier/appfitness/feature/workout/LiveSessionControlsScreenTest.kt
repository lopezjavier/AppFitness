package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

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
    fun running_topBarOffersPause_andSendsIt() {
        show(controlsState())

        rule.onNodeWithTag("live_pause_toggle").assertContentDescriptionEquals("Pausar entreno")
        rule.onNodeWithTag("live_pause_toggle").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.PauseSession), events)
    }

    @Test
    fun paused_showsPausedLabelInsteadOfLive_andMenuOffersResume() {
        show(controlsState(pausedAt = SESSION_START))

        rule.onNodeWithTag("live_subtitle").assertTextContains("EN PAUSA", substring = true)
        val description = rule.onNodeWithTag("live_subtitle").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.StateDescription)
        assertEquals("En pausa", description)

        rule.onNodeWithTag("live_pause_toggle").assertContentDescriptionEquals("Reanudar entreno")
        rule.onNodeWithTag("live_pause_toggle").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.ResumeSession), events)
    }

    @Test
    fun pausedRest_showsTheFrozenTimeWithTheCue_andSpeaksIt() {
        show(controlsState(pausedAt = SESSION_START).copy(restTimer = RestTimerUi(50.seconds, 90.seconds, true)))

        rule.onNodeWithTag("live_rest_countdown").assertTextEquals("00:50")
        rule.onNodeWithTag("live_rest_timer_paused").assertTextEquals("En pausa")
        rule.onNodeWithTag("live_rest_countdown").assertContentDescriptionEquals("Descanso en pausa: 50 s")
    }

    @Test
    fun runningRest_hasNoPausedCue() {
        show(controlsState().copy(restTimer = RestTimerUi(50.seconds, 90.seconds, false)))

        rule.onNodeWithTag("live_rest_countdown").assertContentDescriptionEquals("Descanso: 50 s")
        rule.onNodeWithTag("live_rest_timer_paused").assertDoesNotExist()
    }

    @Test
    fun running_hasNoPausedLabel() {
        show(controlsState())

        rule.onNodeWithTag("live_subtitle").assertTextContains("EN VIVO", substring = true)
    }

    @Test
    fun menu_hasNoPauseOrResumeItem() {
        show(controlsState())

        openMenu()

        rule.onNodeWithTag("live_menu_pause").assertDoesNotExist()
        rule.onNodeWithTag("live_menu_resume").assertDoesNotExist()
        rule.onNodeWithTag("live_menu_finish").assertExists()
    }

    @Test
    fun aPendingSet_hidesTheFinishButton() {
        show(controlsState(sets = listOf(set(1, done = true), set(2))))

        rule.onNodeWithTag("live_add_exercise").assertExists()
        rule.onNodeWithTag("live_finish_button").assertDoesNotExist()
    }

    @Test
    fun allSetsDone_showsFinishBelowAddExercise_andRequestsFinish() {
        show(controlsState(sets = listOf(set(1, done = true), set(2, done = true))))

        val addBottom = rule.onNodeWithTag("live_add_exercise").getBoundsInRoot().bottom
        rule.onNodeWithTag("live_finish_button").assertExists().assertIsEnabled()
        assertTrue(rule.onNodeWithTag("live_finish_button").getBoundsInRoot().top >= addBottom)
        rule.onNodeWithTag("live_finish_button").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.RequestFinish), events)
    }

    @Test
    fun allSetsDone_offline_finishIsDisabledWithTheReasonVisible() {
        show(controlsState(sets = listOf(set(1, done = true))).copy(isOnline = false))

        rule.onNodeWithTag("live_finish_button").assertIsNotEnabled()
        rule.onNodeWithTag("live_finish_offline_reason").assertExists()
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
        rule.onNodeWithTag("live_pause_toggle").performClick()
        rule.onNodeWithTag("live_subtitle").assertTextContains("EN PAUSA", substring = true)

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("live_subtitle").assertTextContains("EN PAUSA", substring = true)
    }
}
