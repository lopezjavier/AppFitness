package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SetRowDefaults
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val TARGET = 40.minutes

private fun cardioRow(running: Boolean = false, seconds: Int = 0) = LiveSetRow(
    setIndex = 1,
    weight = "",
    reps = "",
    completed = false,
    plannedDurationMinutes = 40,
    stopwatchRunning = running,
    durationSeconds = seconds,
)

private fun cardioState(bar: TimerBarUi? = null, running: Boolean = false, seconds: Int = 0) =
    LiveSessionUiState.Active(
        name = "Cardio",
        startedAt = SESSION_START,
        exercises = listOf(
            LiveExercise(
                position = 0,
                exerciseId = "bike",
                name = "Bicicleta Estática",
                supersetLabel = null,
                sets = listOf(cardioRow(running, seconds)),
                metricType = ExerciseMetricType.DistanceDuration,
                cardio = true,
            ),
        ),
        timerBar = bar,
    )

private fun cardioBar(elapsed: kotlin.time.Duration, paused: Boolean = false) =
    TimerBarUi.Cardio(0, 1, "Bicicleta Estática", elapsed, TARGET, paused)

/** T-238 / US-72 / R-58: the cardio row layout and the cardio run in the shared timer bar. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class LiveSessionCardioTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<LiveSessionEvent>()

    private fun show(planned: Int?) {
        rule.setContent {
            AppTheme {
                Column {
                    SessionSetRow(
                        position = 0,
                        row = LiveSetRow(1, "", "", completed = false, plannedDurationMinutes = planned),
                        onEvent = {},
                        metricType = ExerciseMetricType.Duration,
                        showRpe = planned == null,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun showScreen(state: LiveSessionUiState, fontScale: Float = 1f) {
        rule.setContent {
            AppTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    LiveSessionScreen(state, 0, { events += it }, {}, onOpenExercise = {})
                }
            }
        }
        rule.waitForIdle()
    }

    /** Shows the screen once; the returned setters swap the state and the font scale (setContent runs once). */
    private fun showSwitchable(initial: LiveSessionUiState): Pair<(LiveSessionUiState) -> Unit, (Float) -> Unit> {
        var state by mutableStateOf(initial)
        var scale by mutableFloatStateOf(1f)
        rule.setContent {
            AppTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                    LiveSessionScreen(state, 0, {}, {}, onOpenExercise = {})
                }
            }
        }
        rule.waitForIdle()
        return Pair({
            state = it
            rule.waitForIdle()
        }, {
            scale = it
            rule.waitForIdle()
        })
    }

    private fun bounds(tag: String): DpRect = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().let {
        DpRect(it.left, it.top, it.right, it.bottom)
    }

    private fun centerX(rect: DpRect): Float = (rect.left.value + rect.right.value) / 2

    private fun centerY(rect: DpRect): Float = (rect.top.value + rect.bottom.value) / 2

    private val DpRect.width get() = right - left
    private val DpRect.height get() = bottom - top

    private fun isCardio(type: ExerciseMetricType) = type in
        setOf(ExerciseMetricType.DistanceDuration, ExerciseMetricType.StepsDuration, ExerciseMetricType.FloorsDuration)

    private fun exerciseOf(position: Int, type: ExerciseMetricType) = LiveExercise(
        position = position,
        exerciseId = "e$position",
        name = "Ejercicio $position",
        supersetLabel = null,
        sets = listOf(
            LiveSetRow(1, "", "", false, plannedDurationMinutes = 40.takeIf { isCardio(type) }),
        ),
        metricType = type,
        cardio = isCardio(type),
    )

    private fun stateOf(vararg types: ExerciseMetricType) = LiveSessionUiState.Active(
        name = "Sesion",
        startedAt = SESSION_START,
        exercises = types.mapIndexed { index, type -> exerciseOf(index, type) },
    )

    private fun twoCardsState() = stateOf(ExerciseMetricType.WeightReps, ExerciseMetricType.DistanceDuration)

    @Test
    fun theCardioSet_showsThePlannedMinutes_andNoRpeCell() {
        show(50)

        rule.onNodeWithTag("session_cardio_minutes_field_0_1").assertExists()
        rule.onNodeWithText("50 min").assertExists()
        rule.onNodeWithTag("session_cardio_minutes_field_0_1")
            .assertContentDescriptionEquals("Minutos de cardio, planificados 50")
        rule.onNodeWithTag("live_set_0_1_rpe").assertDoesNotExist()
    }

    @Test
    fun aLiftingSet_hasNoMinutesPlaceholder() {
        show(null)

        rule.onNodeWithTag("session_cardio_minutes_field_0_1").assertDoesNotExist()
    }

    @Test
    fun startButton_sendsTheStopwatchToggle() {
        showScreen(cardioState())

        rule.onNodeWithTag("session_cardio_start_0_1").assertIsEnabled()
            .assertContentDescriptionEquals("Iniciar cardio").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.StopwatchToggled(0, 1)), events)
    }

    @Test
    fun runningCardio_showsTheBarWithTheExerciseName_andNothingRunsInTheRow() {
        showScreen(cardioState(bar = cardioBar(12.minutes + 5.seconds), running = true, seconds = 725))

        rule.onNodeWithTag("live_cardio_timer_elapsed").assertTextContains("12:05")
        rule.onNodeWithTag("live_cardio_timer_label").assertTextContains("Bicicleta Estática")
        rule.onNodeWithTag("session_cardio_start_0_1").assertIsNotEnabled()
            .assertContentDescriptionEquals("En marcha en la barra")
        rule.onNodeWithTag("live_set_0_1_stopwatch_time").assertDoesNotExist()
        rule.onNodeWithContentDescription("Detener cronómetro").assertDoesNotExist()
    }

    @Test
    fun restBar_isTaggedDescanso_andTheCardioBarCarriesTheExerciseName() {
        showScreen(cardioState(bar = TimerBarUi.Rest(90.seconds, 120.seconds)))
        rule.onNodeWithTag("live_rest_label").assertTextContains("Descanso")
        rule.onNodeWithContentDescription("Descanso: 1 min 30 s").assertExists()
    }

    @Test
    fun cardioBar_hasDistinctSpokenText_andOnlyThePauseAction() {
        showScreen(cardioState(bar = cardioBar(12.minutes), running = true))

        rule.onNodeWithTag("live_cardio_timer_elapsed")
            .assertContentDescriptionEquals("Bicicleta Estática: 12 min 0 s de 40 min")
        rule.onNodeWithTag("live_cardio_timer_toggle").assertContentDescriptionEquals("Pausar cardio")
        rule.onNodeWithTag("live_rest_add").assertDoesNotExist()
        rule.onNodeWithTag("live_rest_skip").assertDoesNotExist()
    }

    @Test
    fun pausedCardio_showsThePausedLabel_andResumes() {
        showScreen(cardioState(bar = cardioBar(12.minutes, paused = true), seconds = 720))

        rule.onNodeWithTag("live_cardio_timer_paused").assertTextContains("En pausa")
        rule.onNodeWithTag("live_cardio_timer_elapsed")
            .assertContentDescriptionEquals("En pausa, Bicicleta Estática: 12 min 0 s de 40 min")
        rule.onNodeWithTag("live_cardio_timer_toggle").assertContentDescriptionEquals("Reanudar cardio").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.StopwatchToggled(0, 1)), events)
    }

    @Test
    fun pausingFromTheBar_sendsTheToggle() {
        showScreen(cardioState(bar = cardioBar(1.minutes), running = true, seconds = 60))

        rule.onNodeWithTag("live_cardio_timer_toggle").performClick()

        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.StopwatchToggled(0, 1)), events)
    }

    @Test
    fun overTarget_showsPlusTimeAndSaysSo() {
        showScreen(cardioState(bar = cardioBar(TARGET + 90.seconds), running = true))

        rule.onNodeWithTag("live_cardio_timer_elapsed").assertTextContains("+01:30")
        rule.onNodeWithTag("live_cardio_timer_elapsed")
            .assertContentDescriptionEquals("Bicicleta Estática: 1 min 30 s por encima del objetivo")
    }

    @Test
    fun timeField_isEditableWhilePaused() {
        showScreen(cardioState(bar = cardioBar(5.minutes, paused = true), seconds = 300))

        rule.onNodeWithTag("live_set_0_1_stopwatch_time").performClick()
        rule.onNodeWithTag("live_set_0_1_stopwatch_input").performTextInput("10:00")
        rule.onNodeWithTag("live_set_0_1_stopwatch_input").performImeAction()

        assertTrue(events.contains(LiveSessionEvent.DurationEdited(0, 1, 600)))
    }

    @Test
    fun headers_areCenteredOverTheirControls() {
        showScreen(cardioState())

        val pairs = listOf(
            "session_cardio_header_tipo" to "live_set_0_1_type",
            "session_cardio_header_km" to "live_set_0_1_distance",
            "session_cardio_header_tiempo" to "live_set_0_1_stopwatch_cell",
            "session_cardio_header_listo" to "live_set_0_1_check",
        )
        pairs.forEach { (header, control) ->
            val delta = abs(centerX(bounds(header)) - centerX(bounds(control)))
            assertTrue("$header is $delta dp off $control", delta <= CENTER_TOLERANCE)
        }
    }

    @Test
    fun startCircle_sitsOnLineOneBetweenTheTimeFieldAndListo_whenItFits() {
        // One field (time only): the circle fits on a phone; two fields (km and time) do not.
        showScreen(
            cardioState().let { state ->
                state.copy(exercises = state.exercises.map { it.copy(metricType = ExerciseMetricType.Duration) })
            },
        )

        val cell = bounds("live_set_0_1_stopwatch_cell")
        val start = bounds("session_cardio_start_0_1")
        val check = bounds("live_set_0_1_check")
        assertTrue(start.left >= cell.right && start.right <= check.left)
        assertTrue(start.width.value >= START_TARGET && start.height.value >= START_TARGET)
        rule.onAllNodesWithText("Iniciar").assertCountEquals(0)
    }

    @Test
    fun startPill_movesToLineTwoWithItsLabel_whenTheCircleDoesNotFit() {
        showScreen(cardioState(), fontScale = 2f)

        val start = bounds("session_cardio_start_0_1")
        val check = bounds("live_set_0_1_check")
        assertTrue("the pill sits under line 1", start.top >= check.bottom - CENTER_TOLERANCE.dp)
        rule.onNodeWithText("Iniciar").assertExists()
        rule.onNodeWithTag("session_cardio_start_0_1").assertContentDescriptionEquals("Iniciar cardio").performClick()
        assertEquals(listOf<LiveSessionEvent>(LiveSessionEvent.StopwatchToggled(0, 1)), events)
    }

    @Test
    fun cardioStartFits_dependsOnTheWidthAndTheFontScale() {
        val phone = 328.dp // 360 dp minus the card margins and padding
        assertTrue(SetRowDefaults.cardioStartFits(phone, fieldCount = 1, hasNote = true, fontScale = 1f))
        assertTrue(SetRowDefaults.cardioStartFits(phone, fieldCount = 1, hasNote = true, fontScale = 1.3f))
        assertFalse(SetRowDefaults.cardioStartFits(phone, fieldCount = 1, hasNote = true, fontScale = 2f))
        assertFalse(SetRowDefaults.cardioStartFits(phone, fieldCount = 2, hasNote = true, fontScale = 1f))
        assertTrue(SetRowDefaults.cardioStartFits(412.dp, fieldCount = 2, hasNote = true, fontScale = 1f))
    }

    @Test
    fun cardioRow_neverShowsAPreviousLine_evenIfTheRowCarriesOne() {
        val withPrevious = cardioState().let { state ->
            state.copy(
                exercises = state.exercises.map {
                    it.copy(sets = listOf(cardioRow().copy(previous = "5 km · 30:00")))
                },
            )
        }
        showScreen(withPrevious)

        rule.onAllNodesWithText("Antes", substring = true).assertCountEquals(0)
        rule.onNodeWithTag("live_set_0_1_previous").assertDoesNotExist()
    }

    @Test
    fun noPreviousValue_drawsNoPreviousLine() {
        showScreen(cardioState())

        rule.onAllNodesWithText("Antes:", substring = true).assertCountEquals(0)
    }

    @Test
    fun noteButton_sitsOnLineOneAfterListo_inEveryMetricType_atEveryFontScale() {
        val (setState, setScale) = showSwitchable(stateOf(ExerciseMetricType.WeightReps))
        FONT_SCALES.forEach { scale ->
            setScale(scale)
            ExerciseMetricType.entries.forEach { type ->
                setState(stateOf(type))
                val note = bounds("live_set_note_action_0_1")
                val check = bounds("live_set_0_1_check")
                val delta = abs(centerY(note) - centerY(check))
                assertTrue("$type at $scale: note is $delta dp off the check", delta <= ALIGN_TOLERANCE)
                assertTrue("$type at $scale: note is left of the check", note.left >= check.right)
            }
        }
    }

    @Test
    fun listoAndNota_haveTheSameLeftEdge_onAStrengthAndACardioCard() {
        val (_, setScale) = showSwitchable(twoCardsState())
        FONT_SCALES.forEach { scale ->
            setScale(scale)

            val strengthCheck = bounds("live_set_0_1_check")
            val cardioCheck = bounds("live_set_1_1_check")
            val strengthNote = bounds("live_set_note_action_0_1")
            val cardioNote = bounds("live_set_note_action_1_1")
            assertEquals("LISTO at $scale", strengthCheck.left.value, cardioCheck.left.value, ALIGN_TOLERANCE)
            assertEquals("NOTA at $scale", strengthNote.left.value, cardioNote.left.value, ALIGN_TOLERANCE)
        }
    }

    @Test
    fun timeField_isDrawnAsAFieldFillingItsColumn_andTheHeaderCentresOverIt() {
        showScreen(cardioState())

        val header = bounds("session_cardio_header_tiempo")
        val field = bounds("live_set_0_1_stopwatch_cell")
        assertEquals(centerX(header), centerX(field), ALIGN_TOLERANCE)
        assertTrue(field.height.value >= START_TARGET)
    }

    @Test
    fun targetText_sitsInsideTheTimeField_notUnderTheTypeChip() {
        showScreen(cardioState())

        val target = bounds("session_cardio_minutes_field_0_1")
        val cell = bounds("live_set_0_1_stopwatch_cell")
        val type = bounds("live_set_0_1_type")
        assertTrue(target.left >= cell.left && target.right <= cell.right)
        assertTrue(target.left >= type.right)
    }

    @Test
    fun cardioRow_hasNoStopSquare() {
        showScreen(cardioState(bar = cardioBar(3.minutes), running = true, seconds = 180))

        rule.onNodeWithContentDescription("Detener cronómetro").assertDoesNotExist()
        rule.onNodeWithTag("live_set_0_1_stopwatch_toggle").assertDoesNotExist()
    }

    @Test
    fun largeFont_keepsTheRowCellsApart() {
        showScreen(cardioState(), fontScale = 2f)

        val tags = listOf(
            "live_set_0_1_type",
            "live_set_0_1_distance",
            "live_set_0_1_stopwatch_cell",
            "live_set_0_1_check",
        )
        tags.zipWithNext().forEach { (left, right) ->
            assertTrue("$left overlaps $right", bounds(left).right <= bounds(right).left)
        }
        val start = bounds("session_cardio_start_0_1")
        val target = bounds("session_cardio_minutes_field_0_1")
        assertTrue("target overlaps the start button", target.bottom <= start.top || target.right <= start.left)
        val headers = listOf(
            "session_cardio_header_tipo",
            "session_cardio_header_km",
            "session_cardio_header_tiempo",
            "session_cardio_header_listo",
        )
        headers.zipWithNext().forEach { (left, right) ->
            assertTrue("$left overlaps $right", bounds(left).right <= bounds(right).left)
        }
    }

    @Test
    fun runningAndPausedBars_surviveRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                LiveSessionScreen(
                    cardioState(bar = cardioBar(5.minutes, paused = true), seconds = 300),
                    0,
                    {},
                    {},
                    onOpenExercise = {},
                )
            }
        }
        rule.onNodeWithTag("live_cardio_timer_elapsed").assertTextContains("05:00")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("live_cardio_timer_elapsed").assertTextContains("05:00")
        rule.onNodeWithTag("live_cardio_timer_paused").assertExists()
    }

    private companion object {
        const val CENTER_TOLERANCE = 2f
        const val ALIGN_TOLERANCE = 1f
        const val START_TARGET = 48f
        val FONT_SCALES = listOf(1f, 1.3f, 2f)
    }
}
