package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeDiscomfortRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-66: the note of a set and the routine's note of an exercise in the live session. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class LiveSessionNotesTest {
    @get:Rule
    val rule = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val clock = MutableClock()
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(SessionExercise("e1", 0, null, listOf(pendingSet(1)))),
    )
    private val discomforts = FakeDiscomfortRepository(now = { clock.current })
    private val weights = FakeWeightRepository()
    private val routines = FakeRoutineRepository(
        listOf(
            Routine(
                "r1",
                "Torso",
                null,
                false,
                listOf(
                    RoutineExercise(
                        "e1",
                        0,
                        null,
                        listOf(PlannedSet(60.0, 10)),
                        restSeconds = 90,
                        notes = "bajar el peso si duele",
                    ),
                ),
            ),
        ),
    )
    private val handle = SavedStateHandle()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        repository.history = mapOf("e1" to emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = LiveSessionViewModel(
        repository,
        FakeExerciseRepository(listOf(exerciseRow("e1", null, name = "Press banca", custom = true))),
        FakeExerciseLibrary(),
        FakeConnectivityObserver(),
        clock,
        FakeHaptics(),
        routines,
        FakeAlertSound(),
        FakeTrainingPreferencesRepository(),
        weights,
        SaveBodyMeasurement(weights, clock, TimeZone.UTC),
        TimeZone.UTC,
        handle,
        discomfortRepository = discomforts,
    )

    private fun show() {
        rule.setContent {
            val vm = remember { newViewModel() }
            val state by vm.uiState.collectAsState()
            AppTheme { LiveSessionScreen(state, 0, vm::onEvent, {}, onOpenExercise = {}) }
        }
        settle()
    }

    private fun settle() {
        scheduler.runCurrent()
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        settle()
    }

    private fun type(text: String) {
        rule.onNodeWithTag("note_sheet_field").performTextInput(text)
        settle()
    }

    @Test
    fun savingANote_showsItUnderTheSetAndWritesItWithTheSet() {
        show()
        rule.onNodeWithTag("live_set_note_0_1").assertDoesNotExist()

        tap("live_set_note_action_0_1")
        type("pausa larga")
        tap("note_sheet_commit")

        rule.onNodeWithTag("note_sheet_commit").assertDoesNotExist()
        rule.onNodeWithTag("live_set_note_0_1").assertTextContains("pausa larga")
        assertEquals("pausa larga", repository.upserts.last().notes)
    }

    @Test
    fun tappingTheShownNote_opensTheSheetPrefilledAndTheEditReplacesIt() {
        show()
        tap("live_set_note_action_0_1")
        type("pausa larga")
        tap("note_sheet_commit")

        tap("live_set_note_0_1")
        rule.onNodeWithTag("note_sheet_field").assertTextContains("pausa larga")
        rule.onNodeWithTag("note_sheet_commit").assertIsNotEnabled()
        type(" y rapida")
        rule.onNodeWithTag("note_sheet_commit").assertIsEnabled()
        tap("note_sheet_commit")

        rule.onNodeWithTag("live_set_note_0_1").assertTextContains("pausa larga y rapida")
    }

    @Test
    fun aNoteOf201Characters_showsTheErrorAndKeepsGuardarDisabled() {
        show()
        tap("live_set_note_action_0_1")

        type("a".repeat(201))

        rule.onNodeWithText("Máximo 200 caracteres").assertExists()
        rule.onNodeWithTag("note_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun clearingTheNote_removesIt() {
        show()
        tap("live_set_note_action_0_1")
        type("pausa larga")
        tap("note_sheet_commit")
        tap("live_set_note_0_1")

        rule.onNodeWithTag("note_sheet_field").performTextClearance()
        settle()
        tap("note_sheet_commit")

        rule.onNodeWithTag("live_set_note_0_1").assertDoesNotExist()
        assertEquals(null, repository.upserts.last().notes)
    }

    @Test
    fun aNoteThatMentionsPain_showsNoMessageAndNoDiscomfort() {
        show()
        tap("live_set_note_action_0_1")
        type("dolor fuerte")
        tap("note_sheet_commit")

        rule.onNodeWithTag("live_set_note_0_1").assertTextContains("dolor fuerte")
        rule.onNodeWithTag("live_exercise_0_discomfort_block").assertDoesNotExist()
        rule.onNodeWithText("Molestia", substring = true).assertDoesNotExist()
        assertEquals(emptyList<Any>(), discomforts.rows)
    }

    @Test
    fun theRoutineNote_isShownReadOnlyUnderTheExercise() {
        show()

        rule.onNodeWithTag("live_exercise_note_0").assertTextContains("bajar el peso si duele")
    }

    @Test
    fun theHalfTypedNote_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            val vm = remember { newViewModel() }
            val state by vm.uiState.collectAsState()
            AppTheme { LiveSessionScreen(state, 0, vm::onEvent, {}, onOpenExercise = {}) }
        }
        settle()
        tap("live_set_note_action_0_1")
        type("pausa lar")

        tester.emulateSavedInstanceStateRestore()
        settle()

        rule.onNodeWithTag("note_sheet_field").assertTextContains("pausa lar")
    }
}
