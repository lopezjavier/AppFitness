package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.IntakeHost
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.CoachIntakeDraft
import io.github.lopezjavier.appfitness.domain.model.InjuredArea
import io.github.lopezjavier.appfitness.domain.model.PreBreakBest
import io.github.lopezjavier.appfitness.domain.model.Situation
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val STORY = "Llevo 4 años, vuelvo tras una lesión de hombro y mi mejor press era 90 x 5"

/** US-81: "Para conocerte", one test per Gherkin scenario over the real view model and a fake repository. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class IntakeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachIntakeRepository()
    private val prompt = FakeIntakePromptPreference()
    private val connectivity = FakeConnectivityObserver()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("bench", "bench_press", name = "Press de banca")))
    private var done = 0
    private var closed = 0
    private var pickerOpened = 0
    private var picked by mutableStateOf<String?>(null)

    private fun show(first: Boolean = false) {
        val model = IntakeViewModel(repository, prompt, SavedStateHandle())
        rule.setContent {
            AppTheme {
                IntakeRoot(
                    host = IntakeHost(
                        continuesToBeforeStart = first,
                        selectedExerciseId = picked,
                        onConsumeSelection = { picked = null },
                        onAddExercise = { pickerOpened++ },
                        onDone = { done++ },
                        onClose = { closed++ },
                    ),
                    viewModel = model,
                    connectivity = connectivity,
                    exercises = exercises,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        rule.onNodeWithTag(tag).performTextInput(text)
        rule.waitForIdle()
    }

    private val us81Draft = CoachIntakeDraft(
        trainingYears = 4.0,
        situation = Situation.ReturningInjury,
        injuredArea = InjuredArea.Shoulder,
        preBreakBests = listOf(PreBreakBest("bench", 90.0, 5)),
    )

    @Test
    fun theFields_areAllThere_withTheTextCounter() {
        show()

        listOf(
            "intake_free_text",
            "intake_free_text_counter",
            "intake_continue",
            "intake_years_value",
            "intake_situation",
            "intake_injured_area",
            "intake_add_best",
            "intake_maintenance_kcal",
            "intake_cut_kcal",
            "intake_save",
        ).forEach { rule.onNodeWithTag(it).assertExists() }
        rule.onNodeWithTag("intake_free_text_counter").assertTextEquals("0/500")
        rule.onNodeWithTag("intake_years_value").assertTextEquals("Sin indicar")
        rule.onNodeWithTag("intake_skip").assertDoesNotExist()
    }

    @Test
    fun skip_isPinnedBesideSave_withoutScrolling() {
        show(first = true)

        rule.onNodeWithTag("intake_skip").assertIsDisplayed()
        rule.onNodeWithTag("intake_save").assertIsDisplayed()
    }

    @Test
    fun fillFromText_isDisabledWhileTheTextIsBlank() {
        show()
        rule.onNodeWithTag("intake_continue").assertIsNotEnabled()

        type("intake_free_text", STORY)

        rule.onNodeWithTag("intake_continue").assertIsEnabled()
    }

    @Test
    fun writingInMyOwnWords_fillsFourFieldsWithMarkers_leavesCaloriesEmpty_andSavesNothingYet() {
        repository.extractResult = us81Draft
        show()
        type("intake_free_text", STORY)

        tap("intake_continue")

        rule.onNodeWithTag("intake_years_value").assertTextEquals("4 años")
        rule.onNodeWithText("Vuelvo tras una lesión").assertExists()
        rule.onNodeWithText("Hombro").assertExists()
        rule.onNodeWithTag("intake_best_row_bench").assertExists()
        listOf("years", "situation", "injured_area", "bests").forEach {
            rule.onNodeWithTag("intake_extracted_marker_$it").assertExists()
        }
        rule.onNodeWithTag("intake_extracted_marker_maintenance").assertDoesNotExist()
        rule.onNodeWithTag("intake_extracted_marker_cut_kcal").assertDoesNotExist()
        rule.onAllNodesWithText("Rellenado desde tu texto. Revísalo.").assertCountEquals(4)
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun touchingAFilledField_clearsItsMarker_andSaveStoresTheForm() {
        repository.extractResult = us81Draft
        show(first = true)
        type("intake_free_text", STORY)
        tap("intake_continue")

        tap("intake_years_increment")

        rule.onNodeWithTag("intake_years_value").assertTextEquals("4,5 años")
        rule.onNodeWithTag("intake_extracted_marker_years").assertDoesNotExist()
        rule.onNodeWithTag("intake_extracted_marker_situation").assertExists()
        tap("intake_save")
        assertEquals(4.5, repository.saveCalls.single().trainingYears)
        assertEquals(1, done)
    }

    @Test
    fun theCoachNeverGuesses_aFieldTheTextDoesNotMention_staysUnset() {
        repository.extractResult = CoachIntakeDraft(trainingYears = 2.0)
        show()
        type("intake_free_text", "Entreno desde hace dos años")

        tap("intake_continue")

        rule.onNodeWithTag("intake_years_value").assertTextEquals("2 años")
        rule.onNodeWithTag("intake_situation").assertTextEquals("Situación", "Sin indicar")
        rule.onNodeWithTag("intake_extracted_marker_situation").assertDoesNotExist()
    }

    @Test
    fun onlyValidValues_aCaloriesFigureOutOfRange_isMarkedAndBlocksSaving() {
        show()

        type("intake_maintenance_kcal", "100")

        rule.onNodeWithText("Entre 800 y 6000 kcal").assertExists()
        rule.onNodeWithTag("intake_save").assertIsNotEnabled()
    }

    @Test
    fun theYearsStepper_goesBackToSinIndicarBelowZero() {
        show()

        tap("intake_years_increment")
        rule.onNodeWithTag("intake_years_value").assertTextEquals("1 año")
        tap("intake_years_decrement")
        tap("intake_years_decrement")
        rule.onNodeWithTag("intake_years_value").assertTextEquals("0 años")
        tap("intake_years_decrement")

        rule.onNodeWithTag("intake_years_value").assertTextEquals("Sin indicar")
    }

    @Test
    fun theSituationPicker_appliesOnTap() {
        show()

        tap("intake_situation")
        tap("intake_situation_picker_option_returning_break")

        rule.onNodeWithText("Vuelvo tras una pausa").assertExists()
        rule.onNodeWithTag("intake_situation_picker_sheet").assertDoesNotExist()
    }

    @Test
    fun whenTheBudgetIsReached_aMessageShows_andTheFieldsStayEditable() {
        repository.extractFailure = CoachException(CoachFailure.LimitReached(CoachFailure.Scope.User))
        show()
        type("intake_free_text", STORY)

        tap("intake_continue")

        rule.onNodeWithTag("intake_limit_message").assertExists()
        tap("intake_years_increment")
        rule.onNodeWithTag("intake_years_value").assertTextEquals("1 año")
    }

    @Test
    fun withoutConnection_extractingShowsTheOfflineMessage() {
        repository.extractFailure = AppException(AppError.Network)
        show()
        type("intake_free_text", STORY)

        tap("intake_continue")

        rule.onNodeWithText("Sin conexión: no se puede leer tu texto.").assertExists()
    }

    @Test
    fun addingAMark_opensThePicker_thenTheWeightAndRepsSheet_andAddsTheRow() {
        show()

        tap("intake_add_best")
        assertEquals(1, pickerOpened)
        picked = "bench"
        rule.waitForIdle()
        type("intake_best_weight", "90")
        type("intake_best_reps", "5")
        tap("intake_best_sheet_commit")

        rule.onNodeWithTag("intake_best_row_bench").assertExists()
        rule.onNodeWithText("90 kg x 5").assertExists()
        rule.onNodeWithText("Press de banca").assertExists()
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun backWithChanges_asksToDiscard_andDiscardingCloses() {
        show()
        tap("intake_years_increment")

        tap("intake_back")
        rule.onNodeWithTag("intake_discard_dialog").assertExists()
        assertEquals(0, closed)
        tap("intake_discard_confirm")

        assertEquals(1, closed)
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun backWithoutChanges_closesAtOnce() {
        repository.stored = CoachIntake(trainingYears = 3.0)
        show()

        tap("intake_back")

        assertEquals(1, closed)
        rule.onNodeWithTag("intake_discard_dialog").assertDoesNotExist()
    }

    @Test
    fun ahoraNo_isOnlyOfferedInTheFirstPlanFlow_andLeavesWithoutSaving() {
        show(first = true)

        tap("intake_skip")

        assertEquals(1, done)
        assertTrue(prompt.asked)
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun editingLater_showsWhatWasSaved() {
        repository.stored =
            CoachIntake(trainingYears = 3.0, situation = Situation.Continuous, knownMaintenanceKcal = 2500)
        show()

        rule.onNodeWithTag("intake_years_value").assertTextEquals("3 años")
        rule.onNodeWithText("Entreno de forma continua").assertExists()
        rule.onNodeWithTag("intake_save").assertIsNotEnabled()
        rule.onNodeWithTag("intake_skip").assertDoesNotExist()
    }
}
