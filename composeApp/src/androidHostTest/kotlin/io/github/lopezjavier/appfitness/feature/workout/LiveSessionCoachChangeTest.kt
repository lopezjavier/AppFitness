package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFigures
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.ProgressionAction
import io.github.lopezjavier.appfitness.domain.model.ProgressionReason
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import io.github.lopezjavier.appfitness.feature.trainer.CoachChangeMarker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val suggestion = ProgressionSuggestion(
    exerciseId = "e1",
    action = ProgressionAction.RaiseWeight,
    weightKg = 60.0,
    goalReps = 9,
    reason = ProgressionReason.AllSetsAtMax,
    setsUsed = emptyList(),
)

private fun liveState(suggestion: ProgressionSuggestion?, marker: CoachChangeMarker?) = LiveSessionUiState.Active(
    name = "Torso",
    startedAt = SESSION_START,
    exercises = listOf(
        LiveExercise(
            0,
            "e1",
            "Press banca",
            null,
            listOf(LiveSetRow(1, "", "", completed = false)),
            suggestion = suggestion,
            coachMarker = marker,
        ),
    ),
    isOnline = true,
)

/** US-97: inside a block the live exercise shows the coach's `?` instead of the T-159 suggestion; outside it stays. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class LiveSessionCoachChangeTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(state: LiveSessionUiState.Active) {
        rule.setContent { AppTheme { LiveSessionScreen(state, 0, {}, {}, onOpenExercise = {}) } }
        rule.waitForIdle()
    }

    @Test
    fun insideABlock_aChangedExerciseShowsTheMarker_andNoSuggestion() {
        val marker = CoachChangeMarker(
            CoachChangeKind.LoadRaise,
            CoachChangeFigures(weightFromKg = 60.0, weightToKg = 62.5, rpeMax = 8.0),
            "all_sets_at_max",
        )
        show(liveState(suggestion = null, marker = marker))

        rule.onNodeWithContentDescription("Por qué cambió el peso: Press banca").assertExists()
        rule.onNodeWithTag("live_exercise_0_suggestion").assertDoesNotExist()
        rule.onNodeWithTag("live_exercise_coach_marker_0").performClick()
        rule.onNodeWithText(
            "Subida de 60 kg a 62,5 kg: completaste todas las series en el máximo del rango con RPE 8 o menos.",
        ).assertExists()
    }

    @Test
    fun outsideABlock_theSuggestionStays_andThereIsNoMarker() {
        show(liveState(suggestion = suggestion, marker = null))

        rule.onNodeWithTag("live_exercise_0_suggestion").assertExists()
        rule.onNodeWithTag("live_exercise_coach_marker_0").assertDoesNotExist()
    }

    @Test
    fun aKeptWeightMarker_explainsTheOpenDiscomfortNeutrally() {
        show(
            liveState(
                suggestion = null,
                marker = CoachChangeMarker(CoachChangeKind.LoadKept, CoachChangeFigures(), "kept_discomfort"),
            ),
        )

        rule.onNodeWithTag("live_exercise_coach_marker_0").performClick()

        rule.onNodeWithText("El peso se mantiene mientras tengas una molestia abierta en este ejercicio.")
            .assertExists()
    }
}
