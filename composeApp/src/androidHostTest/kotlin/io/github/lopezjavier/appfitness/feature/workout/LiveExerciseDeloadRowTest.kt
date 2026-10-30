package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DeloadExerciseSuggestion
import io.github.lopezjavier.appfitness.domain.model.ProgressionAction
import io.github.lopezjavier.appfitness.domain.model.ProgressionReason
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun deloadState(plannedSets: Int, deloadSets: Int, withDeload: Boolean = true) = LiveSessionUiState.Active(
    name = "Torso",
    startedAt = SESSION_START,
    exercises = listOf(
        LiveExercise(
            0,
            "e1",
            "Press banca",
            null,
            listOf(LiveSetRow(1, "", "", completed = false)),
            suggestion = ProgressionSuggestion(
                exerciseId = "e1",
                action = ProgressionAction.RaiseWeight,
                weightKg = 60.0,
                goalReps = 9,
                reason = ProgressionReason.AllSetsAtMax,
                setsUsed = emptyList(),
            ),
            deload = DeloadExerciseSuggestion("e1", plannedSets, deloadSets).takeIf { withDeload },
        ),
    ),
    isOnline = true,
)

/** US-75: the deload line of a live exercise replaces the progression row and is informative only (R-51). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class LiveExerciseDeloadRowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(state: LiveSessionUiState.Active) {
        rule.setContent { AppTheme { LiveSessionScreen(state, 0, {}, {}, onOpenExercise = {}) } }
        rule.waitForIdle()
    }

    @Test
    fun inADeloadWeek_theLineShowsSameWeightAndTheReducedSets() {
        show(deloadState(plannedSets = 5, deloadSets = 3))

        rule.onNodeWithTag("live_exercise_0_deload").assertExists()
        rule.onNodeWithText("Descarga: mismo peso, 3 de 5 series").assertExists()
    }

    @Test
    fun withASingleSet_theLineReadsOneSerie() {
        show(deloadState(plannedSets = 1, deloadSets = 1))

        rule.onNodeWithText("Descarga: mismo peso, 1 serie").assertExists()
    }

    @Test
    fun theDeloadLine_hidesTheProgressionRow() {
        show(deloadState(plannedSets = 4, deloadSets = 2))

        rule.onNodeWithTag("live_exercise_0_suggestion").assertDoesNotExist()
    }

    @Test
    fun theDeloadLine_isNotTappable() {
        show(deloadState(plannedSets = 4, deloadSets = 2))

        val config = rule.onNodeWithTag("live_exercise_0_deload").fetchSemanticsNode().config
        assertFalse(config.contains(SemanticsActions.OnClick))
    }

    @Test
    fun outsideADeloadWeek_noDeloadLineAndTheProgressionRowStays() {
        show(deloadState(plannedSets = 4, deloadSets = 2, withDeload = false))

        rule.onNodeWithTag("live_exercise_0_deload").assertDoesNotExist()
        rule.onNodeWithTag("live_exercise_0_suggestion").assertExists()
    }
}
