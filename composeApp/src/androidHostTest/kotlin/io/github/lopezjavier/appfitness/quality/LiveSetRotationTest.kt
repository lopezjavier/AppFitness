package io.github.lopezjavier.appfitness.quality

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionEvent
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionScreen
import io.github.lopezjavier.appfitness.feature.workout.LiveSessionUiState
import io.github.lopezjavier.appfitness.feature.workout.LiveSetRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * US-46 rotation: a weight and reps typed into a live set are not lost when the activity is recreated. The state
 * holder below plays the view model (it outlives the composition, as the real one outlives a rotation).
 * The FoodSheet quantity is covered by `FoodRestorationTest.typedAmount_andMicronutrients_surviveRecreation_...`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class LiveSetRotationTest {
    @get:Rule
    val rule = createComposeRule()

    private var state: LiveSessionUiState.Active by mutableStateOf(qualityLiveState())

    private fun updateSet(position: Int, index: Int, change: (LiveSetRow) -> LiveSetRow) {
        state = state.copy(
            exercises = state.exercises.map { exercise ->
                if (exercise.position != position) {
                    exercise
                } else {
                    exercise.copy(sets = exercise.sets.map { if (it.setIndex == index) change(it) else it })
                }
            },
        )
    }

    private fun reduce(event: LiveSessionEvent) {
        when (event) {
            is LiveSessionEvent.WeightChanged -> updateSet(event.position, event.setIndex) {
                it.copy(weight = event.text)
            }

            is LiveSessionEvent.RepsChanged -> updateSet(event.position, event.setIndex) { it.copy(reps = event.text) }

            else -> Unit
        }
    }

    private fun textOf(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.EditableText)?.text

    @Test
    fun typedWeightAndReps_surviveRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme { LiveSessionScreen(state, 0, ::reduce, {}, onOpenExercise = {}) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("live_set_0_2_weight").performTextInput("72.5")
        rule.onNodeWithTag("live_set_0_2_reps").performTextInput("9")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        // The field shows the Spanish decimal comma.
        assertEquals("72,5", textOf("live_set_0_2_weight"))
        assertEquals("9", textOf("live_set_0_2_reps"))
    }
}
