package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

private fun ghostRow(index: Int, previous: String?, done: Boolean = false) = LiveSetRow(
    setIndex = index,
    weight = if (done) "85" else "",
    reps = if (done) "8" else "",
    completed = done,
    weightPlaceholder = previous?.let { "85" },
    repsPlaceholder = previous?.let { "8" },
    previous = previous,
)

private fun session(
    rest: RestTimerUi? = null,
    restSeconds: Int = 120,
    rows: List<LiveSetRow> =
        listOf(ghostRow(1, "80 kg × 10", done = true), ghostRow(2, "85 kg × 8"), ghostRow(3, null)),
) = LiveSessionUiState.Active(
    name = "Torso",
    startedAt = SESSION_START,
    exercises = listOf(LiveExercise(0, "e1", "Press banca", null, rows, restSeconds)),
    restTimer = rest,
)

/** US-12 ghost data and US-13 rest timer as the live screen renders them. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class LiveSessionRestTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<LiveSessionEvent>()

    private val opened = mutableListOf<String>()

    private fun show(state: LiveSessionUiState) {
        rule.setContent {
            AppTheme { LiveSessionScreen(state, 0, { events += it }, {}, onOpenExercise = { opened += it }) }
        }
        rule.waitForIdle()
    }

    @Test
    fun ghost_showsThePreviousPerformance_andADashWhenThereIsNone() {
        show(session())

        rule.onNodeWithTag("live_set_0_2_previous").assertTextContains("85 kg × 8")
        rule.onNodeWithTag("live_set_0_3_previous").assertTextContains("—")
    }

    @Test
    fun ghost_tapOnAPendingRowAsksToUseThePreviousValues() {
        show(session())

        rule.onNodeWithTag("live_set_0_2_previous").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.UsePrevious(0, 2)), events)
    }

    @Test
    fun ghost_tapOnACompletedRowDoesNothing() {
        show(session())

        rule.onNodeWithTag("live_set_0_1_previous").performClick()

        assertTrue(events.isEmpty())
    }

    @Test
    fun ghost_placeholdersAreNotValues() {
        show(session())

        val weight = rule.onNodeWithTag("live_set_0_2_weight").fetchSemanticsNode()
        assertEquals("", weight.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty())
        assertTrue(events.none { it is LiveSessionEvent.WeightChanged })
    }

    @Test
    fun restBar_showsTheCountdownAndSendsAddAndSkip() {
        show(session(rest = RestTimerUi(120.seconds, 120.seconds)))

        rule.onNodeWithTag("live_rest_countdown").assertTextContains("02:00")
        rule.onNodeWithTag("live_rest_add").assertTextContains("+30s")
        rule.onNodeWithTag("live_rest_skip").assertTextContains("Omitir")
        rule.onNodeWithTag("live_rest_add").performClick()
        rule.onNodeWithTag("live_rest_skip").performClick()

        assertEquals(listOf(LiveSessionEvent.RestAdd30, LiveSessionEvent.RestSkip), events)
    }

    @Test
    fun tappingTheExerciseName_opensItsDetail() {
        show(session())

        rule.onNodeWithTag("live_exercise_0_open").performClick()

        assertEquals(listOf("e1"), opened)
    }

    @Test
    fun exerciseNameRegion_isAtLeast48dpHigh_andTheMenuStillOpensItsOwnSheet() {
        show(session())

        rule.onNodeWithTag("live_exercise_0_open").getUnclippedBoundsInRoot().height.let { assertTrue(it >= 48.dp) }
        rule.onNodeWithTag("live_exercise_0_menu").performClick()
        rule.waitForIdle()

        assertTrue(opened.isEmpty())
    }

    @Test
    fun restSheet_showsMinutesAndSeconds() {
        show(session(restSeconds = 70))
        rule.onNodeWithTag("live_exercise_0_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("live_exercise_0_menu_rest").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("live_exercise_0_rest_value").assertTextContains("1 min 10 s")
    }

    @Test
    fun restBar_isAbsentWithoutARunningRest() {
        show(session(rest = null))

        rule.onNodeWithTag("live_rest_countdown").assertDoesNotExist()
    }

    @Test
    fun restBar_neverOverlapsTheListOrTheFirstRows() {
        show(session(rest = RestTimerUi(72.seconds, 120.seconds)))

        val bar = rule.onNodeWithTag("live_rest_countdown").getUnclippedBoundsInRoot()
        val firstCard = rule.onNodeWithTag("live_exercise_0").getUnclippedBoundsInRoot()
        val firstRow = rule.onNodeWithTag("live_set_0_1").getUnclippedBoundsInRoot()
        assertTrue(bar.bottom <= firstCard.top)
        assertTrue(bar.bottom <= firstRow.top)
    }

    @Test
    fun restBar_staysPinnedWhileTheListScrolls() {
        show(session(rest = RestTimerUi(72.seconds, 120.seconds)))
        val before = rule.onNodeWithTag("live_rest_countdown").getUnclippedBoundsInRoot()

        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("live_add_exercise"))

        assertEquals(before.top, rule.onNodeWithTag("live_rest_countdown").getUnclippedBoundsInRoot().top)
    }

    @Test
    fun restMenu_opensASheetWithTheRestAndSavesTheNewValue() {
        show(session(restSeconds = 120))

        rule.onNodeWithTag("live_exercise_0_menu").performClick()
        rule.onNodeWithTag("live_exercise_0_menu_rest").performClick()
        rule.onNodeWithTag("live_exercise_0_rest_sheet").assertExists()
        rule.onNodeWithTag("live_exercise_0_rest_value").assertTextContains("2 min")
        rule.onNodeWithTag("live_exercise_0_rest_increment").performClick()
        rule.onNodeWithTag("live_exercise_0_rest_value").assertTextContains("2 min 10 s")
        rule.onNodeWithTag("live_exercise_0_rest_confirm").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.SetExerciseRest(0, 130)), events)
    }

    @Test
    fun restMenu_stepperStopsAtTheBounds() {
        show(session(restSeconds = 0))

        rule.onNodeWithTag("live_exercise_0_menu").performClick()
        rule.onNodeWithTag("live_exercise_0_menu_rest").performClick()

        rule.onNodeWithTag("live_exercise_0_rest_decrement").assertIsNotEnabled()
        rule.onNodeWithTag("live_exercise_0_rest_increment").assertIsEnabled()
    }

    @Test
    fun restMenu_buttonHasA48dpTarget() {
        show(session())

        val bounds = rule.onNodeWithTag("live_exercise_0_menu").getUnclippedBoundsInRoot()
        assertTrue(bounds.width >= 48.dp && bounds.height >= 48.dp)
    }
}
