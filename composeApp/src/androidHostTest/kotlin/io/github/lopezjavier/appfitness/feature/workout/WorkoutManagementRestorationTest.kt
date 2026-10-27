package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-40 for the workout management screens: the half-edited form, the open date dialog, the chooser step and the
 * "Guardar como rutina" name survive recreation and nothing is written meanwhile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per restoration scenario.
class WorkoutManagementRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeWorkoutSessionRepository().apply { completed += completedSession("s1") }
    private val routines = FakeRoutineRepository()
    private val connectivity = FakeConnectivityObserver()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press banca")))

    @Test
    fun editScreen_keepsTheHalfEditedDraftAfterRecreation_withoutWriting() {
        val handle = SavedStateHandle()
        var current: EditWorkoutViewModel? = null
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                val model = remember {
                    EditWorkoutViewModel("s1", repository, exercises, library, connectivity, MutableClock(), handle)
                        .also { current = it }
                }
                EditWorkoutRoot("s1", null, {}, {}, {}, {}, viewModel = model)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("edit_workout_description").performScrollTo().performTextInput("A medias")
        rule.onNodeWithTag("edit_workout_add_set_0").performScrollTo().performClick()
        rule.waitForIdle()
        val before = current

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertNotSame(before, current)
        rule.onNodeWithTag("edit_workout_description").assertTextContains("A medias")
        rule.onNodeWithTag("edit_workout_set_0_2_weight").assertExists()
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun editScreen_keepsItsDirtyStateAndTheOpenDiscardDialogAfterRecreation() {
        val handle = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                val model = remember {
                    EditWorkoutViewModel("s1", repository, exercises, library, connectivity, MutableClock(), handle)
                }
                EditWorkoutRoot("s1", null, {}, {}, {}, {}, viewModel = model)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("edit_workout_description").performScrollTo().performTextInput("A medias")
        rule.onNodeWithTag("edit_workout_back").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("edit_workout_discard_dialog").assertExists()
        rule.onNodeWithTag("edit_workout_keep_editing").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("edit_workout_description").assertTextContains("A medias")
        rule.onNodeWithTag("edit_workout_save").assertIsEnabled()
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun pastScreen_keepsTheDraftAndTheOpenDateDialogAfterRecreation() {
        val handle = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                val model = remember {
                    PastWorkoutViewModel(
                        PastWorkoutSource.Empty,
                        repository,
                        routines,
                        exercises,
                        library,
                        connectivity,
                        MutableClock(),
                        handle,
                    )
                }
                PastWorkoutRoot(null, null, null, {}, {}, {}, viewModel = model)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("past_workout_name").performTextInput("Piernas")
        rule.onNodeWithTag("past_workout_date").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("past_workout_name").assertTextContains("Piernas")
        rule.onNodeWithTag("past_workout_date_dialog").assertExists()
        rule.onNodeWithTag("past_workout_date").assert(hasContentDescription("18 oct 2026", substring = true))
    }

    @Test
    fun theViewModelRecreatedFromTheSameSavedState_restoresTheDraftAndTheSource() {
        val handle = SavedStateHandle()
        routines.routines += io.github.lopezjavier.appfitness.feature.routines.routineOf("r1", "Torso", "e1")
        val first = PastWorkoutViewModel(
            PastWorkoutSource.FromRoutine("r1"),
            repository,
            routines,
            exercises,
            library,
            connectivity,
            MutableClock(),
            handle,
        )
        first.onEvent(WorkoutFormEvent.NameChanged("Mi torso"))

        // The source of the second one is gone, yet the restored draft wins over loading it again.
        routines.routines.clear()
        val second = PastWorkoutViewModel(
            PastWorkoutSource.FromRoutine("r1"),
            repository,
            routines,
            exercises,
            library,
            connectivity,
            MutableClock(),
            handle,
        )

        assertEquals(
            "Mi torso",
            (second.uiState.value as? PastWorkoutUiState.Content)?.view?.form?.name ?: "pending",
        )
    }

    @Test
    fun chooser_keepsTheOpenStepAfterRecreation() {
        val handle = SavedStateHandle()
        routines.routines += io.github.lopezjavier.appfitness.feature.routines.routineOf("r1", "Torso", "e1")
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                val model = remember { PastWorkoutChooserViewModel(routines, handle) }
                val state by model.uiState.collectAsStateWithLifecycle()
                PastWorkoutChooserSheet(state, model::onEvent, sessions = { HistoryUiState.Empty })
            }
        }
        val model = PastWorkoutChooserViewModel(routines, handle)
        model.onEvent(PastWorkoutChooserEvent.Open())
        model.onEvent(PastWorkoutChooserEvent.ChooseRoutine)
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("past_workout_source_routine_r1").assertExists()
    }

    @Test
    fun saveAsRoutineSheet_keepsTheTypedNameAfterRecreation() {
        val handle = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                val model = remember {
                    SessionDetailViewModel(
                        "s1", repository, exercises, library, connectivity, MutableClock(),
                        FakeTrainingPreferencesRepository(), FakeWeightRepository(), routines, handle,
                    )
                }
                SessionDetailRoot(sessionId = "s1", onClose = {}, onOpenExercise = {}, viewModel = model)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("session_detail_more").performClick()
        rule.onNodeWithText("Guardar como rutina").performClick()
        rule.onNodeWithTag("save_as_routine_sheet_input").performTextInput(" B")
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("save_as_routine_sheet").assertExists()
        rule.onNodeWithTag("save_as_routine_sheet_input").assertTextContains("Torso B")
        assertTrue(routines.saved.isEmpty())
    }
}
