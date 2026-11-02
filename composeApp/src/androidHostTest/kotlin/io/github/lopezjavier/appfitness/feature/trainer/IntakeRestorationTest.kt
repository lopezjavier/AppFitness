package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.IntakeHost
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.CoachIntakeDraft
import io.github.lopezjavier.appfitness.domain.model.InjuredArea
import io.github.lopezjavier.appfitness.domain.model.PreBreakBest
import io.github.lopezjavier.appfitness.domain.model.Situation
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val STORY = "Llevo 4 años y vuelvo tras una lesión de hombro"

/** ADR-40: "Para conocerte" survives recreation and process death with its text, markers, picker and dialog. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class IntakeRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachIntakeRepository()
    private val prompt = FakeIntakePromptPreference()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("bench", "bench_press", name = "Press de banca")))
    private val connectivity = FakeConnectivityObserver()

    private val host = IntakeHost(false, null, {}, {}, {}, {})

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun extractedForm(tester: StateRestorationTester, handle: SavedStateHandle): () -> IntakeViewModel? {
        repository.extractResult = CoachIntakeDraft(
            trainingYears = 4.0,
            situation = Situation.ReturningInjury,
            injuredArea = InjuredArea.Shoulder,
            preBreakBests = listOf(PreBreakBest("bench", 90.0, 5)),
        )
        var current: IntakeViewModel? = null
        tester.setContent {
            AppTheme {
                val model = remember { IntakeViewModel(repository, prompt, handle).also { current = it } }
                IntakeRoot(host, viewModel = model, connectivity = connectivity, exercises = exercises)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("intake_free_text").performTextInput(STORY)
        tap("intake_continue")
        return { current }
    }

    @Test
    fun anExtractedForm_keepsTheTextTheFieldsAndTheMarkers_afterRecreation() {
        val tester = StateRestorationTester(rule)
        val current = extractedForm(tester, SavedStateHandle())
        val before = current()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertNotSame(before, current())
        rule.onNodeWithTag("intake_free_text_counter").assertTextEquals("${STORY.length}/500")
        rule.onNodeWithTag("intake_years_value").assertTextEquals("4 años")
        rule.onNodeWithTag("intake_extracted_marker_years").assertExists()
        rule.onNodeWithTag("intake_best_row_bench").assertExists()
        rule.onNodeWithTag("intake_save").assertExists()
    }

    @Test
    fun anOpenPicker_staysOpen_afterRecreation() {
        val tester = StateRestorationTester(rule)
        extractedForm(tester, SavedStateHandle())
        tap("intake_situation")
        rule.onNodeWithTag("intake_situation_picker_sheet").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("intake_situation_picker_sheet").assertExists()
    }

    @Test
    fun theDiscardDialog_staysOpen_afterRecreation() {
        val tester = StateRestorationTester(rule)
        extractedForm(tester, SavedStateHandle())
        tap("intake_back")
        rule.onNodeWithTag("intake_discard_dialog").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("intake_discard_dialog").assertExists()
    }

    @Test
    fun aViewModelRecreatedFromTheSameState_showsTheDraft_overTheStoredIntake() {
        repository.stored = CoachIntake(trainingYears = 2.0)
        val handle = SavedStateHandle()
        var current by mutableStateOf(IntakeViewModel(repository, prompt, handle))
        rule.setContent {
            AppTheme { IntakeRoot(host, viewModel = current, connectivity = connectivity, exercises = exercises) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("intake_years_value").assertTextEquals("2 años")
        tap("intake_years_increment")

        current = IntakeViewModel(repository, prompt, handle)
        rule.waitForIdle()

        rule.onNodeWithTag("intake_years_value").assertTextEquals("2,5 años")
        rule.onNodeWithTag("intake_save").assertExists()
    }
}
