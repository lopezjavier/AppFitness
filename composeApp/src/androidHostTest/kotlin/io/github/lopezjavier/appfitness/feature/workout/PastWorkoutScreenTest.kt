package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.routines.routineOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-37 "Logging a past workout" and starting one from a routine or from a finished workout. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class PastWorkoutScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeWorkoutSessionRepository()
    private val routines = FakeRoutineRepository()
    private val connectivity = FakeConnectivityObserver()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench")))
    private val library = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press banca")))
    private var savedIds = mutableListOf<String>()

    private fun show(source: PastWorkoutSource = PastWorkoutSource.Empty) {
        val model = PastWorkoutViewModel(
            source,
            repository,
            routines,
            exercises,
            library,
            connectivity,
            MutableClock(),
        )
        rule.setContent {
            AppTheme {
                PastWorkoutRoot(
                    routineId = (source as? PastWorkoutSource.FromRoutine)?.routineId,
                    sessionId = (source as? PastWorkoutSource.FromSession)?.sessionId,
                    selectedExerciseId = null,
                    onConsumeSelection = {},
                    onAddExercise = {},
                    onSaved = { savedIds += it },
                    viewModel = model,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun theDateStartsOnYesterday_andTheFormIsEmpty() {
        show()

        rule.onNodeWithTag("past_workout_date").assert(hasContentDescription("18 oct 2026", substring = true))
        rule.onNodeWithTag("past_workout_empty_hint").assertExists()
    }

    @Test
    fun pickingADate_anExercise_andValues_thenGuardar_createsTheWorkoutOnThatDate() {
        val picked = androidx.compose.runtime.mutableStateOf<String?>(null)
        val model = PastWorkoutViewModel(
            PastWorkoutSource.Empty,
            repository,
            routines,
            exercises,
            library,
            connectivity,
            MutableClock(),
        )
        rule.setContent {
            AppTheme {
                PastWorkoutRoot(null, null, picked.value, {
                    picked.value = null
                }, {}, { savedIds += it }, viewModel = model)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("past_workout_date").performClick()
        rule.onNodeWithTag("past_workout_date_dialog").assertExists()
        rule.onNode(hasText("October 15, 2026", substring = true)).performClick()
        rule.onNodeWithTag("past_workout_date_dialog_confirm").performClick()
        rule.onNodeWithTag("past_workout_date").assert(hasContentDescription("15 oct 2026", substring = true))

        rule.onNodeWithTag("past_workout_name").performTextInput("Piernas")
        picked.value = "e1"
        rule.waitForIdle()
        rule.onNodeWithTag("past_workout_set_0_0_weight").performScrollTo().performTextInput("100")
        rule.onNodeWithTag("past_workout_set_0_0_reps").performTextInput("5")
        rule.onNodeWithTag("past_workout_save").performClick()
        rule.waitForIdle()

        val (draft, sessionId) = repository.savedDrafts.single()
        assertNull(sessionId)
        assertEquals(LocalDate(2026, 10, 15), draft.date)
        assertEquals("Piernas", draft.name)
        assertEquals(SetEntry.WeightReps(100.0, 5), draft.exercises.single().sets.single().entry)
        assertEquals(listOf("s-saved"), savedIds)
        assertTrue(repository.startedRoutines.isEmpty())
    }

    @Test
    fun theDateDialog_doesNotOfferFutureDays() {
        show()

        rule.onNodeWithTag("past_workout_date").performClick()

        rule.onNode(hasText("October 25, 2026", substring = true)).assertIsNotEnabled()
        rule.onNode(hasText("October 19, 2026", substring = true)).assertExists()
    }

    @Test
    fun cancellingTheDateDialog_keepsTheDate() {
        show()

        rule.onNodeWithTag("past_workout_date").performClick()
        rule.onNode(hasText("October 3, 2026", substring = true)).performClick()
        rule.onNodeWithTag("past_workout_date_dialog_dismiss").performClick()

        rule.onNodeWithTag("past_workout_date_dialog").assertDoesNotExist()
        rule.onNodeWithTag("past_workout_date").assert(hasContentDescription("18 oct 2026", substring = true))
    }

    @Test
    fun guardarWithAnIncompleteForm_flagsTheNameAndSendsNothing() {
        show()

        rule.onNodeWithTag("past_workout_save").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("past_workout_name_error").assertExists()
        assertTrue(repository.savedDrafts.isEmpty())
        assertTrue(savedIds.isEmpty())
    }

    @Test
    fun fromARoutine_thePlannedSetsAreAlreadyInTheRows() {
        routines.routines += routineOf("r1", "Torso", "e1")
        show(PastWorkoutSource.FromRoutine("r1"))

        rule.onNodeWithTag("past_workout_name").assertTextContains("Torso")
        rule.onNodeWithTag("past_workout_set_0_2_reps").performScrollTo().assertTextContains("8")
        rule.onNodeWithTag("past_workout_save").performClick()
        rule.waitForIdle()

        assertEquals("r1", repository.savedDrafts.single().first.routineId)
        assertEquals(3, repository.savedDrafts.single().first.exercises.single().sets.size)
    }

    @Test
    fun fromAFinishedWorkout_itsCompletedSetsFillTheRows_andTheSourceIsKept() {
        repository.completed += completedSession("s1")
        show(PastWorkoutSource.FromSession("s1"))

        rule.onNodeWithTag("past_workout_set_0_0_weight").performScrollTo().assertTextContains("87,5")
        rule.onNodeWithTag("past_workout_save").performClick()
        rule.waitForIdle()

        assertNull(repository.savedDrafts.single().second)
        assertEquals(2, repository.completed.single { it.id == "s1" }.exercises.single().sets.size)
    }

    @Test
    fun offline_disablesGuardar() {
        connectivity.state.value = false
        show()

        rule.onNodeWithTag("past_workout_save").assertIsNotEnabled()
        rule.onNodeWithTag("past_workout_offline_reason").assertExists()
    }
}
