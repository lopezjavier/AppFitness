package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppMessages
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HapticEvent
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.RecordingAppHaptics
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CURRENT_WEEK = 2
private const val PIERNA_B_TAG = "trainer_box_block_session_${CURRENT_WEEK}_3"
private const val PIERNA_B_START_TAG = "${PIERNA_B_TAG}_start"
private const val OFFLINE_START_HINT = "Sin conexión: no se puede empezar."
private const val LONG_NAME = "Pierna B con énfasis en glúteo"

private class SessionStartMessages : AppMessages(CoroutineScope(Job())) {
    val shown = mutableListOf<String>()

    override fun show(text: String) {
        shown += text
    }
}

/**
 * T-300 (US-136): the session card of the next pending routine of the current week has a play button (disabled
 * offline or while a start is running), and the effects of that start navigate, reject or show the message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class TrainerSessionStartTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<TrainerEvent>()

    private val accepted = PlanSummary.Accepted(coachPlan(status = PlanStatus.Accepted))

    private fun session(index: Int, name: String, routineId: String?, done: Boolean) =
        PlanWeekSession(sessionIndex = index, name = name, routineId = routineId, done = done)

    private fun currentWeek(pendingName: String = "Pierna B", pendingRoutine: String? = "r-pierna-b") =
        planWeek(CURRENT_WEEK, WeekPhase.Current).copy(
            sessions = listOf(
                session(0, "Torso A", "r-torso-a", done = true),
                session(1, "Pierna A", "r-pierna-a", done = true),
                session(2, "Torso B", "r-torso-b", done = true),
                session(3, pendingName, pendingRoutine, done = false),
            ),
        )

    private fun pastWeek() = planWeek(1, WeekPhase.Past).copy(
        sessions = listOf(session(0, "Torso A", "r-past-a", false), session(1, "Pierna A", "r-past-b", false)),
    )

    private fun upcomingWeek() = planWeek(3, WeekPhase.Upcoming).copy(
        sessions = listOf(session(0, "Torso A", "r-next-a", false), session(1, "Pierna A", "r-next-b", false)),
    )

    private fun weeks(current: PlanWeek = currentWeek()) = listOf(pastWeek(), current, upcomingWeek())

    private fun state(weeks: List<PlanWeek>, starting: Boolean = false) = TrainerUiState.Content(
        plan = accepted,
        block = BlockSummary.Saved(coachBlock(saved = true)),
        weeks = weeks,
        startingRoutine = starting,
    )

    private fun show(state: TrainerUiState, online: Boolean = true, fontScale: Float = 1f) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                AppTheme { TrainerScreen(state, flowOf(online), { events += it }) }
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun assertNoStartButtons(week: Int, sessions: Int) {
        for (index in 0 until sessions) {
            rule.onNodeWithTag("trainer_box_block_session_${week}_${index}_start").assertDoesNotExist()
        }
    }

    // Gherkin: the next pending routine has a play button

    @Test
    fun theNextPendingRoutine_hasAPlayButton_andNoOtherCardDoes() {
        show(state(weeks()))

        rule.onNodeWithTag(PIERNA_B_START_TAG).assertExists()
        assertNoStartButtons(CURRENT_WEEK, 3)
        // The card tag stays on the text group (the button is its sibling).
        rule.onNodeWithTag(PIERNA_B_TAG).assertExists()
    }

    @Test
    fun tappingThePlayButton_sendsStartRoutine_andIsNamedAfterTheSession() {
        show(state(weeks()))

        rule.onNodeWithTag(PIERNA_B_START_TAG).assert(hasContentDescription("Empezar Pierna B"))
        tap(PIERNA_B_START_TAG)

        assertEquals(listOf<TrainerEvent>(TrainerEvent.StartRoutine("r-pierna-b")), events)
    }

    @Test
    fun theCardKeepsItsSpokenNameAndState_nextToTheButton() {
        show(state(weeks()))

        rule.onNodeWithTag(PIERNA_B_TAG).assert(hasContentDescription("Pierna B: Pendiente"))
    }

    @Test
    fun everySessionCard_showsTheBareGlyphOfItsPart_atTheHeightOfItsTitleLine() {
        show(state(weeks()))

        for (index in 0..3) {
            rule.onNodeWithTag("trainer_box_block_session_icon_${CURRENT_WEEK}_$index", useUnmergedTree = true)
                .assertExists()
        }
        val glyph = rule.onNodeWithTag("trainer_box_block_session_icon_${CURRENT_WEEK}_3", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(24.dp, glyph.width)
        assertEquals(24.dp, glyph.height)
    }

    // Gherkin: no button without a routine, in other weeks or when all are done

    @Test
    fun aPendingSessionWithoutARoutine_showsNoButton() {
        show(state(weeks(currentWeek(pendingRoutine = null))))

        assertNoStartButtons(CURRENT_WEEK, 4)
    }

    @Test
    fun aPastWeek_showsNoButton() {
        show(state(weeks()))

        tap("trainer_box_block_weeks_item_1")

        rule.onNodeWithTag("trainer_box_block_week_1", useUnmergedTree = true).assertExists()
        assertNoStartButtons(1, 2)
    }

    @Test
    fun anUpcomingWeek_showsNoButton() {
        show(state(weeks()))

        tap("trainer_box_block_weeks_item_3")

        rule.onNodeWithTag("trainer_box_block_week_3", useUnmergedTree = true).assertExists()
        assertNoStartButtons(3, 2)
    }

    @Test
    fun whenEverySessionIsDone_showsNoButton() {
        val allDone = currentWeek().copy(
            sessions = currentWeek().sessions.map { it.copy(done = true) },
        )
        show(state(weeks(allDone)))

        assertNoStartButtons(CURRENT_WEEK, 4)
    }

    // Gherkin: offline, and a start in flight

    @Test
    fun offline_theButtonIsDisabled_andTheHintExplainsIt() {
        show(state(weeks()), online = false)

        rule.onNodeWithTag(PIERNA_B_START_TAG).assertIsNotEnabled()
        rule.onNodeWithTag("offline_hint", useUnmergedTree = true).assertExists()
        rule.onNode(hasText(OFFLINE_START_HINT), useUnmergedTree = true).assertExists()
    }

    @Test
    fun online_theButtonIsEnabled_andThereIsNoStartHint() {
        show(state(weeks()), online = true)

        rule.onNodeWithTag(PIERNA_B_START_TAG).assertIsEnabled()
        rule.onNode(hasText(OFFLINE_START_HINT), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun offline_onAPastWeek_showsNoStartHint() {
        show(state(weeks()), online = false)

        tap("trainer_box_block_weeks_item_1")

        rule.onNode(hasText(OFFLINE_START_HINT), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun offline_withoutARoutineOnTheNextPending_showsNoStartHint() {
        show(state(weeks(currentWeek(pendingRoutine = null))), online = false)

        rule.onNode(hasText(OFFLINE_START_HINT), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun whileAStartIsRunning_theButtonIsDisabled() {
        show(state(weeks(), starting = true))

        rule.onNodeWithTag(PIERNA_B_START_TAG).assertIsNotEnabled()
    }

    // Layout: the card at 360 dp for the supported font scales

    private fun assertButtonFitsItsCard(fontScale: Float, name: String = "Pierna B") {
        show(state(weeks(currentWeek(pendingName = name))), fontScale = fontScale)

        val button = rule.onNodeWithTag(PIERNA_B_START_TAG).performScrollTo().assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val text = rule.onNodeWithTag(PIERNA_B_TAG).getUnclippedBoundsInRoot()
        val screen = rule.onNodeWithTag("trainer_screen").getUnclippedBoundsInRoot()
        assertEquals(48.dp, button.width)
        assertEquals(48.dp, button.height)
        assertTrue("the name starts before the button", text.left < button.left)
        assertTrue("the name group stays left of the button", text.right <= button.left)
        assertTrue("the name group has width", text.width > 0.dp)
        assertTrue("the button stays inside the screen", button.right <= screen.right && button.left >= screen.left)
    }

    @Test
    fun layout_atFontScale1_buttonFitsItsCard() = assertButtonFitsItsCard(1f)

    @Test
    fun layout_atFontScale1_3_buttonFitsItsCard() = assertButtonFitsItsCard(1.3f)

    @Test
    fun layout_atFontScale2_buttonFitsItsCard() = assertButtonFitsItsCard(2f)

    @Test
    fun layout_aLongName_wrapsWithoutPushingTheButtonOut() = assertButtonFitsItsCard(1f, LONG_NAME)

    // Restoration (ADR-40): the chosen week survives a recreation; back on the current week the button is back

    @Test
    fun theChosenWeek_survivesRecreation_andTheButtonIsOnTheSameCard() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { TrainerScreen(state(weeks()), flowOf(true), {}) } }
        tap("trainer_box_block_weeks_item_1")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_box_block_week_1", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(PIERNA_B_START_TAG).assertDoesNotExist()
        tap("trainer_box_block_weeks_item_2")
        rule.onNodeWithTag(PIERNA_B_START_TAG).assertExists()
    }

    // Effects

    private val haptics = RecordingAppHaptics()
    private val messages = SessionStartMessages()
    private var openedLive = 0

    private fun playEffect(effect: TrainerEffect) {
        val effects = Channel<TrainerEffect>(Channel.UNLIMITED)
        rule.setContent {
            CompositionLocalProvider(LocalAppHaptics provides haptics, LocalAppMessages provides messages) {
                AppTheme {
                    TrainerEffectsHost(
                        effects = effects.receiveAsFlow(),
                        onRefresh = {},
                        actions = TrainerActions(onOpenLiveSession = { openedLive++ }),
                    )
                }
            }
        }
        rule.waitForIdle()
        effects.trySend(effect)
        rule.waitForIdle()
    }

    @Test
    fun openLiveSession_navigatesOnce_withoutHapticOrMessage() {
        playEffect(TrainerEffect.OpenLiveSession)

        assertEquals(1, openedLive)
        assertTrue(haptics.calls.isEmpty())
        assertTrue(messages.shown.isEmpty())
    }

    @Test
    fun sessionInProgress_rejects_andShowsTheMessage_withoutNavigating() {
        playEffect(TrainerEffect.SessionInProgress)

        haptics.assertSingle(HapticEvent.Reject)
        assertEquals(listOf("Ya hay un entreno en curso"), messages.shown)
        assertEquals(0, openedLive)
    }

    @Test
    fun startFailed_rejects_andShowsTheGenericMessage() {
        playEffect(TrainerEffect.StartFailed(offline = false))

        haptics.assertSingle(HapticEvent.Reject)
        assertEquals(listOf("No se pudo completar la acción. Inténtalo de nuevo."), messages.shown)
        assertEquals(0, openedLive)
    }

    @Test
    fun startFailedOffline_rejects_andShowsTheOfflineMessage() {
        playEffect(TrainerEffect.StartFailed(offline = true))

        haptics.assertSingle(HapticEvent.Reject)
        assertEquals(listOf(OFFLINE_START_HINT), messages.shown)
    }
}
