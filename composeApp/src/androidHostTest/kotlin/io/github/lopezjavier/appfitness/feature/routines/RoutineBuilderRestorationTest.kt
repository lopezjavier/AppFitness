package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the routine builder keeps its draft and the open discard dialog across recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class RoutineBuilderRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val exercises = FakeExerciseRepository(listOf(exerciseRow("a", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press de banca")))
    private val repository = FakeRoutineRepository()
    private var closes = 0

    private fun viewModel(handle: SavedStateHandle) = RoutineBuilderViewModel(
        null,
        handle,
        repository,
        LoadExerciseCatalog(exercises, library),
        FakeConnectivityObserver(),
        FakeTrainingPreferencesRepository(),
    )

    @Test
    fun typedNameRowsAndTheOpenDiscardDialog_surviveRecreation() {
        val handle = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent {
            val vm = androidx.compose.runtime.remember { viewModel(handle) }
            val state by vm.uiState.collectAsState()
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    RoutineBuilderScreen(state, vm::onEvent, {}, {}, onClose = { closes++ })
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_name_field").performTextInput("Torso")
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_discard_dialog").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("routine_builder_name_field").assertTextContains("Torso")
        rule.onNodeWithTag("routine_builder_discard_dialog").assertExists()
        assertEquals(0, closes)
    }

    @Test
    fun theDraftRows_surviveARecreatedViewModel() {
        val handle = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        lateinit var current: RoutineBuilderViewModel
        tester.setContent {
            val vm = androidx.compose.runtime.remember { viewModel(handle).also { current = it } }
            val state by vm.uiState.collectAsState()
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    RoutineBuilderScreen(state, vm::onEvent, {}, {})
                }
            }
        }
        rule.waitForIdle()
        current.onEvent(RoutineBuilderEvent.AddExercise("a"))
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("routine_builder_row_0").assertContentDescriptionEquals("Press de banca")
    }

    @Test
    fun theOpenNoteSheet_keepsItsHalfTypedText() {
        val handle = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        lateinit var current: RoutineBuilderViewModel
        tester.setContent {
            val vm = androidx.compose.runtime.remember { viewModel(handle).also { current = it } }
            val state by vm.uiState.collectAsState()
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    RoutineBuilderScreen(state, vm::onEvent, {}, {})
                }
            }
        }
        rule.waitForIdle()
        current.onEvent(RoutineBuilderEvent.AddExercise("a"))
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_menu_0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("routine_builder_exercise_note_0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("note_sheet_field").performTextInput("bajar el pe")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("note_sheet_field").assertTextContains("bajar el pe")
    }

    @Test
    fun aSavedExerciseNote_survivesARecreatedViewModel() {
        val handle = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        lateinit var current: RoutineBuilderViewModel
        tester.setContent {
            val vm = androidx.compose.runtime.remember { viewModel(handle).also { current = it } }
            val state by vm.uiState.collectAsState()
            AppTheme {
                CompositionLocalProvider(LocalExerciseMediaUris provides FakeExerciseMediaUris) {
                    RoutineBuilderScreen(state, vm::onEvent, {}, {})
                }
            }
        }
        rule.waitForIdle()
        current.onEvent(RoutineBuilderEvent.AddExercise("a"))
        current.onEvent(RoutineBuilderEvent.SetExerciseNote(0, "bajar el peso si duele"))
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("routine_builder_note_0").assertTextContains("bajar el peso si duele")
    }
}
