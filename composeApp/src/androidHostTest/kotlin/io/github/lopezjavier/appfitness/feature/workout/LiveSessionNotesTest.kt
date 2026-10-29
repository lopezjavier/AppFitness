package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import io.github.lopezjavier.appfitness.domain.model.SetNoteKind
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
import org.junit.Assert.assertTrue
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
        rule.onNodeWithTag("note_sheet_text").performTextInput(text)
        settle()
    }

    private fun saveSetNote(kind: String, text: String? = null) {
        tap("live_set_note_action_0_1")
        tap("note_sheet_option_$kind")
        if (text != null) type(text)
        tap("note_sheet_commit")
    }

    private val density: Float get() = rule.density.density

    @Test
    fun theNoteButton_isInsideTheSetRow_notOnALineUnderIt() {
        show()

        val row = rule.onNodeWithTag("live_set_0_1").fetchSemanticsNode().boundsInRoot
        val button = rule.onNodeWithTag("live_set_note_action_0_1").fetchSemanticsNode().boundsInRoot
        assertTrue("button inside the row vertically", button.top >= row.top && button.bottom <= row.bottom)
        assertTrue("button at least 48 dp", button.width >= 48f * density && button.height >= 48f * density)
        assertTrue("button at the end of the row", button.right <= row.right && button.left > row.center.x)
    }

    @Test
    fun withoutANote_noNoteLineIsShown_andTheRowKeepsItsHeight() {
        show()

        rule.onNodeWithTag("live_set_note_0_1").assertDoesNotExist()
        val row = rule.onNodeWithTag("live_set_0_1").fetchSemanticsNode().boundsInRoot
        assertTrue("row not taller than 56 dp", row.height <= 56f * density)
    }

    @Test
    fun theSetNoteSheet_offersTheFiveOptionsAndNoPainOption() {
        show()
        tap("live_set_note_action_0_1")

        listOf("technique", "range", "grip", "assisted", "other").forEach {
            rule.onNodeWithTag("note_sheet_option_$it").assertExists()
        }
        rule.onNodeWithText("Técnica mejorable").assertExists()
        rule.onNodeWithText("Rango incompleto").assertExists()
        rule.onNodeWithText("Agarre flojo").assertExists()
        rule.onNodeWithText("Con ayuda").assertExists()
        rule.onNodeWithText("Otro").assertExists()
        rule.onNodeWithText("Dolor", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("note_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun theHelpButton_explainsEachOptionAndSaysItIsForThisWorkout() {
        show()
        tap("live_set_note_action_0_1")

        tap("note_sheet_help")

        rule.onNodeWithText("Solo para este entreno.").assertExists()
        rule.onNodeWithTag("note_sheet_help_sheet_body").assertExists()
        rule.onNodeWithText("Agarre flojo: el agarre limitó la serie.").assertExists()
    }

    @Test
    fun anOptionWithoutText_savesAndShowsTheOptionLabel() {
        show()

        saveSetNote("technique")

        rule.onNodeWithTag("note_sheet_commit").assertDoesNotExist()
        rule.onNodeWithTag("live_set_note_0_1").assertTextContains("Técnica mejorable")
        assertEquals(SetNoteKind.Technique, repository.upserts.last().noteKind)
        assertEquals(null, repository.upserts.last().notes)
    }

    @Test
    fun anOptionWithOptionalText_showsOptionAndText() {
        show()

        saveSetNote("grip", "se resbala")

        rule.onNodeWithTag("live_set_note_0_1").assertTextContains("Agarre flojo · se resbala")
    }

    @Test
    fun otro_requiresTextToEnableGuardar() {
        show()
        tap("live_set_note_action_0_1")

        tap("note_sheet_option_other")
        rule.onNodeWithTag("note_sheet_commit").assertIsNotEnabled()
        type("pausa larga")

        rule.onNodeWithTag("note_sheet_commit").assertIsEnabled()
        tap("note_sheet_commit")
        rule.onNodeWithTag("live_set_note_0_1").assertTextContains("Otro · pausa larga")
        assertEquals(SetNoteKind.Other, repository.upserts.last().noteKind)
    }

    @Test
    fun aNoteOf201Characters_showsTheErrorAndKeepsGuardarDisabled() {
        show()
        tap("live_set_note_action_0_1")
        tap("note_sheet_option_other")

        type("a".repeat(201))

        rule.onNodeWithText("Máximo 200 caracteres").assertExists()
        rule.onNodeWithTag("note_sheet_commit").assertIsNotEnabled()
    }

    @Test
    fun tappingTheShownNote_opensTheSheetPreselectedAndTheEditReplacesIt() {
        show()
        saveSetNote("range")

        tap("live_set_note_0_1")
        rule.onNodeWithTag("note_sheet_option_range").assertIsSelected()
        rule.onNodeWithTag("note_sheet_commit").assertIsNotEnabled()
        tap("note_sheet_option_assisted")
        rule.onNodeWithTag("note_sheet_commit").assertIsEnabled()
        tap("note_sheet_commit")

        rule.onNodeWithTag("live_set_note_0_1").assertTextContains("Con ayuda")
    }

    @Test
    fun deselectingTheOption_removesTheNote() {
        show()
        saveSetNote("range")
        tap("live_set_note_0_1")

        tap("note_sheet_option_range")
        tap("note_sheet_commit")

        rule.onNodeWithTag("live_set_note_0_1").assertDoesNotExist()
        assertEquals(null, repository.upserts.last().noteKind)
    }

    @Test
    fun aNoteThatMentionsPain_showsNoMessageAndNoDiscomfort() {
        show()
        saveSetNote("other", "dolor fuerte")

        rule.onNodeWithTag("live_set_note_0_1").assertTextContains("dolor fuerte", substring = true)
        rule.onNodeWithTag("live_exercise_0_discomfort_block").assertDoesNotExist()
        rule.onNodeWithText("Molestia", substring = true).assertDoesNotExist()
        assertEquals(emptyList<Any>(), discomforts.rows)
    }

    @Test
    fun theRoutineNote_isShownUnderTheExercise() {
        show()

        rule.onNodeWithTag("live_exercise_note_0").assertTextContains("bajar el peso si duele", substring = true)
    }

    @Test
    fun theRoutineNote_isEditedFromTheMenuWithItsOwnOptions() {
        show()
        tap(MENU_BUTTON)
        tap("live_exercise_0_menu_note")

        listOf("weight_up", "weight_down", "technique", "setup", "other").forEach {
            rule.onNodeWithTag("routine_note_sheet_option_$it").assertExists()
        }
        rule.onNodeWithText("Subir peso").assertExists()
        tap("routine_note_sheet_option_weight_down")
        tap("routine_note_sheet_commit")

        rule.onNodeWithTag("live_exercise_note_0").assertTextContains("Bajar peso", substring = true)
    }

    @Test
    fun theRoutineNote_opensItsSheetWhenTapped() {
        show()

        tap("live_exercise_note_0")

        rule.onNodeWithTag("routine_note_sheet_option_other").assertIsSelected()
        rule.onNodeWithTag("routine_note_sheet_text").assertTextContains("bajar el peso si duele")
    }

    @Test
    fun theHalfFilledNote_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            val vm = remember { newViewModel() }
            val state by vm.uiState.collectAsState()
            AppTheme { LiveSessionScreen(state, 0, vm::onEvent, {}, onOpenExercise = {}) }
        }
        settle()
        tap("live_set_note_action_0_1")
        tap("note_sheet_option_grip")
        type("pausa lar")

        tester.emulateSavedInstanceStateRestore()
        settle()

        rule.onNodeWithTag("note_sheet_option_grip").assertIsSelected()
        rule.onNodeWithTag("note_sheet_text").assertTextContains("pausa lar")
    }

    private companion object {
        const val MENU_BUTTON = "live_exercise_0_menu"
    }
}
