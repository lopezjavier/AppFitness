package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Day = LocalDate(2026, 10, 12)

/** "Añadir entreno" of a calendar day opens the source chooser, which starts the form on that day (US-37). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class HistoryAddPastWorkoutTest {
    @get:Rule
    val rule = createComposeRule()

    private val starts = mutableListOf<PastWorkoutChooserEffect.Start>()
    private var sessionsReads = 0
    private lateinit var model: PastWorkoutChooserViewModel

    private fun show() {
        model = PastWorkoutChooserViewModel(FakeRoutineRepository())
        rule.setContent {
            val state by model.uiState.collectAsState()
            LaunchedEffect(model) { model.effects.collect { starts += it as PastWorkoutChooserEffect.Start } }
            AppTheme {
                PastWorkoutChooserSheet(
                    state,
                    model::onEvent,
                    sessions = {
                        remember { sessionsReads++ }
                        HistoryUiState.Empty
                    },
                )
            }
        }
        rule.runOnUiThread { model.onEvent(PastWorkoutChooserEvent.Open(Day)) }
        rule.waitForIdle()
    }

    @Test
    fun theChooser_asksWhereToStart_andTheEmptySourceStartsOnTheTappedDay() {
        show()

        rule.onNodeWithTag("past_workout_source_sheet_title").assertExists()
        rule.onNodeWithTag("past_workout_source_empty").performClick()
        rule.waitForIdle()

        assertEquals(listOf(PastWorkoutChooserEffect.Start(PastWorkoutSource.Empty, Day)), starts)
        rule.onNodeWithTag("past_workout_source_sheet").assertDoesNotExist()
    }

    @Test
    fun theWorkoutList_isReadOnlyWhenItsStepOpens() {
        show()
        assertEquals(0, sessionsReads)

        rule.onNodeWithTag("past_workout_source_session").performClick()
        rule.waitForIdle()

        assertEquals(1, sessionsReads)
        rule.onNodeWithTag("past_workout_source_sessions_empty").assertExists()
    }
}
