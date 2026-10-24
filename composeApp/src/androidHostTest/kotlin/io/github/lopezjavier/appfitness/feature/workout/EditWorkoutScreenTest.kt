package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-37 "Editing" and "Deleting" a finished workout: one "Guardar", immediate delete, nothing written on leaving. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class EditWorkoutScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeWorkoutSessionRepository().apply { completed += completedSession("s1") }
    private val connectivity = FakeConnectivityObserver()
    private val exercises = FakeExerciseRepository(
        listOf(exerciseRow("e1", "bench"), exerciseRow("e2", "squat")),
    )
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press banca"), "squat" to exerciseInfo("squat", "Sentadilla")),
    )

    private var closed = 0
    private var deleted = 0
    private var addExerciseTaps = 0
    private var picked by mutableStateOf<String?>(null)

    private fun show() {
        val model = EditWorkoutViewModel("s1", repository, exercises, library, connectivity, MutableClock())
        rule.setContent {
            AppTheme {
                EditWorkoutRoot(
                    sessionId = "s1",
                    selectedExerciseId = picked,
                    onConsumeSelection = { picked = null },
                    onAddExercise = { addExerciseTaps++ },
                    onClose = { closed++ },
                    onDeleted = { deleted++ },
                    viewModel = model,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun save() {
        rule.onNodeWithTag("edit_workout_save").performClick()
        rule.waitForIdle()
    }

    @Test
    fun theWorkoutIsShownAsAnEditableForm_withTheDateAsReadOnlyText() {
        show()

        rule.onNodeWithTag("edit_workout_name").assertTextContains("Torso")
        rule.onNodeWithTag("edit_workout_date").assert(hasContentDescription("19 oct 2026", substring = true))
        rule.onNodeWithTag("edit_workout_duration").assertTextContains("52")
        rule.onNodeWithTag("edit_workout_set_0_0_weight").assertTextContains("87,5")
        rule.onNodeWithTag("edit_workout_set_0_1_reps").assertTextContains("8")
        rule.onNodeWithTag("edit_workout_save").assertIsEnabled()
    }

    @Test
    fun changingASetAndTheDescription_thenGuardar_sendsTheDraftAndCloses() {
        show()

        rule.onNodeWithTag("edit_workout_description").performScrollTo().performTextInput("Buen día")
        rule.onNodeWithTag("edit_workout_set_0_1_reps").performScrollTo().performTextClearance()
        rule.onNodeWithTag("edit_workout_set_0_1_reps").performTextInput("7")
        save()

        val (draft, sessionId) = repository.savedDrafts.single()
        assertEquals("s1", sessionId)
        assertEquals("Buen día", draft.description)
        assertEquals(SetEntry.WeightReps(87.5, 7), draft.exercises.single().sets[1].entry)
        assertEquals(1, closed)
    }

    @Test
    fun leavingWithoutGuardar_writesNothing() {
        show()

        rule.onNodeWithTag("edit_workout_name").performScrollTo().performTextInput(" nuevo")
        rule.onNodeWithTag("edit_workout_add_set_0").performScrollTo().performClick()
        rule.waitForIdle()

        assertTrue(repository.savedDrafts.isEmpty())
        assertTrue(repository.deleted.isEmpty())
        assertEquals(0, closed)
    }

    @Test
    fun addingAndRemovingSets_changesTheRowsAtOnceInTheDraftOnly() {
        show()

        rule.onNodeWithTag("edit_workout_add_set_0").performScrollTo().performClick()
        rule.onNodeWithTag("edit_workout_set_0_2_weight").assertExists()
        rule.onNodeWithTag("edit_workout_set_0_0_delete").performScrollTo().performClick()
        rule.onNodeWithTag("edit_workout_set_0_2_weight").assertDoesNotExist()
        save()

        assertEquals(2, repository.savedDrafts.single().first.exercises.single().sets.size)
    }

    @Test
    fun changingTheSetTypeAndTheRpe_goesThroughThePickers() {
        show()

        rule.onNodeWithTag("edit_workout_set_0_0_type").performScrollTo().performClick()
        rule.onNodeWithTag("set_type_picker_option_drop").performClick()
        rule.onNodeWithTag("edit_workout_set_0_1_rpe").performScrollTo().performClick()
        rule.onNodeWithTag("rpe_picker_option_8").performClick()
        save()

        val sets = repository.savedDrafts.single().first.exercises.single().sets
        assertEquals(SetType.Drop, sets[0].setType)
        assertEquals(8.0, sets[1].rpe)
    }

    @Test
    fun theRestStepper_changesTheRestOfTheExercise() {
        show()

        rule.onNodeWithTag("edit_workout_rest_0_increment").performScrollTo().performClick()
        save()

        assertEquals(130, repository.savedDrafts.single().first.exercises.single().restSeconds)
    }

    @Test
    fun removingTheExercise_leavesTheFormInvalidAndGuardarFlagsIt() {
        show()

        rule.onNodeWithTag("edit_workout_exercise_0_delete").performScrollTo().performClick()
        save()

        rule.onNodeWithTag("edit_workout_empty_hint").assertExists()
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun aBlankName_isFlaggedWhenGuardarIsTapped_andNothingIsSent() {
        show()

        rule.onNodeWithTag("edit_workout_name").performTextClearance()
        save()

        rule.onNodeWithTag("edit_workout_name_error").assertExists()
        assertTrue(repository.savedDrafts.isEmpty())
        assertEquals(0, closed)
    }

    @Test
    fun theExerciseThePickerReturned_isAppendedWithOneEmptySet() {
        show()

        picked = "e2"
        rule.waitForIdle()

        rule.onNodeWithTag("edit_workout_set_1_0_weight").assertExists()
        rule.onNodeWithTag("edit_workout_add_exercise").performScrollTo().performClick()
        assertEquals(1, addExerciseTaps)
    }

    @Test
    fun deleting_removesTheWorkoutAtOnceWithoutADialog_andLeaves() {
        show()

        rule.onNodeWithTag("edit_workout_delete").performClick()
        rule.waitForIdle()

        assertEquals(listOf("s1"), repository.deleted)
        assertEquals(1, deleted)
        rule.onNodeWithTag("edit_workout_delete_confirm").assertDoesNotExist()
    }

    @Test
    fun offline_disablesGuardarAndEliminar_andShowsTheReason() {
        connectivity.state.value = false
        show()

        rule.onNodeWithTag("edit_workout_save").assertIsNotEnabled()
        rule.onNodeWithTag("edit_workout_delete").assertIsNotEnabled()
        rule.onNodeWithTag("edit_workout_offline_reason").assertExists()
    }

    @Test
    fun aFailedSave_keepsTheEdits_andShowsTheMessage() {
        repository.saveCompletedFailure = io.github.lopezjavier.appfitness.domain.AppError.Unknown
        show()

        rule.onNodeWithTag("edit_workout_name").performScrollTo().performTextInput("!")
        save()

        rule.onNodeWithTag("edit_workout_snackbar").assertExists()
        rule.onNodeWithTag("edit_workout_name").assertTextContains("Torso!")
        assertEquals(0, closed)
    }

    @Test
    fun loadingAndErrorStates_render_andRetryIsOffered() {
        var events = 0
        rule.setContent {
            AppTheme {
                EditWorkoutScreen(EditWorkoutUiState.Error, { events++ }, {}, {})
            }
        }
        rule.onNodeWithTag("edit_workout_error").assertExists()
        rule.onNodeWithTag("edit_workout_error_action").performClick()
        assertEquals(1, events)
    }
}
