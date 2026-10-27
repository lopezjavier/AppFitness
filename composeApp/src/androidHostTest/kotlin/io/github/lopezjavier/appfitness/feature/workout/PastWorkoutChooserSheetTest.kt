package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The source chooser of "Añadir entreno" (US-37): three options, then a routine or a workout list. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class PastWorkoutChooserSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<PastWorkoutChooserEvent>()

    private val rows = HistoryUiState.Content(
        rows = listOf(HistoryRow("s1", DateLabel.Yesterday, "Torso", 3_120, 2_120.0, 2)),
        endReached = true,
    )

    private fun show(
        step: PastWorkoutChooserStep,
        routines: ChooserRoutines = ChooserRoutines.Loading,
        history: HistoryUiState = rows,
    ) {
        rule.setContent {
            AppTheme {
                PastWorkoutChooserSheet(
                    PastWorkoutChooserUiState(step, routines),
                    { events += it },
                    sessions = { history },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun closed_showsNothing() {
        show(PastWorkoutChooserStep.Closed)

        rule.onNodeWithTag("past_workout_source_sheet").assertDoesNotExist()
    }

    @Test
    fun theTitle_asksWhereToStart_andFollowsTheStep() {
        show(PastWorkoutChooserStep.Options)
        rule.onNodeWithTag("past_workout_source_sheet_title").assertTextEquals("¿Desde dónde empiezas?")
    }

    @Test
    fun theRoutineStep_isTitledDesdeUnaRutina_withoutABackRow() {
        show(PastWorkoutChooserStep.Routines)

        rule.onNodeWithTag("past_workout_source_sheet_title").assertTextEquals("Desde una rutina")
        rule.onNodeWithTag("past_workout_source_back").assertDoesNotExist()
    }

    @Test
    fun theOptions_areThreeRowsThatEmitTheirChoice() {
        show(PastWorkoutChooserStep.Options)

        rule.onNodeWithTag("past_workout_source_empty").assertTextContains("Desde cero")
        rule.onNodeWithTag("past_workout_source_routine").assertTextContains("Desde una rutina")
        rule.onNodeWithTag("past_workout_source_session").assertTextContains("Desde un entreno anterior")
        rule.onNodeWithTag("past_workout_source_empty").performClick()
        rule.onNodeWithTag("past_workout_source_routine").performClick()
        rule.onNodeWithTag("past_workout_source_session").performClick()

        assertEquals(
            listOf(
                PastWorkoutChooserEvent.ChooseEmpty,
                PastWorkoutChooserEvent.ChooseRoutine,
                PastWorkoutChooserEvent.ChooseWorkout,
            ),
            events,
        )
    }

    @Test
    fun theRoutinePicker_listsRoutines_andPickingOneEmitsIt() {
        show(PastWorkoutChooserStep.Routines, ChooserRoutines.Content(listOf(RoutineSummary("r1", "Torso", 4))))

        rule.onNodeWithTag("past_workout_source_routine_r1").performClick()

        assertEquals(listOf<PastWorkoutChooserEvent>(PastWorkoutChooserEvent.PickRoutine("r1")), events)
    }

    @Test
    fun theRoutinePicker_showsEmptyLoadingAndErrorStates() {
        show(PastWorkoutChooserStep.Routines, ChooserRoutines.Empty)
        rule.onNodeWithTag("past_workout_source_routines_empty").assertExists()
    }

    @Test
    fun theRoutinePicker_errorOffersRetry() {
        show(PastWorkoutChooserStep.Routines, ChooserRoutines.Error)

        rule.onNodeWithTag("past_workout_source_routines_error_action").performClick()

        assertEquals(listOf<PastWorkoutChooserEvent>(PastWorkoutChooserEvent.RetryRoutines), events)
    }

    @Test
    fun theWorkoutPicker_usesTheHistoryRow_andPickingOneEmitsIt() {
        show(PastWorkoutChooserStep.Sessions)

        rule.onNodeWithTag("past_workout_source_session_s1").performClick()

        assertEquals(listOf<PastWorkoutChooserEvent>(PastWorkoutChooserEvent.PickWorkout("s1")), events)
    }

    @Test
    fun theWorkoutPicker_withoutWorkouts_showsTheEmptyState() {
        show(PastWorkoutChooserStep.Sessions, history = HistoryUiState.Empty)

        rule.onNodeWithTag("past_workout_source_sessions_empty").assertExists()
    }

    @Test
    fun closingTheSheet_emitsDismiss() {
        show(PastWorkoutChooserStep.Options)

        rule.onNodeWithTag("past_workout_source_sheet_close").performClick()
        rule.waitUntil(timeoutMillis = 5_000) { events.isNotEmpty() } // The sheet animates out first.

        assertEquals(listOf<PastWorkoutChooserEvent>(PastWorkoutChooserEvent.Dismiss), events)
    }
}
