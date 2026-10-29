package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppMessages
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HapticEvent
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.RecordingAppHaptics
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetNoteKind
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-37 "Editing" and "Deleting" a finished workout, on the editor template (US-56). */
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
    private val haptics = RecordingAppHaptics()
    private val messages = AppMessages(CoroutineScope(Job()))

    private var closed = 0
    private var deleted = 0
    private var addExerciseTaps = 0
    private var picked by mutableStateOf<String?>(null)

    private fun show() {
        val model = EditWorkoutViewModel("s1", repository, exercises, library, connectivity, MutableClock())
        rule.setContent {
            AppTheme {
                CompositionLocalProvider(LocalAppHaptics provides haptics, LocalAppMessages provides messages) {
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
        }
        rule.waitForIdle()
    }

    private fun save() {
        rule.onNodeWithTag("edit_workout_save").performClick()
        rule.waitForIdle()
    }

    private fun editDescription() {
        rule.onNodeWithTag("edit_workout_description").performScrollTo().performTextInput("Buen día")
    }

    @Test
    fun theWorkoutIsShownAsAnEditableForm_withTheDateAsReadOnlyText_andGuardarDisabledUntilAnEdit() {
        show()

        rule.onNodeWithTag("edit_workout_title").assertTextContains("Editar entreno")
        rule.onNodeWithTag("edit_workout_name").assertTextContains("Torso")
        rule.onNodeWithTag("edit_workout_date").assert(hasContentDescription("19 oct 2026", substring = true))
        rule.onNodeWithTag("edit_workout_duration").assertTextContains("52")
        rule.onNodeWithTag("edit_workout_set_0_0_weight").assertTextContains("87,5")
        rule.onNodeWithTag("edit_workout_set_0_1_reps").assertTextContains("8")
        rule.onNodeWithTag("edit_workout_save").assertIsNotEnabled()
    }

    @Test
    fun changingASetAndTheDescription_thenGuardar_sendsTheDraft_andClosesOnceWithOneConfirm() {
        show()

        editDescription()
        rule.onNodeWithTag("edit_workout_set_0_1_reps").performScrollTo().performTextClearance()
        rule.onNodeWithTag("edit_workout_set_0_1_reps").performTextInput("7")
        rule.onNodeWithTag("edit_workout_save").assertIsEnabled()
        save()

        val (draft, sessionId) = repository.savedDrafts.single()
        assertEquals("s1", sessionId)
        assertEquals("Buen día", draft.description)
        assertEquals(SetEntry.WeightReps(87.5, 7), draft.exercises.single().sets[1].entry)
        assertEquals(1, closed)
        haptics.assertSingle(HapticEvent.Confirm)
    }

    @Test
    fun addingASetNote_showsItInTheForm_andGuardarSendsIt() {
        show()

        rule.onNodeWithTag("edit_workout_set_note_action_0_1").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("note_sheet_option_other").performClick()
        rule.onNodeWithTag("note_sheet_text").performTextInput("pausa larga")
        rule.onNodeWithTag("note_sheet_commit").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("edit_workout_set_note_0_1").assertTextContains("Otro · pausa larga")
        save()
        val set = repository.savedDrafts.single().first.exercises.single().sets[1]
        assertEquals("pausa larga", set.notes)
        assertEquals(SetNoteKind.Other, set.noteKind)
    }

    @Test
    fun setRow_at360Dp_keepsInputsWideEnoughNextToTheNoteButton() {
        show()

        val weight = rule.onNodeWithTag("edit_workout_set_0_1_weight").performScrollTo().getUnclippedBoundsInRoot()
        val reps = rule.onNodeWithTag("edit_workout_set_0_1_reps").getUnclippedBoundsInRoot()
        assertTrue("weight too narrow: ${weight.width}", weight.width >= 40.dp)
        assertTrue("reps too narrow: ${reps.width}", reps.width >= 40.dp)
        rule.onNodeWithTag("edit_workout_set_note_action_0_1").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun backWithoutEdits_leavesSilently() {
        show()

        rule.onNodeWithTag("edit_workout_back").performClick()
        rule.waitForIdle()

        assertEquals(1, closed)
        rule.onNodeWithTag("edit_workout_discard_dialog").assertDoesNotExist()
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun backWithEdits_asksToDiscard_andKeepEditingStays() {
        show()
        editDescription()

        rule.onNodeWithTag("edit_workout_back").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("¿Descartar los cambios?").assertExists()
        rule.onNodeWithTag("edit_workout_keep_editing").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("edit_workout_discard_dialog").assertDoesNotExist()
        assertEquals(0, closed)
        rule.onNodeWithTag("edit_workout_description").assertTextContains("Buen día")
    }

    @Test
    fun backWithEdits_thenDescartar_leavesWithoutSaving() {
        show()
        editDescription()

        rule.onNodeWithTag("edit_workout_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("edit_workout_discard_confirm").performClick()
        rule.waitForIdle()

        assertEquals(1, closed)
        assertTrue(repository.savedDrafts.isEmpty())
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun addingAndRemovingSets_changesTheRowsAtOnceInTheDraftOnly() {
        show()

        rule.onNodeWithTag("edit_workout_add_set_0").performScrollTo().performClick()
        rule.onNodeWithTag("edit_workout_set_0_2_weight").assertExists()
        val row = rule.onNodeWithTag("edit_workout_set_0_0_row").performScrollTo().fetchSemanticsNode()
        val remove = row.config[SemanticsActions.CustomActions].single { it.label.startsWith("Eliminar") }
        rule.runOnUiThread { remove.action() }
        rule.waitForIdle()
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
    fun removingTheExercise_leavesTheFormInvalid_andGuardarStaysDisabled() {
        show()

        rule.onNodeWithTag("edit_workout_exercise_0_delete").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("edit_workout_empty_hint").assertExists()
        rule.onNodeWithTag("edit_workout_save").assertIsNotEnabled()
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun aBlankName_isFlaggedUnderTheField_andGuardarStaysDisabled() {
        show()

        rule.onNodeWithTag("edit_workout_name").performTextClearance()
        rule.waitForIdle()

        rule.onNodeWithTag("edit_workout_name_error").assertExists()
        rule.onNodeWithTag("edit_workout_save").assertIsNotEnabled()
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
    fun offline_disablesGuardarAndEliminar_showsTheHint_andATapOnGuardarRejectsOnce() {
        connectivity.state.value = false
        show()
        editDescription()

        rule.onNodeWithTag("edit_workout_save").assertIsNotEnabled()
        rule.onNodeWithTag("edit_workout_delete").assertIsNotEnabled()
        rule.onNodeWithText("Sin conexión: no se puede guardar").assertExists()
        rule.onNodeWithTag("edit_workout_save").performClick()
        rule.waitForIdle()

        haptics.assertSingle(HapticEvent.Reject)
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun aFailedSave_keepsTheEdits_showsTheMessage_andRejectsOnce() {
        repository.saveCompletedFailure = io.github.lopezjavier.appfitness.domain.AppError.Unknown
        show()

        rule.onNodeWithTag("edit_workout_name").performScrollTo().performTextInput("!")
        save()
        rule.waitForIdle()

        assertEquals("No se pudo completar la acción. Inténtalo de nuevo.", messages.current.value?.text)
        haptics.assertSingle(HapticEvent.Reject)
        rule.onNodeWithTag("edit_workout_name").assertTextContains("Torso!")
        assertEquals(0, closed)
    }

    @Test
    fun noSnackbarHostIsDrawn() {
        show()

        rule.onNodeWithTag("edit_workout_snackbar").assertDoesNotExist()
    }

    @Test
    fun loadingAndErrorStates_render_andRetryIsOffered() {
        var events = 0
        rule.setContent {
            AppTheme {
                EditWorkoutScreen(EditWorkoutUiState.Error, { events++ }, {}, {}, {})
            }
        }
        rule.onNodeWithTag("edit_workout_error").assertExists()
        rule.onNodeWithTag("edit_workout_error_action").performClick()
        assertEquals(1, events)
    }
}
