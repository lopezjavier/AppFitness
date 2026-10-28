package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-40 on the live session: every sheet that is open when the process is recreated is open afterwards. Sheets
 * driven by the view model (pickers, calculators, Finish) come back from the state; the card's own sheets and the
 * typed drafts must come back from the saved instance state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per restorable sheet.
class LiveSessionRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val tester by lazy { StateRestorationTester(rule) }

    // Plays the view model: it outlives the recreation of the composition.
    private var state: LiveSessionUiState.Active = active()

    private fun active(
        metric: ExerciseMetricType = ExerciseMetricType.WeightReps,
        equipment: Equipment? = Equipment.Barbell,
    ) = LiveSessionUiState.Active(
        name = "Torso",
        startedAt = SESSION_START,
        exercises = listOf(
            LiveExercise(
                0,
                "e1",
                "Press banca",
                null,
                listOf(
                    LiveSetRow(1, "60", "10", true, type = SetType.Working),
                    LiveSetRow(2, "", "", false),
                ),
                metricType = metric,
                equipment = equipment,
            ),
        ),
    )

    private fun showAndRestore() {
        tester.setContent { AppTheme { LiveSessionScreen(state, 0, {}, {}, onOpenExercise = {}) } }
        rule.waitForIdle()
    }

    private fun recreate() {
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
    }

    private fun textOf(tag: String) =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString {
            it.text
        }

    @Test
    fun exerciseActionSheet_staysOpen() {
        showAndRestore()
        rule.onNodeWithTag("live_exercise_0_menu").performClick()
        rule.onNodeWithTag("live_exercise_0_menu_rest").assertExists()

        recreate()

        rule.onNodeWithTag("live_exercise_0_menu_rest").assertExists()
    }

    @Test
    fun discomfortSheet_staysOpenWithTheHalfTypedNote() {
        showAndRestore()
        rule.onNodeWithTag("live_exercise_0_menu").performClick()
        rule.onNodeWithTag("live_exercise_0_menu_discomfort").performClick()
        rule.onNodeWithTag("live_exercise_0_discomfort_note").performTextInput("me duele al bajar")

        recreate()

        rule.onNodeWithTag("live_exercise_0_discomfort_note").assertTextContains("me duele al bajar")
    }

    @Test
    fun openDiscomfort_isStillShownAfterRecreation() {
        state =
            active().let { it.copy(exercises = it.exercises.map { e -> e.copy(discomfort = LiveDiscomfort(true)) }) }
        showAndRestore()

        recreate()

        rule.onNodeWithTag("live_exercise_0_discomfort").assertExists()
        rule.onNodeWithTag("live_exercise_0_discomfort_severe").assertExists()
    }

    @Test
    fun sessionMenu_staysOpen() {
        showAndRestore()
        rule.onNodeWithTag("live_menu_button").performClick()

        recreate()

        rule.onNodeWithTag("live_menu_finish").assertExists()
    }

    @Test
    fun restSheet_staysOpenWithTheSteppedValue() {
        showAndRestore()
        rule.onNodeWithTag("live_exercise_0_menu").performClick()
        rule.onNodeWithTag("live_exercise_0_menu_rest").performClick()
        val before = textOf("live_exercise_0_rest_value")
        rule.onNodeWithTag("live_exercise_0_rest_increment").performClick()
        val stepped = textOf("live_exercise_0_rest_value")
        assertNotEquals(before, stepped)

        recreate()

        assertEquals(stepped, textOf("live_exercise_0_rest_value"))
    }

    @Test
    fun bodyWeightSheet_staysOpenWithTheTypedWeight() {
        state = active(metric = ExerciseMetricType.Bodyweight, equipment = Equipment.Bodyweight)
        showAndRestore()
        rule.onNodeWithTag("live_exercise_0_menu").performClick()
        rule.onNodeWithTag("live_exercise_0_menu_body_weight").performClick()
        rule.onNodeWithTag("live_exercise_0_body_weight_field").performTextInput("82,5")

        recreate()

        rule.onNodeWithTag("live_exercise_0_body_weight_field").assertTextContains("82,5")
    }

    @Test
    fun setTypePicker_staysOpen() {
        state = active().copy(typePicker = TypePickerTarget(0, 2))
        showAndRestore()
        rule.onNodeWithTag("set_type_picker_option_warm_up").assertExists()

        recreate()

        rule.onNodeWithTag("set_type_picker_option_warm_up").assertExists()
    }

    @Test
    fun rpePicker_staysOpen() {
        state = active().copy(rpePicker = RpePickerTarget(0, 2, completesSet = true))
        showAndRestore()
        rule.onNodeWithTag("rpe_picker_option_8").assertExists()

        recreate()

        rule.onNodeWithTag("rpe_picker_option_8").assertExists()
    }

    @Test
    fun warmUpCalculator_staysOpenWithItsWeightDraft() {
        val draft = CalculatorDraft(0, CalculatorKind.WarmUp, weightText = "100", weightEdited = true)
        state = active().copy(calculator = calculatorUiState(draft, TrainingPreferences(), Equipment.Barbell))
        showAndRestore()

        recreate()

        rule.onNodeWithTag("live_exercise_0_warm_up_calc_weight_field").assertTextContains("100")
    }

    @Test
    fun discLoader_staysOpenWithItsDiscCounts() {
        val draft = CalculatorDraft(0, CalculatorKind.Discs).withDiscCount(20.0, 2)
        state = active().copy(calculator = calculatorUiState(draft, TrainingPreferences(), Equipment.Barbell))
        showAndRestore()
        val total = textOf("live_exercise_0_disc_loader_total")

        recreate()

        assertEquals(total, textOf("live_exercise_0_disc_loader_total"))
    }

    @Test
    fun finishSheet_staysOpenWithItsWarning() {
        state = active().copy(finishSheet = FinishSheet(3))
        showAndRestore()

        recreate()

        rule.onNodeWithTag("live_finish_sheet_warning").assertExists()
        rule.onNodeWithTag("live_finish_sheet_commit").assertExists()
    }
}
