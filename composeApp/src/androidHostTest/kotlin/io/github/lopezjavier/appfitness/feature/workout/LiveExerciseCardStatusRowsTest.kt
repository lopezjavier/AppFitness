package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DiscomfortLevel
import io.github.lopezjavier.appfitness.domain.model.ExerciseNoteKind
import io.github.lopezjavier.appfitness.domain.model.ProgressionAction
import io.github.lopezjavier.appfitness.domain.model.ProgressionReason
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun cardState(editable: Boolean = true, withRows: Boolean = true) = LiveSessionUiState.Active(
    name = "Torso",
    startedAt = SESSION_START,
    exercises = listOf(
        LiveExercise(
            0,
            "e1",
            "Press banca",
            null,
            listOf(LiveSetRow(1, "", "", completed = false)),
            targetRpeText = "9".takeIf { withRows },
            suggestion = ProgressionSuggestion(
                exerciseId = "e1",
                action = ProgressionAction.RaiseWeight,
                weightKg = 60.0,
                goalReps = 9,
                reason = ProgressionReason.AllSetsAtMax,
                setsUsed = emptyList(),
            ).takeIf { withRows },
            discomfort = LiveDiscomfort(DiscomfortLevel.Tension).takeIf { withRows },
            routineNoteKind = ExerciseNoteKind.Technique.takeIf { withRows },
            routineNoteEditable = editable,
        ),
    ),
    isOnline = true,
)

/** R-51: the status rows of a live exercise card, their order and their absence. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class LiveExerciseCardStatusRowsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(state: LiveSessionUiState.Active) {
        rule.setContent { AppTheme { LiveSessionScreen(state, 0, {}, {}, onOpenExercise = {}) } }
        rule.waitForIdle()
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getBoundsInRoot().top

    @Test
    fun theStatusRows_stackInOrderBetweenTheTargetAndTheSetTable() {
        show(cardState())

        val tops = listOf(
            "live_exercise_0_target_rpe",
            "live_exercise_0_discomfort_block",
            "live_exercise_note_0",
            "live_exercise_0_suggestion",
            "live_set_0_1_rpe",
        ).map(::top)

        assertEquals(tops.sorted(), tops)
        assertEquals(tops.distinct(), tops)
    }

    @Test
    fun withoutAnyStatus_noStatusRowIsDrawn() {
        show(cardState(withRows = false))

        listOf("live_exercise_0_discomfort_block", "live_exercise_note_0", "live_exercise_0_suggestion")
            .forEach { rule.onNodeWithTag(it).assertDoesNotExist() }
    }

    @Test
    fun theNoteRow_isTappableOnlyWhenTheRoutineNoteIsEditable() {
        show(cardState(editable = false))

        val config = rule.onNodeWithTag("live_exercise_note_0").fetchSemanticsNode().config
        assertFalse(config.contains(SemanticsActions.OnClick))
    }
}
