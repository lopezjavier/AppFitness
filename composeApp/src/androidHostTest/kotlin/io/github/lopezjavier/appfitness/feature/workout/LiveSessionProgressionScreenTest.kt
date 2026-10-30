package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.ProgressionAction
import io.github.lopezjavier.appfitness.domain.model.ProgressionReason
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import io.github.lopezjavier.appfitness.domain.model.SetUsed
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val usedSets = listOf(
    SetUsed(LocalDate(2026, 10, 24), 80.0, 8, 8.0),
    SetUsed(LocalDate(2026, 10, 24), 80.0, 8, null),
)

private fun suggestion(reason: ProgressionReason, weight: Double = 82.5, goal: Int = 6) = ProgressionSuggestion(
    exerciseId = "e1",
    action = ProgressionAction.RaiseWeight,
    weightKg = weight,
    goalReps = goal,
    reason = reason,
    setsUsed = usedSets,
)

private fun progressionState(suggestion: ProgressionSuggestion?, helpPosition: Int? = null) = LiveSessionUiState.Active(
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
        ),
    ),
    isOnline = true,
    progressionHelpPosition = helpPosition,
)

@Composable
private fun StatefulProgressionLive(suggestion: ProgressionSuggestion?) {
    var help by rememberSaveable { mutableStateOf<Int?>(null) }
    LiveSessionScreen(
        state = progressionState(suggestion, help),
        elapsedSeconds = 0,
        onEvent = { event ->
            when (event) {
                is LiveSessionEvent.OpenProgressionHelp -> help = event.position
                LiveSessionEvent.DismissProgressionHelp -> help = null
                else -> Unit
            }
        },
        onBack = {},
        onOpenExercise = {},
    )
}

/** US-70 live part: the suggestion line, its help sheet and the cases where it is absent. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class LiveSessionProgressionScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(suggestion: ProgressionSuggestion?) {
        rule.setContent { AppTheme { StatefulProgressionLive(suggestion) } }
        rule.waitForIdle()
    }

    @Test
    fun raisingTheWeight_showsTheSuggestionLine() {
        show(suggestion(ProgressionReason.AllSetsAtMax))

        rule.onNodeWithTag("live_exercise_0_suggestion").assertTextEquals("Sugerencia: 82,5 kg × 6")
    }

    @Test
    fun raisingTheReps_showsTheSameWeightAndTheGoalReps() {
        show(suggestion(ProgressionReason.BelowMax, weight = 80.0, goal = 8))

        rule.onNodeWithTag("live_exercise_0_suggestion").assertTextEquals("Sugerencia: 80 kg × 8")
    }

    @Test
    fun loweringTheWeight_showsTheLowerWeight() {
        show(suggestion(ProgressionReason.TwoSessionsBelowMin, weight = 75.0, goal = 6))

        rule.onNodeWithTag("live_exercise_0_suggestion").assertTextEquals("Sugerencia: 75 kg × 6")
    }

    @Test
    fun theSuggestionRow_announcesTheSuggestionAndThatItOpensTheReason() {
        show(suggestion(ProgressionReason.AllSetsAtMax))

        rule.onNodeWithTag("live_exercise_0_suggestion")
            .assertContentDescriptionEquals("Sugerencia: 82,5 kg × 6. Toca para ver el motivo")
    }

    @Test
    fun withoutSuggestion_noLineAndNoHelpAreShown() {
        show(null)

        rule.onNodeWithTag("live_exercise_0_suggestion").assertDoesNotExist()
        rule.onNodeWithTag("live_exercise_0_suggestion_help").assertDoesNotExist()
    }

    @Test
    fun theSuggestionRow_isTheOnlyTapTarget_a48DpButtonWithNoSeparateHelp() {
        show(suggestion(ProgressionReason.AllSetsAtMax))

        rule.onNodeWithTag("live_exercise_0_suggestion").assertHasClickAction().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("live_exercise_0_suggestion_help").assertDoesNotExist()
        rule.onNodeWithTag("progression_help_sheet").assertDoesNotExist()
    }

    @Test
    fun theHelp_listsTheSetsUsedWithDateWeightRepsAndRpe() {
        show(suggestion(ProgressionReason.AllSetsAtMax))

        rule.onNodeWithTag("live_exercise_0_suggestion").performClick()

        rule.onNodeWithTag("progression_help_sheet").assertExists()
        rule.onNodeWithTag("progression_help_set_1").assertTextEquals("sáb, 24 oct · 80 kg × 8 · RPE 8")
        rule.onNodeWithTag("progression_help_set_2").assertTextEquals("sáb, 24 oct · 80 kg × 8")
    }

    @Test
    fun theHelpReason_explainsTheRaise() {
        show(suggestion(ProgressionReason.AllSetsAtMax))

        rule.onNodeWithTag("live_exercise_0_suggestion").performClick()

        rule.onNodeWithTag("progression_help_reason").assertTextContains("toca subir el peso", substring = true)
    }

    @Test
    fun openDiscomfortReason_isNeutralWithoutHealthWords() {
        show(suggestion(ProgressionReason.KeptDiscomfort, weight = 80.0, goal = 8))

        rule.onNodeWithTag("live_exercise_0_suggestion").performClick()

        rule.onNodeWithTag("progression_help_reason").assertTextEquals("Mantén el peso de la última vez.")
        rule.onNodeWithTag("progression_help_footer").assertDoesNotExist()
    }

    @Test
    fun theOpenHelpSheet_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { StatefulProgressionLive(suggestion(ProgressionReason.AllSetsAtMax)) } }
        rule.waitForIdle()
        rule.onNodeWithTag("live_exercise_0_suggestion").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("progression_help_sheet").assertExists()
    }
}
